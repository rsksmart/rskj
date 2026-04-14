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
package org.ethereum.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionType enum and its static utility methods (RSKIP543).
 */
class TransactionTypeTest {

    // ========================================================================
    // Enum values and byte codes
    // ========================================================================

    @ParameterizedTest
    @MethodSource("enumByteCodeProvider")
    void enumByteCode_matchesExpected(TransactionType type, byte expectedByteCode) {
        assertEquals(expectedByteCode, type.getByteCode());
    }

    private static Stream<Arguments> enumByteCodeProvider() {
        return Stream.of(
            Arguments.of(TransactionType.LEGACY, (byte) 0x00),
            Arguments.of(TransactionType.TYPE_1, (byte) 0x01),
            Arguments.of(TransactionType.TYPE_2, (byte) 0x02),
            Arguments.of(TransactionType.TYPE_3, (byte) 0x03),
            Arguments.of(TransactionType.TYPE_4, (byte) 0x04)
        );
    }

    @Test
    void constants_haveExpectedValues() {
        assertEquals((byte) 0x02, TransactionType.RSK_NAMESPACE_PREFIX);
        assertEquals((byte) 0x7f, TransactionType.MAX_TYPE_VALUE);
    }

    // ========================================================================
    // Reverse lookup: fromByte
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void fromByte_encodeDecodeForAllEnumValues(TransactionType type) {
        assertEquals(type, TransactionType.fromByte(type.getByteCode()));
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x05, 0x06, 0x10, 0x50, 0x7e, 0x7f})
    void fromByte_returnsNullForUnknownTypes(byte unknownByte) {
        assertNull(TransactionType.fromByte(unknownByte));
    }

    // ========================================================================
    // Instance methods: isLegacy(), isTyped()
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void isLegacy_and_isTyped_areMutuallyExclusive(TransactionType type) {
        boolean expectLegacy = (type == TransactionType.LEGACY);
        assertEquals(expectLegacy, type.isLegacy());
        assertEquals(!expectLegacy, type.isTyped());
    }

    // ========================================================================
    // Instance method: getTypeName()
    // ========================================================================

    @ParameterizedTest
    @MethodSource("typeNameProvider")
    void getTypeName_returnsExpectedLabel(TransactionType type, String expectedName) {
        assertEquals(expectedName, type.getTypeName());
    }

    private static Stream<Arguments> typeNameProvider() {
        return Stream.of(
            Arguments.of(TransactionType.LEGACY, "Legacy"),
            Arguments.of(TransactionType.TYPE_1, "RSKIP546 (EIP-2930 Access List)"),
            Arguments.of(TransactionType.TYPE_2, "RSKIP546 (EIP-1559 Dynamic Fee)"),
            Arguments.of(TransactionType.TYPE_3, "RSKIP000 (Blob)"),
            Arguments.of(TransactionType.TYPE_4, "RSKIP545 (EIP-7702 Set Code)")
        );
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void getTypeName_isNeverNullOrEmpty(TransactionType type) {
        assertNotNull(type.getTypeName());
        assertFalse(type.getTypeName().isEmpty());
    }

    // ========================================================================
    // Static: isReservedByte(byte)
    // ========================================================================

    @Test
    void isReservedByte_trueFor0xff() {
        assertTrue(TransactionType.isReservedByte((byte) 0xff));
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x01, 0x7f, (byte) 0xc0, (byte) 0xfe})
    void isReservedByte_falseForNon0xff(byte b) {
        assertFalse(TransactionType.isReservedByte(b));
    }

    // ========================================================================
    // Static: hasRskNamespacePrefix(byte[])
    // ========================================================================

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x03, 0x7f})
    void hasRskNamespacePrefix_trueWhenSecondByteIsValidSubtype(byte subtype) {
        byte[] data = {TransactionType.RSK_NAMESPACE_PREFIX, subtype, (byte) 0xc0};
        assertTrue(TransactionType.hasRskNamespacePrefix(data));
    }

    @Test
    void hasRskNamespacePrefix_falseWhenSecondByteExceedsMaxType() {
        byte[] data = {TransactionType.RSK_NAMESPACE_PREFIX, (byte) 0x80, (byte) 0xc0};
        assertFalse(TransactionType.hasRskNamespacePrefix(data));
    }

    @Test
    void hasRskNamespacePrefix_falseWhenFirstByteIsNotNamespacePrefix() {
        byte[] data = {0x01, 0x03, (byte) 0xc0};
        assertFalse(TransactionType.hasRskNamespacePrefix(data));
    }

    @Test
    void hasRskNamespacePrefix_falseForSingleByte() {
        byte[] data = {TransactionType.RSK_NAMESPACE_PREFIX};
        assertFalse(TransactionType.hasRskNamespacePrefix(data));
    }
}
