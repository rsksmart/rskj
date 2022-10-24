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
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TxValidatorNotRemascTxValidatorTest {

    @Test
    void remascTx() {
        TxValidatorNotRemascTxValidator validator = new TxValidatorNotRemascTxValidator();
        Transaction tx1 = Mockito.mock(RemascTransaction.class);
        Mockito.when(tx1.getHash()).thenReturn(Keccak256.ZERO_HASH);
        Assertions.assertFalse(validator.validate(tx1, null, null, null, 0, false).transactionIsValid());
    }

    @Test
    void commonTx() {
        TxValidatorNotRemascTxValidator validator = new TxValidatorNotRemascTxValidator();
        Assertions.assertTrue(validator.validate(Mockito.mock(Transaction.class), null, null, null, 0, false).transactionIsValid());
    }
}
