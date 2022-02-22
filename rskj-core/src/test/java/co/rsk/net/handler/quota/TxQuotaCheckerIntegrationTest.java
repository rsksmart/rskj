package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.bc.PendingState;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.rpc.Web3;
import org.ethereum.util.RskTestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TxQuotaCheckerIntegrationTest {

    private static final long BLOCK_AVG_GAS_PRICE = 65164000;

    private TxQuotaChecker quotaChecker;
    private TxQuotaChecker.CurrentContext currentContext;

    private RskTestContext rskTestContext;
    private Repository repository;

    private Account sender;
    private Account receiver;

    @Before
    public void setUp() {
        rskTestContext = new RskTestContext(new String[]{"--regtest"}) {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }

            @Override
            protected RepositoryLocator buildRepositoryLocator() {
                return spy(super.buildRepositoryLocator());
            }
        };
        Blockchain blockChain = rskTestContext.getBlockchain();
        RepositoryLocator repositoryLocator = rskTestContext.getRepositoryLocator();
        repository = repositoryLocator.startTrackingAt(blockChain.getBestBlock().getHeader());
        ReceivedTxSignatureCache signatureCache = spy(rskTestContext.getReceivedTxSignatureCache());

        Web3 web3 = mock(Web3.class);
        when(web3.eth_gasPrice()).thenReturn("0x" + Long.toHexString(BLOCK_AVG_GAS_PRICE));

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(
                rskTestContext.getRskSystemProperties(),
                repositoryLocator,
                rskTestContext.getBlockStore(),
                rskTestContext.getBlockFactory(),
                rskTestContext.getCompositeEthereumListener(),
                rskTestContext.getTransactionExecutorFactory(),
                signatureCache,
                10,
                100,
                web3);

        // don't call start to avoid creating threads
        transactionPool.processBest(blockChain.getBestBlock());

        // this is to work around the current test structure, which abuses the Repository by modifying it in place
        doReturn(repository).when(repositoryLocator).snapshotAt(any());

        Block currentBlock = blockChain.getBestBlock();
        Block mockedBlock = spy(currentBlock);
        when(mockedBlock.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(6_800_000));
        when(mockedBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(59_240));

        PendingState pendingState = transactionPool.getPendingState();
        currentContext = new TxQuotaChecker.CurrentContext(mockedBlock, pendingState, repository, web3);
        quotaChecker = new TxQuotaChecker();

        sender = new AccountBuilder().name("sender").build();
        receiver = new AccountBuilder().name("receiver").build();
    }

    @After
    public void tearDown() {
        rskTestContext.close();
    }

    @Test
    public void acceptTxAfterRefresh() {
        long blockMinGasPrice = 59240;
        long smallGasPrice = BLOCK_AVG_GAS_PRICE - 100_000;
        long bigGasPrice = BLOCK_AVG_GAS_PRICE + 1_000_000;
        long accountNonce = repository.getNonce(sender.getAddress()).longValue();

        Transaction smallTx = tx(accountNonce, 200_000, smallGasPrice, 12_500);
        long approximateLastTxTime = System.currentTimeMillis();
        assertTrue("Initial small tx should've been accepted", quotaChecker.acceptTx(smallTx, Optional.empty(), currentContext));

        Transaction bigTx = tx(accountNonce, 3_000_000, bigGasPrice, 50_000);
        simulateLastTransactionRunAt(approximateLastTxTime - 50); // 1 second ago
        approximateLastTxTime = System.currentTimeMillis();
        assertTrue("Big tx should've been accepted after a small one some milliseconds afterwards", quotaChecker.acceptTx(smallTx, Optional.empty(), currentContext));

        Transaction bigTxReplacing = tx(accountNonce, 3_000_000, (long) (bigGasPrice * 1.1), 50_000);
        simulateLastTransactionRunAt(approximateLastTxTime - 1000); // 1 second ago
        approximateLastTxTime = System.currentTimeMillis();
        assertFalse("Big replacing tx should've been rejected 1 second after the replaced tx", quotaChecker.acceptTx(bigTxReplacing, Optional.of(bigTx), currentContext));

        simulateLastTransactionRunAt(approximateLastTxTime - 2 * 60 * 1000); // 2 minutes ago
        assertTrue("Big replacing tx should've been accepted 2 minutes after the last replace attempt", quotaChecker.acceptTx(bigTxReplacing, Optional.of(bigTx), currentContext));

        Transaction topFactorTx = tx(accountNonce + 3, 6_800_000, blockMinGasPrice, 92_750); // TODO:I adjust this and other values when max factor is clarified (1900 seems too high)
        simulateLastTransactionRunAt(approximateLastTxTime - 60 * 1000); // 1 minute ago
        assertFalse("Top factor tx should've been rejected 1 minute after last big tx", quotaChecker.acceptTx(topFactorTx, Optional.empty(), currentContext));

        Transaction topFactorTxReplacing = tx(accountNonce + 3, 6_800_000, (long) (blockMinGasPrice * 1.1), 92_750); // TODO:I adjust this and other values when max factor is clarified (1900 seems too high)
        simulateLastTransactionRunAt(approximateLastTxTime - 20 * 60 * 1000); // 20 minutes ago
        assertFalse("Top factor replacing tx should've been rejected 10 minutes after last big tx", quotaChecker.acceptTx(topFactorTxReplacing, Optional.of(topFactorTx), currentContext));

        simulateLastTransactionRunAt(approximateLastTxTime - 21 * 60 * 1000); // 21 minutes ago
        assertTrue("Top factor replacing tx should've been accepted 30 minutes after last big tx", quotaChecker.acceptTx(topFactorTxReplacing, Optional.of(topFactorTx), currentContext));
    }

    private void simulateLastTransactionRunAt(long simulatedLastTxTime) {
        TxQuota senderQuota = quotaChecker.getTxQuota(sender.getAddress());
        Whitebox.setInternalState(senderQuota, "timestamp", simulatedLastTxTime);
    }

    private Transaction tx(long nonce, long gasLimit, long gasPrice, long size) {
        Transaction transaction = new TransactionBuilder()
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .receiver(receiver)
                .sender(sender)
                .data(Hex.decode("0001"))
                .value(BigInteger.TEN)
                .build();

        Transaction mockedTx = spy(transaction);
        when(mockedTx.getSize()).thenReturn(size);

        return mockedTx;
    }

}
