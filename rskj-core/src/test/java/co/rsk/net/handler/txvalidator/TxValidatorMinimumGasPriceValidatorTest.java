/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TxValidatorMinimumGasPriceValidatorTest {

    @Test
    public void validMinimumGasPrice() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);

        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(10));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(11));
        Mockito.when(tx3.getGasPrice()).thenReturn(Coin.valueOf(500000000));

        TxValidatorMinimuGasPriceValidator tvmgpv = new TxValidatorMinimuGasPriceValidator();


        Assert.assertTrue(tvmgpv.validate(tx1, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertTrue(tvmgpv.validate(tx2, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertTrue(tvmgpv.validate(tx3, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    public void invalidMinimumGasPrice() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);

        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(9));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(0));
        Mockito.when(tx3.getGasPrice()).thenReturn(null);

        TxValidatorMinimuGasPriceValidator tvmgpv = new TxValidatorMinimuGasPriceValidator();

        Assert.assertFalse(tvmgpv.validate(tx1, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertFalse(tvmgpv.validate(tx2, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertFalse(tvmgpv.validate(tx3, null, null, Coin.valueOf(10L), Long.MAX_VALUE, false).transactionIsValid());
    }

}
