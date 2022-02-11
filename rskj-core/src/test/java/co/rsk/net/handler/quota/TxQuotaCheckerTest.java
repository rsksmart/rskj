package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class TxQuotaCheckerTest {

    private static final long BLOCK_MIN_GAS_PRICE = 59240;
    private static final long BLOCK_AVG_GAS_PRICE = 65164000;
    private static final long BLOCK_GAS_LIMIT = 6800000;
    private static final long DEFAULT_NONCE = 22;

    private TxQuotaChecker.CurrentContext currentContext;

    @Parameterized.Parameter(value = 0)
    public String description;

    @Parameterized.Parameter(value = 1)
    public Transaction newTransaction;

    @Parameterized.Parameter(value = 2)
    public Optional<Transaction> replacedTransaction;

    @Parameterized.Parameter(value = 3)
    public boolean isContract;

    @Parameterized.Parameter(value = 4)
    public long accountNonce;

    @Parameterized.Parameter(value = 5)
    public boolean accepted;

    @Parameterized.Parameters(name = "{index}: expect {0} acceptance to be {5}")
    public static Collection<Object[]> data() {
        long smallGasPrice = BLOCK_AVG_GAS_PRICE - 100_000;
        Transaction smallTx = tx(DEFAULT_NONCE, 200_000, (long) (smallGasPrice * 1.1), 12_500);
        Transaction smallTxReplaced = tx(DEFAULT_NONCE, 200_000, smallGasPrice, 12_500);

        long mediumGasPrice = BLOCK_AVG_GAS_PRICE + 100_000;
        Transaction mediumTx = tx(DEFAULT_NONCE, 500_000, (long) (mediumGasPrice * 1.1), 25_000);
        Transaction mediumTxReplaced = tx(DEFAULT_NONCE, 500_000, mediumGasPrice, 12_500);
        Transaction mediumTxWithNonceSmallAndGap = tx(2, 500_000, (long) (mediumGasPrice * 1.1), 25_000);
        Transaction mediumTxReplacedWithNonceSmallAndGap = tx(2, 500_000, mediumGasPrice, 25_000);

        long bigGasPrice = BLOCK_AVG_GAS_PRICE + 500_000;
        Transaction bigTx = tx(DEFAULT_NONCE, 750_000, (long) (bigGasPrice * 1.1), 50_000);
        Transaction bigTxReplaced = tx(DEFAULT_NONCE, 750_000, bigGasPrice, 50_000);

        long hugeGasPrice = BLOCK_AVG_GAS_PRICE + 1_000_000;
        Transaction hugeTx = tx(DEFAULT_NONCE, 1_000_000, (long) (hugeGasPrice * 1.1), 100_000);
        Transaction hugeTxNonceGap = tx(DEFAULT_NONCE + 10, 1_000_000, hugeGasPrice, 100_000);
        Transaction hugeTxReplaced = tx(DEFAULT_NONCE, 1_000_000, hugeGasPrice, 100_000);

        return Arrays.asList(new Object[][]{
                {"Small transaction", smallTx, Optional.empty(), false, DEFAULT_NONCE, true},
                {"Small transaction replaced", smallTx, Optional.of(smallTxReplaced), false, DEFAULT_NONCE, true},

                {"Medium transaction", mediumTx, Optional.empty(), false, DEFAULT_NONCE, true},
                {"Medium transaction replaced", mediumTx, Optional.of(mediumTxReplaced), false, DEFAULT_NONCE, true},
                {"Medium transaction replaced, small nonce and nonce gap", mediumTxWithNonceSmallAndGap, Optional.of(mediumTxReplacedWithNonceSmallAndGap), true, 0, false},

                {"Big transaction", bigTx, Optional.empty(), false, DEFAULT_NONCE, true},
                {"Big transaction replaced", bigTx, Optional.of(bigTxReplaced), false, DEFAULT_NONCE, false},

                {"Huge transaction", hugeTx, Optional.empty(), false, DEFAULT_NONCE, false},
                {"Huge transaction with nonce gap", hugeTxNonceGap, Optional.empty(), false, DEFAULT_NONCE, false},
                {"Huge transaction replaced", hugeTx, Optional.of(hugeTxReplaced), false, DEFAULT_NONCE, false},

                {"Contract receiver transaction", smallTx, Optional.of(smallTxReplaced), false, DEFAULT_NONCE, true},
        });
    }

    @Before
    public void setUp() {
        Block block = block();

        Repository repository = mock(Repository.class);
        when(repository.isContract(any())).thenReturn(isContract);

        PendingState state = mock(PendingState.class);
        when(state.getNonce(any())).thenReturn(BigInteger.valueOf(accountNonce));

        currentContext = new TxQuotaChecker.CurrentContext(block, state, repository);
    }

    @Test
    public void acceptTx() {
        TxQuotaChecker quotaChecker = new TxQuotaChecker();
        assertEquals(accepted, quotaChecker.acceptTx(newTransaction, replacedTransaction, currentContext));

        TxQuota txQuotaSender = quotaChecker.getTxQuota(newTransaction.getSender());
        assertNotNull("Sender address quota should exist when contract", txQuotaSender);

        TxQuota txQuotaReceiver = quotaChecker.getTxQuota(newTransaction.getReceiveAddress());
        assertEquals("Receiver address quota should exist when EOA", txQuotaReceiver == null, isContract);
    }

    private static Transaction tx(long nonce, long gasLimit, long gasPrice, long size) {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

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
        when(mockedTx.getSender()).thenReturn(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(1)).getAddress()));
        when(mockedTx.getSize()).thenReturn(size);

        return mockedTx;
    }

    private static Block block() {
        Block block = mock(Block.class);

        when(block.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(BLOCK_GAS_LIMIT));
        when(block.getAverageGasPrice()).thenReturn(Coin.valueOf(BLOCK_AVG_GAS_PRICE));
        when(block.getMinimumGasPrice()).thenReturn(Coin.valueOf(BLOCK_MIN_GAS_PRICE));

        return block;
    }

}
