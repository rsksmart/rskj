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
    // Reverse lookup: getByByte
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void getByByte_roundTripsForAllEnumValues(TransactionType type) {
        assertEquals(type, TransactionType.getByByte(type.getByteCode()));
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x05, 0x06, 0x10, 0x50, 0x7e, 0x7f})
    void getByByte_returnsNullForUnknownTypes(byte unknownByte) {
        assertNull(TransactionType.getByByte(unknownByte));
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
    // Static: isLegacyTransaction(byte)
    // ========================================================================

    @ParameterizedTest
    @ValueSource(bytes = {(byte) 0xc0, (byte) 0xc1, (byte) 0xf8, (byte) 0xf9, (byte) 0xfe})
    void isLegacyTransaction_trueForRlpListRange(byte b) {
        assertTrue(TransactionType.isLegacyTransaction(b));
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x01, 0x7f, (byte) 0x80, (byte) 0xbf, (byte) 0xff})
    void isLegacyTransaction_falseOutsideRlpListRange(byte b) {
        assertFalse(TransactionType.isLegacyTransaction(b));
    }

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
    // Static: isValidType(byte)
    // ========================================================================

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x01, 0x02, 0x03, 0x04, 0x40, 0x7f})
    void isValidType_trueForValidRange(byte b) {
        assertTrue(TransactionType.isValidType(b));
    }

    @ParameterizedTest
    @ValueSource(bytes = {(byte) 0x80, (byte) 0xc0, (byte) 0xff})
    void isValidType_falseAboveMaxType(byte b) {
        assertFalse(TransactionType.isValidType(b));
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

    // ========================================================================
    // Static: encodeRskType(byte) / decodeRskType(int) — roundtrip
    // ========================================================================

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x01, 0x03, 0x40, 0x7e, 0x7f})
    void encodeRskType_producesExpectedFormat(byte subtype) {
        int encoded = TransactionType.encodeRskType(subtype);
        int expectedHigh = TransactionType.RSK_NAMESPACE_PREFIX;

        assertEquals(expectedHigh, (encoded >> 8) & 0xFF);
        assertEquals(subtype & 0xFF, encoded & 0xFF);
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x01, 0x03, 0x40, 0x7e, 0x7f})
    void encodeRskType_thenDecodeRskType_roundTrips(byte subtype) {
        int encoded = TransactionType.encodeRskType(subtype);
        byte decoded = TransactionType.decodeRskType(encoded);

        assertEquals(subtype, decoded);
    }

    @ParameterizedTest
    @MethodSource("decodeRskTypeProvider")
    void decodeRskType_returnsExpectedSubtype(int encodedType, byte expectedSubtype) {
        assertEquals(expectedSubtype, TransactionType.decodeRskType(encodedType));
    }

    private static Stream<Arguments> decodeRskTypeProvider() {
        return Stream.of(
            Arguments.of(0x0200, (byte) 0x00),
            Arguments.of(0x0201, (byte) 0x01),
            Arguments.of(0x0203, (byte) 0x03),
            Arguments.of(0x0240, (byte) 0x40),
            Arguments.of(0x027f, (byte) 0x7f)
        );
    }

    @ParameterizedTest
    @ValueSource(bytes = {(byte) 0x80, (byte) 0x81, (byte) 0xff})
    void encodeRskType_throwsForSubtypeAboveMaxValue(byte invalidSubtype) {
        assertThrows(IllegalArgumentException.class,
            () -> TransactionType.encodeRskType(invalidSubtype));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0100, 0x0300, 0x0004, 0x0001, 0x0000, 0xffff})
    void decodeRskType_throwsForNonRskEncodedValues(int invalidEncoded) {
        assertThrows(IllegalArgumentException.class,
            () -> TransactionType.decodeRskType(invalidEncoded));
    }

    // ========================================================================
    // Static: isRskSpecificType(int)
    // ========================================================================

    @ParameterizedTest
    @ValueSource(ints = {0x0200, 0x0201, 0x0203, 0x0240, 0x027f})
    void isRskSpecificType_trueForValidRskEncoding(int encoded) {
        assertTrue(TransactionType.isRskSpecificType(encoded));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x01, 0x04, 0x0100, 0x0300, 0x0280, 0x02ff, 0xffff})
    void isRskSpecificType_falseForNonRskValues(int encoded) {
        assertFalse(TransactionType.isRskSpecificType(encoded));
    }

    // ========================================================================
    // Static: toReceiptString(int)
    // ========================================================================

    @ParameterizedTest
    @MethodSource("receiptStringProvider")
    void toReceiptString_formatsCorrectly(int typeValue, String expected) {
        assertEquals(expected, TransactionType.toReceiptString(typeValue));
    }

    private static Stream<Arguments> receiptStringProvider() {
        return Stream.of(
            Arguments.of(0x00, "0x0"),
            Arguments.of(0x01, "0x1"),
            Arguments.of(0x02, "0x2"),
            Arguments.of(0x03, "0x3"),
            Arguments.of(0x04, "0x4"),
            Arguments.of(0x0200, "0x200"),
            Arguments.of(0x0203, "0x203"),
            Arguments.of(0x027f, "0x27f")
        );
    }

    // ========================================================================
    // Static: getTypeName(int)
    // ========================================================================

    @ParameterizedTest
    @MethodSource("staticTypeNameProvider")
    void getTypeName_static_returnsExpectedLabel(int typeValue, String expected) {
        assertEquals(expected, TransactionType.getTypeName(typeValue));
    }

    private static Stream<Arguments> staticTypeNameProvider() {
        return Stream.of(
            Arguments.of(0x00, "Legacy"),
            Arguments.of(0x01, "RSKIP546 (EIP-2930 Access List)"),
            Arguments.of(0x02, "RSKIP546 (EIP-1559 Dynamic Fee)"),
            Arguments.of(0x03, "RSKIP000 (Blob)"),
            Arguments.of(0x04, "RSKIP545 (EIP-7702 Set Code)")
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0200, 0x0203, 0x027f})
    void getTypeName_static_returnsRskTypeForNamespaceValues(int rskType) {
        String name = TransactionType.getTypeName(rskType);
        assertTrue(name.startsWith("RSK Type"),
            "Expected RSK Type label, got: " + name);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x05, 0x10, 0x7f})
    void getTypeName_static_returnsUnknownForUnmappedTypes(int unknownType) {
        String name = TransactionType.getTypeName(unknownType);
        assertTrue(name.startsWith("Unknown"),
            "Expected Unknown label, got: " + name);
    }
}
