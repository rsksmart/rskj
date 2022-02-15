package co.rsk.net.handler.quota;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RskTestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.util.Optional;

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
        TransactionPoolImpl transactionPool = new TransactionPoolImpl(
                rskTestContext.getRskSystemProperties(),
                repositoryLocator,
                rskTestContext.getBlockStore(),
                rskTestContext.getBlockFactory(),
                rskTestContext.getCompositeEthereumListener(),
                rskTestContext.getTransactionExecutorFactory(),
                signatureCache,
                10,
                100);
        // don't call start to avoid creating threads
        transactionPool.processBest(blockChain.getBestBlock());

        // this is to workaround the current test structure, which abuses the Repository by
        // modifying it in place
        doReturn(repository).when(repositoryLocator).snapshotAt(any());

        Block currentBlock = blockChain.getBestBlock();
        PendingState pendingState = transactionPool.getPendingState();
        currentContext = new TxQuotaChecker.CurrentContext(currentBlock, pendingState, repository);
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
        long smallGasPrice = BLOCK_AVG_GAS_PRICE - 100_000;
        long accountNonce = repository.getNonce(sender.getAddress()).longValue();

        Transaction smallTx = tx(accountNonce, 200_000, smallGasPrice, 12_500);

        assertTrue("Initial small tx should've been accepted", quotaChecker.acceptTx(smallTx, Optional.empty(), currentContext));

        long bigGasPrice = BLOCK_AVG_GAS_PRICE + 1_000_000;
        Transaction bigTx = tx(accountNonce, 1_000_000, (long) (bigGasPrice * 1.1), 100_000);
        assertTrue("Big tx should've been accepted after a small one", quotaChecker.acceptTx(smallTx, Optional.empty(), currentContext));

        // TODO:I update when behavior is clarified: with the current implementation even the most factored tx will be accepted within milliseconds because we are granting the maxQuota to the sender on every refresh
        Transaction bigTxReplaced = tx(accountNonce, 1_000_000, bigGasPrice, 100_000);
        long approximateLastTxTime = System.currentTimeMillis();
        assertTrue("Big replaced tx should've been rejected when almost no quite time after last big tx", quotaChecker.acceptTx(bigTx, Optional.of(bigTxReplaced), currentContext));

        // TODO:I update when behavior is clarified: calculate "enough" time for this transaction to be accepted
        TxQuota senderQuota = quotaChecker.getTxQuota(sender.getAddress());
        long simulatedOldLastTxTime = approximateLastTxTime - 60 * 1000; // simulate last tx was processed 1 minute ago
        Whitebox.setInternalState(senderQuota, "timestamp", simulatedOldLastTxTime);
        assertTrue("Big replaced tx should've been accepted when enough quite time", quotaChecker.acceptTx(bigTx, Optional.of(bigTxReplaced), currentContext));
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
