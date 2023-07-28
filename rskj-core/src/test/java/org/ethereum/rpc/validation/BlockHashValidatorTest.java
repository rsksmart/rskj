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
import static org.junit.jupiter.api.Assertions.*;

class BlockHashValidatorTest {
    @Test
    void testValidBlockHash() {
        assertDoesNotThrow(() -> BlockHashValidator.isValid("0xae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909"));
    }

    @Test
    void testValidBlockHashWithoutOx() {
        assertDoesNotThrow(() -> BlockHashValidator.isValid("ae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909"));
    }

    @Test
    void testInvalidBlockHashFormat() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("0zae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909");
        });
    }

    @Test
    void testInvalidLargerBlockHashLength() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("1234567890abcdef1234567890abcqasdfasdfdef1234567890abcdef1234567890abcde");
        });
    }

    @Test
    void testInvalidShorterBlockHashLength() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("1234567890abcdef1234564567890abcdef1234567890abcde");
        });
    }

    @Test
    void testValidBlockHashMixedCase() {
        assertDoesNotThrow(() -> BlockHashValidator.isValid("0xAe3Bd321D09D559C148A2Eb85A1B395383D176368Fac8Fdf2A3BabF346461909"));
    }

    @Test
    void testValidBlockHashUppercase() {
        assertDoesNotThrow(() -> BlockHashValidator.isValid("0xAE3BD321D09D559C148A2EB85A1B395383D176368FAC8FDF2A3BABF346461909"));
    }

    @Test
    void testWhitespaceString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BlockHashValidator.isValid(" "));
    }

    @Test
    void testEmptyString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BlockHashValidator.isValid(""));
    }

    @Test
    void testNullString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BlockHashValidator.isValid(null));
    }

    @Test
    void testValidErrorCodeOnException() {
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> BlockHashValidator.isValid("123456"));
        assertEquals(-32602, exception.getCode());
    }
}