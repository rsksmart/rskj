/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.ethereum.TestUtils.assertThrows;

class HexTopicValidatorTest {
    @Test
    void testValidHexTopicWithPrefix() {
        String validHexTopic = "0x1ABF1234567890ABCdEF01234167890ABCDeF01234567890ABCDEF0123456789";
        Assertions.assertTrue(HexTopicValidator.isValid(validHexTopic));
    }

    @Test
    void testValidHexTopicWithoutPrefix() {
        String validHexTopic = "1ABF1234567890ABCDEF01234567890ABCDEF01234567890ABCDEF0123456789";
        boolean result = HexTopicValidator.isValid(validHexTopic);
        Assertions.assertTrue(result);
    }

    @Test
    void testInvalidHexTopicTooShort() {
        String invalidHexTopic = "0x12345";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }

    @Test
    void testInvalidHexTopicTooLong() {
        String invalidHexTopic = "0x1ABF1234567890ABCDEF01234567890ABCDEF01234567890ABCDEF0123456789FF";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }

    @Test
    void testInvalidHexTopicWithInvalidCharacters() {
        String invalidHexTopic = "0x1ABF1234567890ABCDEF01234567890ABGDEF01234567890ABCDEF0123456789";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }

    @Test
    void testNullString() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexTopicValidator.isValid(null));
    }

    @Test
    void testEmptyString() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexTopicValidator.isValid(""));
    }

    @Test
    void testWhitespaceString() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexTopicValidator.isValid(" "));
    }

    @Test
    void testInvalidHexadecimalWithPrefix() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexTopicValidator.isValid("0x1234g6"));
    }

    @Test
    void testValidHexadecimalWithoutPrefix() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexTopicValidator.isValid("123456"));
    }
}
