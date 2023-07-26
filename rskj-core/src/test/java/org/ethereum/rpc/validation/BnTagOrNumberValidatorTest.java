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
package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BnTagOrNumberValidatorTest {

    @Test
    void testValidHexBlockNumberOrId() {
        assertDoesNotThrow(() -> {
            BnTagOrNumberValidator.isValid("0x123");
            BnTagOrNumberValidator.isValid("0x0123");
            BnTagOrNumberValidator.isValid("earliest");
            BnTagOrNumberValidator.isValid("finalized");
            BnTagOrNumberValidator.isValid("safe");
            BnTagOrNumberValidator.isValid("latest");
            BnTagOrNumberValidator.isValid("pending");
        });
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
        assertDoesNotThrow(() -> {
            BnTagOrNumberValidator.isValid("Earliest");
            BnTagOrNumberValidator.isValid("Finalized");
            BnTagOrNumberValidator.isValid("Safe");
            BnTagOrNumberValidator.isValid("Latest");
            BnTagOrNumberValidator.isValid("Pending");
        });
    }

    @Test
    void testValidBlockTagUppercase() {
        assertDoesNotThrow(() -> {
            BnTagOrNumberValidator.isValid("EARLIEST");
            BnTagOrNumberValidator.isValid("FINALIZED");
            BnTagOrNumberValidator.isValid("SAFE");
            BnTagOrNumberValidator.isValid("LATEST");
            BnTagOrNumberValidator.isValid("PENDING");
        });
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