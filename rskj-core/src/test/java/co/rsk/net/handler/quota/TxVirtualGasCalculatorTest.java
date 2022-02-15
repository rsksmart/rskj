package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class TxVirtualGasCalculatorTest {

    private static final long BLOCK_MIN_GAS_PRICE = 59240;
    private static final long BLOCK_AVG_GAS_PRICE = 65164000;
    private static final long BLOCK_GAS_LIMIT = 6800000;
    private static final long DEFAULT_NONCE = 100;

    @Parameterized.Parameter(value = 0)
    public String description;

    @Parameterized.Parameter(value = 1)
    public Transaction newTransaction;

    @Parameterized.Parameter(value = 2)
    public Optional<Transaction> replacedTransaction;

    @Parameterized.Parameter(value = 3)
    public long accountNonce;

    @Parameterized.Parameter(value = 4)
    public double futureNonceFactor;

    @Parameterized.Parameter(value = 5)
    public double lowGasPriceFactor;

    @Parameterized.Parameter(value = 6)
    public double nonceFactor;

    @Parameterized.Parameter(value = 7)
    public double sizeFactor;

    @Parameterized.Parameter(value = 8)
    public double replacementFactor;

    @Parameterized.Parameter(value = 9)
    public double gasLimitFactor;

    @Parameterized.Parameters(name = "{index}: expect {0} consumed virtual gas to be txGasLimit * {4} * {5} * {6} * {7} * {8} * {9}")
    public static Collection<Object[]> data() {
        long smallGasPrice = BLOCK_AVG_GAS_PRICE;
        Transaction smallFactorsTx = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 1024);
        long smallNonce = 0;
        Transaction highNonceFactorTx = tx(smallNonce, 100_000, smallGasPrice, 1024);
        Transaction highFutureNonceFactorTx = tx(DEFAULT_NONCE + 100, 100_000, smallGasPrice, 1024);
        Transaction highSizeFactorTx = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 100_000);
        Transaction highLowGasPriceFactorTx = tx(DEFAULT_NONCE, 100_000, BLOCK_MIN_GAS_PRICE, 1024);
        Transaction highGasLimitFactorTx = tx(DEFAULT_NONCE, BLOCK_GAS_LIMIT, smallGasPrice, 1024);
        Transaction highReplacementFactorTx = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 1024);
        Transaction allHighFactorsTx = tx(smallNonce + 3, BLOCK_GAS_LIMIT, BLOCK_MIN_GAS_PRICE, 100_000);

        return Arrays.asList(new Object[][]{
                {"Small factor", smallFactorsTx, Optional.empty(), DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 1, 1.058824},
                {"High nonce factor", highNonceFactorTx, Optional.empty(), smallNonce, 1, 1, 5, 1.04096, 1, 1.058824},
                {"High future nonce factor", highFutureNonceFactorTx, Optional.empty(), DEFAULT_NONCE, 2, 1, 1.039604, 1.04096, 1, 1.058824},
                {"High size factor", highSizeFactorTx, Optional.empty(), DEFAULT_NONCE, 1, 1, 1.039604, 5, 1, 1.058824},
                {"High low gasPrice factor", highLowGasPriceFactorTx, Optional.empty(), DEFAULT_NONCE, 1, 4, 1.039604, 1.04096, 1, 1.058824},
                {"High gasLimit factor", highGasLimitFactorTx, Optional.empty(), DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 1, 5},
                {"High replacement factor", highReplacementFactorTx, Optional.of(highReplacementFactorTx), DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 2, 1.058824},
                {"All high factors", allHighFactorsTx, Optional.of(allHighFactorsTx), smallNonce, 2, 4, 5, 5, 2, 5},
        });
    }

    @Test
    public void calculate() {
        TxVirtualGasCalculator txVirtualGasCalculator = new TxVirtualGasCalculator(accountNonce, BLOCK_GAS_LIMIT, BLOCK_MIN_GAS_PRICE, BLOCK_AVG_GAS_PRICE);
        double expected = newTransaction.getGasLimitAsInteger().longValue() * futureNonceFactor * lowGasPriceFactor * nonceFactor * sizeFactor * replacementFactor * gasLimitFactor;
        double actual = txVirtualGasCalculator.calculate(newTransaction, replacedTransaction);
        assertEquals(expected, actual, expected * 0.0000005 * 6);
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
        when(block.getMinimumGasPrice()).thenReturn(Coin.valueOf(BLOCK_MIN_GAS_PRICE));
        return block;
    }

}
