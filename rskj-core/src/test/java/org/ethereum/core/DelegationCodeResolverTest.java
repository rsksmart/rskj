/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegationCodeResolverTest {

    @Test
    void createDelegatedCode_nullAddress_throws() {
        assertThrows(IllegalStateException.class,
                () -> DelegationCodeResolver.createDelegatedCode(RskAddress.nullAddress()));
    }

    @Test
    void createDelegatedCode_zeroAddress_throws() {
        assertThrows(IllegalStateException.class,
                () -> DelegationCodeResolver.createDelegatedCode(RskAddress.ZERO_ADDRESS));
    }

    @Test
    void createDelegatedCode_roundTripsThroughExtract() {
        RskAddress delegate = new RskAddress("0x00000000000000000000000000000000000000ab");
        byte[] code = DelegationCodeResolver.createDelegatedCode(delegate);

        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertArrayEquals(delegate.getBytes(), DelegationCodeResolver.extractDelegatedAddress(code).getBytes());
    }

    @Test
    void isDelegatedCode_wrongPrefix_returnsFalse() {
        byte[] code = DelegationCodeResolver.createDelegatedCode(
                new RskAddress("0x0000000000000000000000000000000000000001"));
        code[0] = 0x00;

        assertFalse(DelegationCodeResolver.isDelegatedCode(code));
    }
}
