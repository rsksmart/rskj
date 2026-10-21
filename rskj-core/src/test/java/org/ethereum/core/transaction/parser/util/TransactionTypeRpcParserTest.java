/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser.util;

import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TransactionTypeRpcParser}.
 * Covers the hex-string-to-{@link TransactionType} parsing used at the JSON-RPC ingress.
 */
class TransactionTypeRpcParserTest {

    @Test
    void fromHex_null_returnsLegacy() {
        assertEquals(TransactionType.LEGACY, TransactionTypeRpcParser.fromHex(null));
    }

    @Test
    void fromHex_explicitDecimalZero_throws() {
        RskJsonRpcRequestException ex = assertThrows(
                RskJsonRpcRequestException.class,
                () -> TransactionTypeRpcParser.fromHex("0"));
        assertTrue(ex.getMessage().contains("explicit type 0x00 is not allowed"),
                "Error should mention explicit 0x00 is not allowed, got: " + ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x0", "0x00"})
    void fromHex_explicitHexZero_throws(String hex) {
        RskJsonRpcRequestException ex = assertThrows(
                RskJsonRpcRequestException.class,
                () -> TransactionTypeRpcParser.fromHex(hex));
        assertTrue(ex.getMessage().contains("explicit type 0x00 is not allowed"),
                "Error should mention explicit 0x00 is not allowed, got: " + ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "0x1", "0x2", "0x3", "0x4", "0x01", "0x02", "0x03", "0x04"})
    void fromHex_validTypes_returnNonLegacy(String hex) {
        TransactionType type = TransactionTypeRpcParser.fromHex(hex);
        assertNotNull(type);
        assertNotEquals(TransactionType.LEGACY, type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "6", "10", "127", "0x5", "0x0a", "0x7f"})
    void fromHex_unknownType_throws(String hex) {
        assertThrows(RskJsonRpcRequestException.class,
                () -> TransactionTypeRpcParser.fromHex(hex));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0xff", "", "0x80", "-1"})
    void fromHex_invalidInput_throws(String hex) {
        assertThrows(RskJsonRpcRequestException.class,
                () -> TransactionTypeRpcParser.fromHex(hex));
    }
}
