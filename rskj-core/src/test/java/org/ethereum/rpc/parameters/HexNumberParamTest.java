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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexNumberParamTest {

    @Test
    void testValidHexNumberParam() {
        String validHexNumber = "0x76c0";
        HexNumberParam hexNumberParam = new HexNumberParam(validHexNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validHexNumber, hexNumberParam.getHexNumber());
    }

    @Test
    void testValidHexNumberParamAsStringNumber() {
        String validStringNumber = "1500";
        HexNumberParam hexNumberParam = new HexNumberParam(validStringNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validStringNumber, hexNumberParam.getHexNumber());
    }

    @Test
    void testInvalidHexNumberParam() {
        String invalidHexNumber = "0x76ty";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(invalidHexNumber));
    }

    @Test
    void testIsHexNumberLengthValid_lengthsEqualsToUpperBounds_returnsTrueAsExpected() {
        // Given
        String maxLengthWithPrefix = "0x000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
        String maxLengthWithoutPrefix = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Then
        assertTrue(HexNumberParam.isHexNumberLengthValid(maxLengthWithPrefix));
        assertTrue(HexNumberParam.isHexNumberLengthValid(maxLengthWithoutPrefix));
    }

    @Test
    void testIsHexNumberLengthValid_lengthsHigherThanUpperBounds_returnsFalseAsExpected() {
        // Given
        String longerThanMaxWithPrefix = "0x000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20";
        String longerThanMaxWithoutPrefix = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20";

        // Then
        assertFalse(HexNumberParam.isHexNumberLengthValid(longerThanMaxWithPrefix));
        assertFalse(HexNumberParam.isHexNumberLengthValid(longerThanMaxWithoutPrefix));
    }

}
