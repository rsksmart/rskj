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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionType enum and its static utility methods (RSKIP543).
 */
class TransactionTypeTest {

    // ========================================================================
    // Enum values and byte codes
    // ========================================================================

    @Test
    void testEnumByteCodes() {
        assertEquals((byte) 0x00, TransactionType.LEGACY.getByteCode());
        assertEquals((byte) 0x01, TransactionType.TYPE_1.getByteCode());
        assertEquals((byte) 0x02, TransactionType.TYPE_2.getByteCode());
        assertEquals((byte) 0x03, TransactionType.TYPE_3.getByteCode());
        assertEquals((byte) 0x04, TransactionType.TYPE_4.getByteCode());
    }

    @Test
    void testConstants() {
        assertEquals((byte) 0x02, TransactionType.RSK_NAMESPACE_PREFIX);
        assertEquals((byte) 0x7f, TransactionType.MAX_TYPE_VALUE);
    }

    @Test
    void testReverseLookup() {
        assertEquals(TransactionType.LEGACY, TransactionType.getByByte((byte) 0x00));
        assertEquals(TransactionType.TYPE_1, TransactionType.getByByte((byte) 0x01));
        assertEquals(TransactionType.TYPE_2, TransactionType.getByByte((byte) 0x02));
        assertEquals(TransactionType.TYPE_3, TransactionType.getByByte((byte) 0x03));
        assertEquals(TransactionType.TYPE_4, TransactionType.getByByte((byte) 0x04));
        assertNull(TransactionType.getByByte((byte) 0x7f), "Unknown type should return null");
    }

    // ========================================================================
    // Instance methods: isLegacy(), isTyped()
    // ========================================================================

    @Test
    void testIsLegacy() {
        assertTrue(TransactionType.LEGACY.isLegacy());
        assertFalse(TransactionType.TYPE_1.isLegacy());
        assertFalse(TransactionType.TYPE_2.isLegacy());
        assertFalse(TransactionType.TYPE_3.isLegacy());
        assertFalse(TransactionType.TYPE_4.isLegacy());
    }

    @Test
    void testIsTyped() {
        assertFalse(TransactionType.LEGACY.isTyped());
        assertTrue(TransactionType.TYPE_1.isTyped());
        assertTrue(TransactionType.TYPE_2.isTyped());
        assertTrue(TransactionType.TYPE_3.isTyped());
        assertTrue(TransactionType.TYPE_4.isTyped());
    }

    // ========================================================================
    // Instance method: getTypeName()
    // ========================================================================

    @Test
    void testInstanceTypeNames() {
        assertEquals("Legacy", TransactionType.LEGACY.getTypeName());
        assertEquals("RSKIP546 (EIP-2930 Access List)", TransactionType.TYPE_1.getTypeName());
        assertEquals("RSKIP546 (EIP-1559 Dynamic Fee)", TransactionType.TYPE_2.getTypeName());
        assertEquals("RSKIP000 (Blob)", TransactionType.TYPE_3.getTypeName());
        assertEquals("RSKIP545 (EIP-7702 Set Code)", TransactionType.TYPE_4.getTypeName());
    }

    // ========================================================================
    // Static: isLegacyTransaction(byte)
    // ========================================================================

    @Test
    void testLegacyTransactionDetection() {
        // Per RSKIP543 / EIP-2718: legacy transactions are RLP lists,
        // so the first byte is >= 0xc0
        assertTrue(TransactionType.isLegacyTransaction((byte) 0xc0));
        assertTrue(TransactionType.isLegacyTransaction((byte) 0xf8));
        assertTrue(TransactionType.isLegacyTransaction((byte) 0xf9));
        assertTrue(TransactionType.isLegacyTransaction((byte) 0xfe));
        assertTrue(TransactionType.isLegacyTransaction((byte) 0xff));

        // Typed transactions have first byte in [0x00, 0x7f]
        assertFalse(TransactionType.isLegacyTransaction((byte) 0x00));
        assertFalse(TransactionType.isLegacyTransaction((byte) 0x01));
        assertFalse(TransactionType.isLegacyTransaction((byte) 0x7f));

        // Bytes in range [0x80, 0xbf] are RLP strings, not lists — not legacy either
        assertFalse(TransactionType.isLegacyTransaction((byte) 0x80));
        assertFalse(TransactionType.isLegacyTransaction((byte) 0xbf));
    }

    // ========================================================================
    // Static: isValidType(byte)
    // ========================================================================

    @Test
    void testValidTypeCheck() {
        // Valid EIP-2718 types: [0x00, 0x7f]
        assertTrue(TransactionType.isValidType((byte) 0x00));
        assertTrue(TransactionType.isValidType((byte) 0x01));
        assertTrue(TransactionType.isValidType((byte) 0x7f));

        // Invalid types: > 0x7f
        assertFalse(TransactionType.isValidType((byte) 0x80));
        assertFalse(TransactionType.isValidType((byte) 0xc0));
        assertFalse(TransactionType.isValidType((byte) 0xff));
    }

    // ========================================================================
    // Static: encodeRskType(byte), decodeRskType(int)
    // ========================================================================

    @Test
    void testRskSpecificTypeEncoding() {
        // Per RSKIP543: "if the type of a Rootstock specific transaction is 3,
        // then we can store the overall type as 0x0203"
        assertEquals(0x0203, TransactionType.encodeRskType((byte) 0x03));
        assertEquals(0x027f, TransactionType.encodeRskType((byte) 0x7f));
        assertEquals(0x0200, TransactionType.encodeRskType((byte) 0x00));
    }

    @Test
    void testRskSpecificTypeDecoding() {
        assertEquals((byte) 0x03, TransactionType.decodeRskType(0x0203));
        assertEquals((byte) 0x7f, TransactionType.decodeRskType(0x027f));
        assertEquals((byte) 0x00, TransactionType.decodeRskType(0x0200));
    }

    @Test
    void testInvalidRskTypeEncodingThrows() {
        // Subtype > 0x7f is out of range
        assertThrows(IllegalArgumentException.class, () ->
                TransactionType.encodeRskType((byte) 0x80));
    }

    @Test
    void testInvalidRskTypeDecodingThrows() {
        // Wrong high byte (0x01 instead of 0x02)
        assertThrows(IllegalArgumentException.class, () ->
                TransactionType.decodeRskType(0x0100));

        // Single-byte Ethereum type (not RSK-specific)
        assertThrows(IllegalArgumentException.class, () ->
                TransactionType.decodeRskType(0x04));
    }

    // ========================================================================
    // Static: isRskSpecificType(int)
    // ========================================================================

    @Test
    void testRskSpecificTypeDetection() {
        // RSK-specific types: high byte 0x02, low byte in [0x00, 0x7f]
        assertTrue(TransactionType.isRskSpecificType(0x0200));
        assertTrue(TransactionType.isRskSpecificType(0x0203));
        assertTrue(TransactionType.isRskSpecificType(0x027f));

        // Not RSK-specific
        assertFalse(TransactionType.isRskSpecificType(0x00));   // legacy
        assertFalse(TransactionType.isRskSpecificType(0x01));   // EIP-2930
        assertFalse(TransactionType.isRskSpecificType(0x04));   // EIP-7702
        assertFalse(TransactionType.isRskSpecificType(0x0100)); // wrong prefix
        assertFalse(TransactionType.isRskSpecificType(0x0280)); // low byte > 0x7f
    }

    // ========================================================================
    // Static: toReceiptString(int)
    // ========================================================================

    @Test
    void testReceiptStringFormatting() {
        assertEquals("0x0", TransactionType.toReceiptString(0x00));
        assertEquals("0x1", TransactionType.toReceiptString(0x01));
        assertEquals("0x4", TransactionType.toReceiptString(0x04));

        // RSK types
        assertEquals("0x203", TransactionType.toReceiptString(0x0203));
        assertEquals("0x27f", TransactionType.toReceiptString(0x027f));
    }

    // ========================================================================
    // Static: getTypeName(int)
    // ========================================================================

    @Test
    void testStaticGetTypeName() {
        // Standard Ethereum types (by byte code value)
        assertEquals("Legacy", TransactionType.getTypeName(0x00));
        assertEquals("RSKIP546 (EIP-2930 Access List)", TransactionType.getTypeName(0x01));
        assertEquals("RSKIP546 (EIP-1559 Dynamic Fee)", TransactionType.getTypeName(0x02));
        assertEquals("RSKIP000 (Blob)", TransactionType.getTypeName(0x03));
        assertEquals("RSKIP545 (EIP-7702 Set Code)", TransactionType.getTypeName(0x04));

        // RSK-specific type
        assertTrue(TransactionType.getTypeName(0x0203).contains("RSK Type"));
    }
}
