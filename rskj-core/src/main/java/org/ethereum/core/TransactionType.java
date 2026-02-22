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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** Transaction types and helpers for RSKIP543 prefix handling. */
public enum TransactionType {

    LEGACY((byte) 0, "Legacy"),
    TYPE_1((byte) 1, "RSKIP546 (EIP-2930 Access List)"),
    TYPE_2((byte) 2, "RSKIP546 (EIP-1559 Dynamic Fee)"),
    TYPE_3((byte) 3, "RSKIP000 (Blob)"),
    TYPE_4((byte) 4, "RSKIP545 (EIP-7702 Set Code)");

    public static final byte RSK_NAMESPACE_PREFIX = 0x02;
    public static final byte MAX_TYPE_VALUE = 0x7f;
    private static final int RLP_LIST_START = 0xc0;

    private static final Map<Byte, TransactionType> lookup = new HashMap<>();

    static {
        for (TransactionType t : EnumSet.allOf(TransactionType.class)) {
            lookup.put(t.byteCode, t);
        }
    }

    private final byte byteCode;
    private final String typeName;

    TransactionType(byte b, String name) {
        this.byteCode = b;
        this.typeName = name;
    }

    public byte getByteCode() {
        return this.byteCode;
    }

    public boolean isLegacy() {
        return this == LEGACY;
    }

    public boolean isTyped() {
        return !isLegacy();
    }

    public String getTypeName() {
        return typeName;
    }

    public static TransactionType getByByte(byte type) {
        return lookup.get(type);
    }

    /**
     * Returns {@code true} if the byte can be a typed-transaction marker.
     */
    public static boolean isTypedTransactionByte(byte b) {
        return (b & 0xFF) <= MAX_TYPE_VALUE;
    }

    public static boolean isLegacyTransaction(byte firstByte) {
        return (firstByte & 0xFF) >= RLP_LIST_START;
    }

    public static boolean isValidType(byte type) {
        return (type & 0xFF) <= MAX_TYPE_VALUE;
    }

    /**
     * Detects whether raw bytes start with the RSK namespace prefix
     * ({@code 0x02 || subtype ≤ 0x7f}).
     *
     * @param rawData the raw encoded transaction/receipt bytes (must be non-null, length ≥ 2)
     * @return {@code true} if this is an RSK namespace encoding
     */
    public static boolean hasRskNamespacePrefix(byte[] rawData) {
        return rawData.length > 1
                && rawData[0] == RSK_NAMESPACE_PREFIX
                && (rawData[1] & 0xFF) <= MAX_TYPE_VALUE;
    }

    public static int typePrefixLength(byte[] rawData) {
        return TransactionTypePrefix.fromRawData(rawData).length();
    }

    public static byte[] stripTypePrefix(byte[] rawData) {
        return TransactionTypePrefix.stripPrefix(rawData);
    }

    public static byte[] buildTypePrefix(TransactionType type, Byte rskSubtype) {
        return TransactionTypePrefix.of(type, rskSubtype).toBytes();
    }

    /**
     * Encode an RSK-specific subtype into its two-byte combined int value
     * ({@code 0x02 << 8 | rskSubtype}, e.g. subtype 0x03 → 0x0203).
     */
    public static int encodeRskType(byte rskSubtype) {
        if ((rskSubtype & 0xFF) > MAX_TYPE_VALUE) {
            throw new IllegalArgumentException(
                    "RSK subtype must be in range [0x00, 0x7f], got: 0x"
                            + String.format("%02x", rskSubtype & 0xFF));
        }
        return (RSK_NAMESPACE_PREFIX << 8) | (rskSubtype & 0xFF);
    }

    /**
     * Decode a two-byte RSK-specific type value into the subtype byte
     * (e.g. 0x0203 → 0x03).
     */
    public static byte decodeRskType(int encodedType) {
        if (!isRskSpecificType(encodedType)) {
            throw new IllegalArgumentException(
                    "Not an RSK-specific type: 0x" + String.format("%04x", encodedType));
        }
        return (byte) (encodedType & 0xFF);
    }

    /**
     * Returns {@code true} if the integer represents an RSK-specific type
     * (high byte == 0x02, low byte in [0x00, 0x7f]).
     */
    public static boolean isRskSpecificType(int encodedType) {
        int highByte = (encodedType >> 8) & 0xFF;
        int lowByte = encodedType & 0xFF;
        return highByte == RSK_NAMESPACE_PREFIX && lowByte <= MAX_TYPE_VALUE;
    }

    public static String toReceiptString(int typeValue) {
        return "0x" + Integer.toHexString(typeValue);
    }

    public static String getTypeName(int typeValue) {
        if (isRskSpecificType(typeValue)) {
            byte subtype = decodeRskType(typeValue);
            return "RSK Type 0x" + String.format("%02x", subtype & 0xFF);
        }
        TransactionType type = lookup.get((byte) typeValue);
        if (type != null) {
            return type.getTypeName();
        }
        return "Unknown (0x" + String.format("%02x", typeValue) + ")";
    }
}
