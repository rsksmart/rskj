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

class BlockHashValidatorTest {
    @Test
    void testValidBlockHash() {
        boolean result = BlockHashValidator.isValid("0xae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909");
        Assertions.assertTrue(result);
    }

    @Test
    void testValidBlockHashWithoutOx() {
        boolean result = BlockHashValidator.isValid("ae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909");
        Assertions.assertTrue(result);
    }

    @Test
    void testInvalidBlockHashFormat() {
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("0zae3bd321d09d559c148a2eb85a1b395383d176368fac8fdf2a3babf346461909");
        });
    }

    @Test
    void testInvalidLargerBlockHashLength() {
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("1234567890abcdef1234567890abcqasdfasdfdef1234567890abcdef1234567890abcde");
        });
    }

    @Test
    void testInvalidShorterBlockHashLength() {
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            BlockHashValidator.isValid("1234567890abcdef1234564567890abcdef1234567890abcde");
        });
    }

}