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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexDurationParamTest {
    @Test
    void testValidHexDurationParam() {
        String validHexValue = "0x1e";
        HexDurationParam hexDurationParam = new HexDurationParam(validHexValue);

        assertNotNull(hexDurationParam);
        assertEquals(30, hexDurationParam.getDuration());
    }

    @Test
    void testInvalidHexDurationParam() {
        String invalidHexValue = "1e"; // Missing hex prefix
        String nonNumericHexValue = "0x1t"; // Non-valid hex value

        assertThrows(RskJsonRpcRequestException.class, () -> new HexDurationParam(invalidHexValue));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexDurationParam(nonNumericHexValue));
    }
}
