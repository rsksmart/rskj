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

import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

public class TxValidatorNonceRangeValidatorTest {

    @Test
    public void oneSlotRange() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getNonceAsInteger()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(tx2.getNonceAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx3.getNonceAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(as.getNonce()).thenReturn(BigInteger.valueOf(1));

        TxValidatorNonceRangeValidator tvnrv = new TxValidatorNonceRangeValidator(1);
        Assert.assertFalse(tvnrv.validate(tx1, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertTrue(tvnrv.validate(tx2, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertFalse(tvnrv.validate(tx3, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    public void fiveSlotsRange() {
        Transaction[] txs = new Transaction[7];
        for (int i = 0; i < 7; i++) {
            txs[i] = Mockito.mock(Transaction.class);
            Mockito.when(txs[i].getNonceAsInteger()).thenReturn(BigInteger.valueOf(i));
        }

        AccountState as = Mockito.mock(AccountState.class);
        Mockito.when(as.getNonce()).thenReturn(BigInteger.valueOf(1));

        TxValidatorNonceRangeValidator tvnrv = new TxValidatorNonceRangeValidator(5);

        for (int i = 0; i < 7; i++) {
            Transaction tx = txs[i];
            long txNonce = tx.getNonceAsInteger().longValue();
            boolean isValid = tvnrv.validate(tx, as, null, null, Long.MAX_VALUE, false).transactionIsValid();
            Assert.assertEquals(isValid, txNonce >= 1 && txNonce <= 5); // only valid if tx nonce is in the range [1; 5]
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalAccountSlotsValue_Zero() {
        new TxValidatorNonceRangeValidator(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalAccountSlotsValue_Negative() {
        new TxValidatorNonceRangeValidator(-1);
    }
}
