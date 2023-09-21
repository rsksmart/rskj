/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexAddressParamTest {

    @Test
    public void testValidHexAddressParam() {
        String validHexAddress = "0x407d73d8a49eeb85d32cf465507dd71d507100c1";

        HexAddressParam hexAddressParam = new HexAddressParam(validHexAddress);

        assertEquals(validHexAddress, hexAddressParam.getAddress().toJsonString());
    }

    @Test
    public void testInvalidHexAddressParam() {
        String invalidHexAddress = "0x407d73d8a4sseb85d32cf465507dd71d507100c1";
        String shorterHexAddress = "0x407d73";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(invalidHexAddress));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(shorterHexAddress));
    }
}
