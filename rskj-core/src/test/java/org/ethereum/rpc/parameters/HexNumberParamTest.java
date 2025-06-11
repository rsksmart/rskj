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

public class HexNumberParamTest {

    @Test
    public void testValidHexNumberParam() {
        String validHexNumber = "0x76c0";
        HexNumberParam hexNumberParam = new HexNumberParam(validHexNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validHexNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testValidHexNumberParamAsStringNumber() {
        String validStringNumber = "1500";
        HexNumberParam hexNumberParam = new HexNumberParam(validStringNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validStringNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testInvalidHexNumberParam() {
        String invalidHexNumber = "0x76ty";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(invalidHexNumber));
    }
}
