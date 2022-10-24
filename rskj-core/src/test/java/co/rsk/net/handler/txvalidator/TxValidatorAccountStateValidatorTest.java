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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TxValidatorAccountStateValidatorTest {

    @Test
    void validAccountState() {
        AccountState state = Mockito.mock(AccountState.class);
        Mockito.when(state.isDeleted()).thenReturn(false);

        TxValidatorAccountStateValidator tvasv = new TxValidatorAccountStateValidator();
        Assertions.assertTrue(tvasv.validate(null, state, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void invalidAccountState() {
        AccountState state = Mockito.mock(AccountState.class);
        Mockito.when(state.isDeleted()).thenReturn(true);

        TxValidatorAccountStateValidator tvasv = new TxValidatorAccountStateValidator();
        Assertions.assertFalse(tvasv.validate(null, state, null, null, Long.MAX_VALUE, false).transactionIsValid());
    }
}
