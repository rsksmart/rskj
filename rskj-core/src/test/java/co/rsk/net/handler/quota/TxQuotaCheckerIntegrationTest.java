package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.bc.PendingState;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.TimeProvider;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.rpc.Web3;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TxQuotaCheckerIntegrationTest {

    private static final long BLOCK_AVG_GAS_PRICE = 65_164_000;
    private static final long BLOCK_MIN_GAS_PRICE = 59240;

    private static final int BLOCK_GAS_LIMIT = 6_800_000;
    private static final long MAX_GAS_PER_SECOND = Math.round(BLOCK_GAS_LIMIT * 0.9);

    private TxQuotaChecker quotaChecker;
    private TxQuotaChecker.CurrentContext currentContext;

    private PendingState state;

    private Account accountA;
    private Account accountB;
    private Account accountC;
    private Account contractA;
    private Account contractB;

    private TimeProvider timeProvider;

    @Before
    public void setUp() {
        accountA = new AccountBuilder().name("accountA").build();
        accountB = new AccountBuilder().name("accountB").build();
        accountC = new AccountBuilder().name("accountC").build();
        contractA = new AccountBuilder().name("contractA").build();
        contractB = new AccountBuilder().name("contractB").build();

        Block mockedBlock = mock(Block.class);
        when(mockedBlock.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(BLOCK_GAS_LIMIT));
        when(mockedBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(59_240));

        state = mock(PendingState.class);
        when(state.getNonce(accountA.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountB.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountC.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(contractA.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(contractB.getAddress())).thenReturn(BigInteger.ZERO);

        Repository repository = mock(Repository.class);
        when(repository.getAccountState(accountA.getAddress())).thenReturn(mock(AccountState.class));
        when(repository.getAccountState(accountB.getAddress())).thenReturn(mock(AccountState.class));
        when(repository.getAccountState(accountC.getAddress())).thenReturn(mock(AccountState.class));
        // intentionally omitted contractA "repository.getAccountState()" mocking
        when(repository.getAccountState(contractB.getAddress())).thenReturn(mock(AccountState.class));

        when(repository.isContract(accountA.getAddress())).thenReturn(false);
        when(repository.isContract(accountB.getAddress())).thenReturn(false);
        when(repository.isContract(accountC.getAddress())).thenReturn(false);
        when(repository.isContract(contractA.getAddress())).thenReturn(true);
        when(repository.isContract(contractB.getAddress())).thenReturn(true);

        Web3 web3 = mock(Web3.class);
        when(web3.eth_gasPrice()).thenReturn("0x" + Long.toHexString(BLOCK_AVG_GAS_PRICE));

        currentContext = new TxQuotaChecker.CurrentContext(mockedBlock, state, repository, web3);

        timeProvider = mock(TimeProvider.class);

        quotaChecker = new TxQuotaChecker(timeProvider);

        // 3 items to facilitate testing
        Whitebox.setInternalState(quotaChecker, "accountQuotas", new MaxSizeHashMap<>(3, true));
    }

    @Test
    public void acceptTx_realisticScenario() {
        long accountANonce = 0;

        long elapsedTime = 0;
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime);

        // new account (nonce==0) not enough quota
        // initial quota granted will be MAX_GAS_PER_SECOND (6_120_000) at this point
        // this tx consumes ~6_138_154vg, slightly more than MAX_GAS_PER_SECOND => is not enough
        Transaction firstTx = txFromAccountAToAccountB(accountANonce, 650_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertFalse("firstTx consuming more than MAX_GAS_PER_SECOND should've been rejected", quotaChecker.acceptTx(firstTx, Optional.empty(), currentContext));

        // enough quota ~6_126_120: this tx consumes ~1_684_195vg (factor ~165.5)
        Transaction smallTx = txFromAccountAToAccountB(accountANonce, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 12_500);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1); // 1 ms after last tx
        assertTrue("Initial tx consuming less than MAX_GAS_PER_SECOND should've been accepted", quotaChecker.acceptTx(smallTx, Optional.empty(), currentContext));

        // receiver's quota was modified as well: ~12_246_120vg (MAX_GAS_PER_SECOND initial + MAX_GAS_PER_SECOND * 1s - consumed on received tx)
        // this tx consumes 12_205_986vg (almost 2*MAX_GAS_PER_SECOND) => is enough
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1000); // 1s after last tx
        Transaction txFromAccountB = txFromAccountBToAccountC(0, 1_000_000, BLOCK_AVG_GAS_PRICE - 100_000, 13_250);
        assertTrue("txFromAccountB consuming almost ~MAX_GAS_PER_SECOND*2 should've been accepted", quotaChecker.acceptTx(txFromAccountB, Optional.empty(), currentContext));

        long smallTxReplacementGasPrice = (long) (smallTx.getGasPrice().asBigInteger().longValue() * 1.1d);

        // enough quota 1_112_161_924vg: this tx consumes ~921_722_072vg (factor ~151)
        Transaction smallTxReplacement = txFromAccountAToAccountB(accountANonce, MAX_GAS_PER_SECOND, smallTxReplacementGasPrice, 60_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 3 * 60 * 1000); // 3 minutes after last tx
        assertTrue("smallTxReplacement should've been accepted 3 minutes after the previous tx", quotaChecker.acceptTx(smallTxReplacement, Optional.of(smallTx), currentContext));

        // not enough quota 190_445_971vg for the same tx replacing itself (~921_722_072vg)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1); // 1 ms after last tx
        assertFalse("smallTxReplacement should've been rejected 1 ms after the same tx was executed", quotaChecker.acceptTx(smallTxReplacement, Optional.of(smallTx), currentContext));

        // simulate future nonce on tx
        long futureNonce = accountANonce + 3;

        // not enough quota 557_645_971vg: this tx consumes ~6_405_600_000 (factor ~942)
        Transaction hugeTx = txFromAccountAToAccountB(futureNonce, 6_800_000, BLOCK_MIN_GAS_PRICE, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 60 * 1000); // 1 minute after last tx
        assertFalse("hugeTx should've been rejected 1 minute after last tx", quotaChecker.acceptTx(hugeTx, Optional.empty(), currentContext));

        // enough quota 6_432_845_971vg: this tx consumes ~6_405_600_000vg (factor ~942)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 16 * 60 * 1000); // 16 minutes after last tx
        assertTrue("hugeTx should've been accepted 16 minutes after last tx", quotaChecker.acceptTx(hugeTx, Optional.empty(), currentContext));

        long hugeTxReplacementGasPrice = (long) (hugeTx.getGasPrice().asBigInteger().longValue() * 1.1d);

        // not enough quota 11_043_245_971vg: this tx consumes ~12_228_038_181vg (factor ~1798)
        Transaction hugeTxReplacement = txFromAccountAToAccountB(futureNonce, 6_800_000, hugeTxReplacementGasPrice, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 30 * 60 * 1000); // 30 minutes after last tx
        assertFalse("hugeTxReplacement should've been rejected 30 minutes after last tx", quotaChecker.acceptTx(hugeTxReplacement, Optional.of(hugeTx), currentContext));

        // enough quota 12_240_000_000vg (maxQuota in this case, accumulated was more): this tx consumes ~12_228_038_181vg (factor ~1798)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 3.3 * 60 * 1000); // 3.3 minutes after last tx (~33.3 minutes are needed to fill max quota)
        assertTrue("hugeTxReplacement should've been accepted 3.3 minutes after last tx", quotaChecker.acceptTx(hugeTxReplacement, Optional.of(hugeTx), currentContext));

        // simulate multiple txs were processed
        accountANonce = 100;
        when(state.getNonce(accountA.getAddress())).thenReturn(BigInteger.valueOf(accountANonce));

        // contractA does not exist on state, therefore a quota will be added for it in the map (even being it a contract)
        // therefore accountA will be removed from the map, that is full, as the least recently accessed one
        Transaction txToContractA = txFromAccountB2ContractA(0, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue("txToContractA should've been accepted", quotaChecker.acceptTx(txToContractA, Optional.empty(), currentContext));
        assertNotNull("contractA should create a quota when receiving a transaction", quotaChecker.getTxQuota(contractA.getAddress()));

        // contractA is added again to the map with this tx
        // now sender is NOT a new account (nonce>0), therefore initial quota will be MAX_QUOTA (12_240_000_000) [instead of MAX_GAS_PER_SECOND (6_120_000)]
        // this tx consumes 665_928_712vg (factor ~377), more than MAX_GAS_PER_SECOND and less than MAX_QUOTA => is enough
        Transaction txAfterReinsertOnMap = txFromAccountAToAccountB(accountANonce, 6_800_000, BLOCK_MIN_GAS_PRICE, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 50); // 50 ms after quota was created
        assertTrue("txAfterReinsertOnMap should've been accepted 1 minute after last tx", quotaChecker.acceptTx(txAfterReinsertOnMap, Optional.empty(), currentContext));

        // contractB exists on state, therefore NO quota be added for it in the map
        Transaction txToContractB = txFromAccountCToContractB(0, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue("txToContractB should've been accepted", quotaChecker.acceptTx(txToContractB, Optional.empty(), currentContext));
        assertNull("contractB should not create a quota when receiving a transaction", quotaChecker.getTxQuota(contractB.getAddress()));

    }

    private Transaction txFromAccountAToAccountB(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountA, accountB, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountBToAccountC(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountB, accountC, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountB2ContractA(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountC, contractA, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountCToContractB(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountC, contractB, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFrom(Account sender, Account receiver, long nonce, long gasLimit, long gasPrice, long size) {
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
