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
            BnTagOrNumberValidator.validate("0x123");
            BnTagOrNumberValidator.validate("0x0123");
            BnTagOrNumberValidator.validate("earliest");
            BnTagOrNumberValidator.validate("finalized");
            BnTagOrNumberValidator.validate("safe");
            BnTagOrNumberValidator.validate("latest");
            BnTagOrNumberValidator.validate("pending");
        });
    }

    @Test
    void testInvalidParameters() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.validate("0x");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.validate("0x12j");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.validate("0xGHI");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            BnTagOrNumberValidator.validate("invalid");
        });
    }

    @Test
    void testValidBlockTagPascalCase() {
        assertDoesNotThrow(() -> {
            BnTagOrNumberValidator.validate("Earliest");
            BnTagOrNumberValidator.validate("Finalized");
            BnTagOrNumberValidator.validate("Safe");
            BnTagOrNumberValidator.validate("Latest");
            BnTagOrNumberValidator.validate("Pending");
        });
    }

    @Test
    void testValidBlockTagUppercase() {
        assertDoesNotThrow(() -> {
            BnTagOrNumberValidator.validate("EARLIEST");
            BnTagOrNumberValidator.validate("FINALIZED");
            BnTagOrNumberValidator.validate("SAFE");
            BnTagOrNumberValidator.validate("LATEST");
            BnTagOrNumberValidator.validate("PENDING");
        });
    }

    @Test
    void testWhitespaceString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.validate(" "));
    }

    @Test
    void testEmptyString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.validate(""));
    }

    @Test
    void testNullString() {
        assertThrows(RskJsonRpcRequestException.class, () -> BnTagOrNumberValidator.validate(null));
    }
}