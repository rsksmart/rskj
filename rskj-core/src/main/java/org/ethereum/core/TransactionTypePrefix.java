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

import java.util.Arrays;
import java.util.Objects;

/**
 * Type-safe representation of transaction/receipt prefixes:
 * legacy (no prefix), standard typed (1 byte), and RSK namespace (2 bytes).
 */
public sealed interface TransactionTypePrefix
        permits TransactionTypePrefix.Legacy,
                TransactionTypePrefix.StandardTyped,
                TransactionTypePrefix.RskNamespace {

    TransactionTypePrefix LEGACY_INSTANCE = new Legacy();
    TransactionType type();
    boolean isLegacy();
    boolean isTyped();
    boolean isRskNamespace();
    byte[] toBytes();
    int length();
    String toFullString();
    String toRpcString();
    static TransactionTypePrefix legacy() {
        return LEGACY_INSTANCE;
    }

    /**
     * Returns a standard typed prefix for the given type.
     * Passing {@code null} or {@code LEGACY} returns the legacy singleton.
     */
    static TransactionTypePrefix typed(TransactionType type) {
        if (type == null || type == TransactionType.LEGACY) {
            return LEGACY_INSTANCE;
        }
        return new StandardTyped(type);
    }

    static TransactionTypePrefix rskNamespace(byte subtype) {
        return new RskNamespace(subtype);
    }

    /**
     * Selects the prefix variant from transaction type and optional RSK subtype.
     * @throws IllegalArgumentException if rskSubtype is non-null but type is not TYPE_2
     */
    static TransactionTypePrefix of(TransactionType type, Byte rskSubtype) {
        if (rskSubtype != null) {
            if (type != TransactionType.TYPE_2) {
                throw new IllegalArgumentException(
                        "RSK subtype can only be used with TYPE_2, got: " + type);
            }
            return rskNamespace(rskSubtype);
        }
        return typed(type);
    }

    /** Detects the prefix from raw encoded transaction/receipt bytes. */
    static TransactionTypePrefix fromRawData(byte[] rawData) {
        Objects.requireNonNull(rawData, "rawData must not be null");
        if (rawData.length == 0) {
            throw new IllegalArgumentException("rawData must not be empty");
        }

        byte firstByte = rawData[0];

        if ((firstByte & 0xFF) > TransactionType.MAX_TYPE_VALUE) {
            if (TransactionType.isReservedByte(firstByte)) {
                throw new IllegalArgumentException(
                        "Transaction type 0xff is reserved per EIP-2718 and cannot be used");
            }
            return LEGACY_INSTANCE;
        }

        return decodeTypedPrefix(rawData, firstByte);
    }

    /**
     * Decodes a typed transaction prefix by looking up the enum and dispatching.
     * Type 0x00 (LEGACY) and unknown bytes are rejected as unsupported.
     */
    private static TransactionTypePrefix decodeTypedPrefix(byte[] rawData, byte typeByte) {
        TransactionType type = TransactionType.getByByte(typeByte);
        if (type == null || type == TransactionType.LEGACY) {
            throw new IllegalArgumentException(String.format(
                    "transaction type not supported: 0x%02x", typeByte & 0xFF));
        }
        return switch (type) {
            case TYPE_1, TYPE_3, TYPE_4 -> new StandardTyped(type);
            case TYPE_2 -> {
                if (TransactionType.hasRskNamespacePrefix(rawData)) {
                    yield new RskNamespace(rawData[1]);
                }
                yield new StandardTyped(type);
            }
            default -> throw new IllegalArgumentException(String.format(
                    "transaction type not supported: 0x%02x", typeByte & 0xFF));
        };
    }

    /** Returns only the RLP payload after removing the detected prefix. */
    static byte[] stripPrefix(byte[] rawData) {
        TransactionTypePrefix prefix = fromRawData(rawData);
        if (prefix.length() == 0) {
            return rawData;
        }
        return Arrays.copyOfRange(rawData, prefix.length(), rawData.length);
    }

    /** Legacy transaction: no prefix bytes. */
    record Legacy() implements TransactionTypePrefix {
        @Override public TransactionType type()     { return TransactionType.LEGACY; }
        @Override public boolean isLegacy()         { return true;  }
        @Override public boolean isTyped()          { return false; }
        @Override public boolean isRskNamespace()   { return false; }
        @Override public byte[] toBytes()           { return new byte[0]; }
        @Override public int length()               { return 0; }
        @Override public String toFullString()      { return "0x00"; }
        @Override public String toRpcString()       { return "0x0"; }

        @Override public String toString() {
            return "Legacy";
        }
    }

    /** Standard typed transaction: {@code type || rlpPayload}. */
    record StandardTyped(TransactionType type) implements TransactionTypePrefix {
        public StandardTyped {
            Objects.requireNonNull(type, "type must not be null");
            if (type == TransactionType.LEGACY) {
                throw new IllegalArgumentException(
                        "Use TransactionTypePrefix.legacy() for legacy transactions");
            }
        }

        @Override public boolean isLegacy()       { return false; }
        @Override public boolean isTyped()        { return true;  }
        @Override public boolean isRskNamespace() { return false; }
        @Override public byte[] toBytes()         { return new byte[]{ type.getByteCode() }; }
        @Override public int length()             { return 1; }

        @Override
        public String toFullString() {
            return String.format("0x%02x", type.getByteCode());
        }

        @Override
        public String toRpcString() {
            return "0x" + Long.toHexString(type.getByteCode() & 0xFF);
        }

        @Override
        public String toString() {
            return "StandardTyped[" + type.getTypeName() + "]";
        }
    }

    /** RSK namespace transaction: {@code 0x02 || subtype || rlpPayload}. */
    record RskNamespace(byte subtype) implements TransactionTypePrefix {
        public RskNamespace {
            if ((subtype & 0xFF) > TransactionType.MAX_TYPE_VALUE) {
                throw new IllegalArgumentException(
                        "RSK subtype must be in [0x00, 0x7f], got: 0x"
                                + String.format("%02x", subtype & 0xFF));
            }
        }

        @Override public TransactionType type()   { return TransactionType.TYPE_2; }
        @Override public boolean isLegacy()       { return false; }
        @Override public boolean isTyped()        { return true;  }
        @Override public boolean isRskNamespace() { return true;  }

        @Override
        public byte[] toBytes() {
            return new byte[]{ TransactionType.RSK_NAMESPACE_PREFIX, subtype };
        }

        @Override public int length() { return 2; }

        @Override
        public String toFullString() {
            return String.format("0x%02x%02x", TransactionType.RSK_NAMESPACE_PREFIX, subtype);
        }

        @Override
        public String toRpcString() {
            return toFullString();
        }

        @Override
        public String toString() {
            return String.format("RskNamespace[subtype=0x%02x]", subtype);
        }
    }
}
