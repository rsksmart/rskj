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

import static org.ethereum.TestUtils.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

class BnTagOrNumberValidatorTest {
    @Test
    void testValidHexBlockNumberOrId() {
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("0x123"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("0x0123"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("earliest"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("finalized"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("safe"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("latest"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("pending"));
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
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("Earliest"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("Finalized"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("Safe"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("Latest"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("Pending"));
    }

    @Test
    void testValidBlockTagUppercase() {
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("EARLIEST"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("FINALIZED"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("SAFE"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("LATEST"));
        assertDoesNotThrow(() -> BnTagOrNumberValidator.isValid("PENDING"));
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

    @Test
    void testValidErrorCodeOnException() {
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.isValid("123456"));
        assertEquals(-32602, exception.getCode());
    }
}