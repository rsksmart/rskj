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
    public void nonceInRange() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getNonceAsInteger()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(tx2.getNonceAsInteger()).thenReturn(BigInteger.valueOf(3));
        Mockito.when(as.getNonce()).thenReturn(BigInteger.valueOf(0));

        TxValidatorNonceRangeValidator tvnrv = new TxValidatorNonceRangeValidator();
        Assert.assertTrue(tvnrv.validate(tx1, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertTrue(tvnrv.validate(tx2, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    public void nonceOutOfRange() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getNonceAsInteger()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(tx2.getNonceAsInteger()).thenReturn(BigInteger.valueOf(6));
        Mockito.when(as.getNonce()).thenReturn(BigInteger.valueOf(1));

        TxValidatorNonceRangeValidator tvnrv = new TxValidatorNonceRangeValidator();
        Assert.assertFalse(tvnrv.validate(tx1, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
        Assert.assertFalse(tvnrv.validate(tx2, as, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }
}
