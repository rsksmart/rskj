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

import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TxValidatorNonceEncodingValidatorTest {

    private TxValidatorNonceEncodingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TxValidatorNonceEncodingValidator();
    }

    @Test
    void nullNonce_isValid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(null);

        Assertions.assertTrue(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void singleByteZeroNonce_isValid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x00});

        Assertions.assertTrue(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void singleByteNonce_isValid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x01});

        Assertions.assertTrue(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void eightByteNonceNoLeadingZeros_isValid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});

        Assertions.assertTrue(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void twoByteNonceNoLeadingZeros_isValid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x01, 0x00});

        Assertions.assertTrue(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void nineByteNonce_isInvalid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[9]);

        Assertions.assertFalse(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void nineByteNonceWithNonZeroFirstByte_isInvalid() {
        Transaction tx = Mockito.mock(Transaction.class);
        byte[] nonce = new byte[9];
        nonce[0] = 0x01;
        Mockito.when(tx.getNonce()).thenReturn(nonce);

        Assertions.assertFalse(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void twoByteNonceWithLeadingZero_isInvalid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x00, 0x01});

        Assertions.assertFalse(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void threeByteNonceWithLeadingZeros_isInvalid() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x00, 0x00, 0x05});

        Assertions.assertFalse(validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }
}
