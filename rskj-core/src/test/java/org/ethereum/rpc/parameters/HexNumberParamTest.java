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
    public void testValidHexStringInput_executesAsExpected() {
        // Given
        String validHexStringInput = "0x76c0";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(validHexStringInput);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(validHexStringInput, hexNumberParam.getHexNumber());
    }

    @Test
    public void testMaxLengthHexStringInput_executesAsExpected() {
        // Given
        // Length 0x + 64 characters
        String maxLengthInput = "0xA1A2A3A4A5A6A7A8A91011121314151617181920212223242526272829303132";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(maxLengthInput);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(maxLengthInput, hexNumberParam.getHexNumber());
    }

    @Test
    public void testBiggestNumberHexStringInput_executesAsExpected() {
        // Given
        // Length 0x + 64 characters
        String biggestNumber = "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(biggestNumber);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(biggestNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testLowestNumberHexStringInput_executesAsExpected() {
        // Given
        String lowestNumber = "0x00";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(lowestNumber);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(lowestNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testNoLeadingZeroSingleByteHexStringInput_executesAsExpected() {
        // Given
        String noLeadingZeroSingleByte = "0x1";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(noLeadingZeroSingleByte);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(noLeadingZeroSingleByte, hexNumberParam.getHexNumber());
    }

    @Test
    public void testSingleByteHexStringInput_executesAsExpected() {
        // Given
        String leadingZeroSingleByte = "0x01";

        // When
        HexNumberParam hexNumberParam = new HexNumberParam(leadingZeroSingleByte);

        // Then
        assertNotNull(hexNumberParam);
        assertEquals(leadingZeroSingleByte, hexNumberParam.getHexNumber());
    }

    @Test
    public void testInvalidCharactersHexStringInput_throwsExceptionAsExpected() {
        // Given
        String invalidCharactersHexNumber = "0x76ty";

        // Then
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(invalidCharactersHexNumber));
        assertEquals("Invalid param: invalid hex string.", ex.getMessage());
    }

    @Test
    public void testHexStringWithoutPrefixInput_throwsExceptionAsExpected() {
        // Given
        String hexStringWithoutPrefix = "AABB";

        // Then
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(hexStringWithoutPrefix));
        assertEquals("Invalid param: invalid hex string.", ex.getMessage());
    }

    @Test
    public void testEmptyStringInput_throwsExceptionAsExpected() {
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(""));
        assertEquals("Invalid param: invalid hex string.", ex.getMessage());
    }

    @Test
    public void testNullStringInput_throwsExceptionAsExpected() {
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(null));
        assertEquals("Invalid param: invalid hex string.", ex.getMessage());
    }

    @Test
    public void testLongerThanMaxHexStringInput_executesAsExpected() {
        // Given
        // Length 0x + 65 characters (maximum length 0x + 64 characters)
        String longerThanMaximum = "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF0";

        // Then
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(longerThanMaximum));
        assertEquals("Invalid param: invalid hex length.", ex.getMessage());
    }

}
