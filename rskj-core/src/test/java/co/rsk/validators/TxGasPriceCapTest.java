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

package co.rsk.validators;

import co.rsk.core.Coin;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TxGasPriceCapTest {

    @Test
    public void isSurpassed_whenMinGasPriceIsZeroThenFalseRegardlessTxGasPrice() {
        long minGasPriceRef = 0L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long txGasPrice = minGasPriceRef + 1_000_000_000_000L;

        for (TxGasPriceCap cap : TxGasPriceCap.values()) {
            Transaction tx = mock(Transaction.class);
            when(tx.getGasPrice()).thenReturn(Coin.valueOf(txGasPrice));
            Assert.assertFalse(cap + " should have not been surpassed", cap.isSurpassed(tx, minGasPrice));
        }
    }

    @Test
    public void isSurpassed_whenRemascTransactionThenFalseRegardlessTxGasPrice() {
        long minGasPriceRef = 1L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);
        long txGasPrice = minGasPriceRef + 1_000_000_000_000L;

        for (TxGasPriceCap cap : TxGasPriceCap.values()) {
            RemascTransaction tx = mock(RemascTransaction.class);
            when(tx.getGasPrice()).thenReturn(Coin.valueOf(txGasPrice));
            Assert.assertFalse(cap + " should have not been surpassed", cap.isSurpassed(tx, minGasPrice));
        }
    }

    @Test
    public void isSurpassed_whenLessThanCapThenFalse() {
        long minGasPriceRef = 1L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);

        for (TxGasPriceCap cap : TxGasPriceCap.values()) {
            Transaction tx = mock(Transaction.class);
            long capPrice = minGasPrice.multiply(cap.timesMinGasPrice).asBigInteger().longValue();
            when(tx.getGasPrice()).thenReturn(Coin.valueOf(capPrice - 1));
            Assert.assertFalse(cap + " should have not been surpassed", cap.isSurpassed(tx, minGasPrice));
        }
    }

    @Test
    public void isSurpassed_whenEqualsCapThenFalse() {
        long minGasPriceRef = 1L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);

        for (TxGasPriceCap cap : TxGasPriceCap.values()) {
            Transaction tx = mock(Transaction.class);
            long capPrice = minGasPrice.multiply(cap.timesMinGasPrice).asBigInteger().longValue();
            when(tx.getGasPrice()).thenReturn(Coin.valueOf(capPrice));
            Assert.assertFalse(cap + " should have not been surpassed", cap.isSurpassed(tx, minGasPrice));
        }
    }

    @Test
    public void isSurpassed_whenMoreThanCapThenTrue() {
        long minGasPriceRef = 1L;
        Coin minGasPrice = Coin.valueOf(minGasPriceRef);

        for (TxGasPriceCap cap : TxGasPriceCap.values()) {
            Transaction tx = mock(Transaction.class);
            long capPrice = minGasPrice.multiply(cap.timesMinGasPrice).asBigInteger().longValue();
            when(tx.getGasPrice()).thenReturn(Coin.valueOf(capPrice + 1));
            Assert.assertTrue(cap + " should have been surpassed", cap.isSurpassed(tx, minGasPrice));
        }
    }
}
