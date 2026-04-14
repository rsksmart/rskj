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
    public static final byte RESERVED_BYTE = (byte) 0xff;

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

    public static TransactionType fromByte(byte type) {
        return lookup.get(type);
    }

    public static boolean isReservedByte(byte firstByte) {
        return firstByte == RESERVED_BYTE;
    }

    public static boolean hasRskNamespacePrefix(byte[] rawData) {
        return rawData.length > 1
                && rawData[0] == RSK_NAMESPACE_PREFIX
                && (rawData[1] & 0xFF) <= MAX_TYPE_VALUE;
    }
}
