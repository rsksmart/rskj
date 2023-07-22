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
package org.ethereum.rpc;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.validation.BnTagOrNumberValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BnTagOrNumberValidatorTest {
    @Test
    void testValidHexBlockNumberOrId() {
        assertTrue(BnTagOrNumberValidator.isValid("0x123"));
        assertTrue(BnTagOrNumberValidator.isValid("0x0123"));
        assertTrue(BnTagOrNumberValidator.isValid("earliest"));
        assertTrue(BnTagOrNumberValidator.isValid("finalized"));
        assertTrue(BnTagOrNumberValidator.isValid("safe"));
        assertTrue(BnTagOrNumberValidator.isValid("latest"));
        assertTrue(BnTagOrNumberValidator.isValid("pending"));
    }

    @Test
    void testInvalidParameters() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.isValid("0x");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.isValid("0x12j");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.isValid("0xGHI");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.isValid("invalid");
        });
    }

    @Test
    void testValidBlockTagPascalCase() {
        assertTrue(BnTagOrNumberValidator.isValid("Earliest"));
        assertTrue(BnTagOrNumberValidator.isValid("Finalized"));
        assertTrue(BnTagOrNumberValidator.isValid("Safe"));
        assertTrue(BnTagOrNumberValidator.isValid("Latest"));
        assertTrue(BnTagOrNumberValidator.isValid("Pending"));
    }

    @Test
    void testValidBlockTagUppercase() {
        assertTrue(BnTagOrNumberValidator.isValid("EARLIEST"));
        assertTrue(BnTagOrNumberValidator.isValid("FINALIZED"));
        assertTrue(BnTagOrNumberValidator.isValid("SAFE"));
        assertTrue(BnTagOrNumberValidator.isValid("LATEST"));
        assertTrue(BnTagOrNumberValidator.isValid("PENDING"));
    }

    @Test
    void testWhitespaceString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.isValid(" "));
    }

    @Test
    void testEmptyString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.isValid(""));
    }

    @Test
    void testNullString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.isValid(null));
    }

}