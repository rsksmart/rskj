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

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

class TxValidatorGasLimitValidatorTest {
    @Test
    void validGasLimit() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(6));

        BigInteger gl = BigInteger.valueOf(6);

        TxValidatorGasLimitValidator tvglv = new TxValidatorGasLimitValidator();

        Assertions.assertTrue(tvglv.validate(tx1, null, gl, null, Long.MAX_VALUE, false).transactionIsValid());
        Assertions.assertTrue(tvglv.validate(tx2, null, gl, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void invalidGasLimit() {

        Transaction tx1 = Mockito.mock(Transaction.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(6));
        Mockito.when(tx1.getHash()).thenReturn(Keccak256.ZERO_HASH);

        BigInteger gl = BigInteger.valueOf(3);

        TxValidatorGasLimitValidator tvglv = new TxValidatorGasLimitValidator();

        Assertions.assertFalse(tvglv.validate(tx1, null, gl, null, Long.MAX_VALUE, false).transactionIsValid());
    }
}
