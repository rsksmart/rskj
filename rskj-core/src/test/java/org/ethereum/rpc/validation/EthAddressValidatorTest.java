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
import org.junit.jupiter.api.Test;

import static org.ethereum.TestUtils.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

class EthAddressValidatorTest {
    @Test
    void testValidAddress() {
        assertDoesNotThrow(() -> EthAddressValidator.isValid("0x023827714750bf8c232ed8856049dc6dd42a693c"));
        assertDoesNotThrow(() -> EthAddressValidator.isValid("0x023827714750bf8c232ed8856049dc6dd42a693"));
    }

    @Test
    void testInvalidAddress() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x12345");
        });

        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x02382771475f2ed8856049dc6dd42a693c");
        });
        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x0y3827714750bf8c232ed8856049dc6dd42a693c");
        });
    }

    @Test
    void testValidAddressWithoutPrefix() {
        assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid("123456"));
    }

    @Test
    void testInvalidAddressWithPrefix() {
        assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid("0x1234g6"));
    }

    @Test
    void testWhitespaceString() {
        assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid(" "));
    }

    @Test
    void testEmptyString() {
        assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid(""));
    }

    @Test
    void testNullString() {
        assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid(null));
    }

    @Test
    void testValidErrorCodeOnException() {
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> EthAddressValidator.isValid("123456"));
        assertEquals(-32602, exception.getCode());
    }
}