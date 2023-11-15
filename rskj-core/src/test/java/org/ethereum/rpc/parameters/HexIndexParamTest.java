/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import static org.junit.jupiter.api.Assertions.*;

class HexIndexParamTest {

    @Test
    void testValidHexIndexParam() {
        String validHexValue = "0x123";
        HexIndexParam hexIndexParam = new HexIndexParam(validHexValue);

        assertNotNull(hexIndexParam);
        assertEquals(291, hexIndexParam.getIndex());
    }

    @Test
    void testInvalidHexIndexParam() {
        String invalidHexValue = "123"; // Missing hex prefix
        String nonNumericHexValue = "0xabcz"; // Non-valid hex value
        String emptyString = ""; // empty value
        String invalidIndex = "0xf1652d8322a880e520f996f7d28b645814a58a202d7d2ab7f058e5566fe4f9f3"; // Invalid index

        assertThrows(RskJsonRpcRequestException.class, () -> new HexIndexParam(invalidHexValue));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexIndexParam(nonNumericHexValue));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexIndexParam(emptyString));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexIndexParam(invalidIndex));
    }

}