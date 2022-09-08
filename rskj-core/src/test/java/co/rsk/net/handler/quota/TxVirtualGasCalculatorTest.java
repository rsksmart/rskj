/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net.handler.quota;

import co.rsk.core.RskAddress;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TxVirtualGasCalculatorTest {

    private static final long BLOCK_MIN_GAS_PRICE = 59240;
    private static final long BLOCK_AVG_GAS_PRICE = 65164000;
    private static final long BLOCK_GAS_LIMIT = 6800000;
    private static final long DEFAULT_NONCE = 100;

    // TODO:I check if this output is fine

    @ParameterizedTest(name = "{index}: expect {0} consumed virtual gas to be txGasLimit * {4} * {5} * {6} * {7} * {8} * {9}")
    @ArgumentsSource(TransactionArgumentsProvider.class)
    public void calculate(String description, Transaction newTransaction, Transaction replacedTransaction, long accountNonce, double futureNonceFactor, double lowGasPriceFactor, double nonceFactor, double sizeFactor, double replacementFactor, double gasLimitFactor) {
        TxVirtualGasCalculator txVirtualGasCalculator = TxVirtualGasCalculator.createWithAllFactors(accountNonce, BLOCK_GAS_LIMIT, BLOCK_MIN_GAS_PRICE, BLOCK_AVG_GAS_PRICE);
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

    private static class TransactionArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            long smallGasPrice = BLOCK_AVG_GAS_PRICE;
            Transaction smallFactorsTx = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 1024);
            long smallNonce = 0;
            Transaction highNonceFactorTx = tx(smallNonce, 100_000, smallGasPrice, 1024);
            Transaction highFutureNonceFactorTx = tx(DEFAULT_NONCE + 100, 100_000, smallGasPrice, 1024);
            Transaction highSizeFactorTx = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 100_000);
            Transaction highLowGasPriceFactorTx = tx(DEFAULT_NONCE, 100_000, BLOCK_MIN_GAS_PRICE, 1024);
            Transaction highGasLimitFactorTx = tx(DEFAULT_NONCE, BLOCK_GAS_LIMIT, smallGasPrice, 1024);
            Transaction highReplacementFactorTx = tx(DEFAULT_NONCE, 100_000, Double.valueOf(smallGasPrice * 1.1).longValue(), 1024);
            Transaction highReplacementFactorTxReplaced = tx(DEFAULT_NONCE, 100_000, smallGasPrice, 1024);
            Transaction topFactorsTx = tx(smallNonce + 3, BLOCK_GAS_LIMIT, Double.valueOf(BLOCK_MIN_GAS_PRICE * 1.1).longValue(), 100_000);
            Transaction topFactorsTxReplaced = tx(smallNonce + 3, BLOCK_GAS_LIMIT, BLOCK_MIN_GAS_PRICE, 100_000);

            return Stream.of(
                    Arguments.of("Small factor", smallFactorsTx, null, DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 1, 1.058824),
                    Arguments.of("High nonce factor", highNonceFactorTx, null, smallNonce, 1, 1, 5, 1.04096, 1, 1.058824),
                    Arguments.of("High future nonce factor", highFutureNonceFactorTx, null, DEFAULT_NONCE, 2, 1, 1.039604, 1.04096, 1, 1.058824),
                    Arguments.of("High size factor", highSizeFactorTx, null, DEFAULT_NONCE, 1, 1, 1.039604, 5, 1, 1.058824),
                    Arguments.of("High low gasPrice factor", highLowGasPriceFactorTx, null, DEFAULT_NONCE, 1, 4, 1.039604, 1.04096, 1, 1.058824),
                    Arguments.of("High gasLimit factor", highGasLimitFactorTx, null, DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 1, 5),
                    Arguments.of("High replacement factor", highReplacementFactorTx, highReplacementFactorTxReplaced, DEFAULT_NONCE, 1, 1, 1.039604, 1.04096, 1.90909, 1.058824),
                    Arguments.of("Top factors", topFactorsTx, topFactorsTxReplaced, smallNonce, 2, 3.999727, 5, 5, 1.90909, 5)
            );
        }
    }

}
