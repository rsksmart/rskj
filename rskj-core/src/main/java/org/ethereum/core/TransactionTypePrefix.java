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

import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import co.rsk.util.HexUtils;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Type-safe representation of transaction/receipt prefixes:
 * legacy (no prefix), standard typed (1 byte), and RSK namespace (2 bytes).
 */
public sealed interface TransactionTypePrefix
        permits LegacyPrefix, StandardTypedPrefix, RskNamespacePrefix {

    TransactionTypePrefix LEGACY_INSTANCE = new LegacyPrefix();

    String ERR_INVALID_TX_TYPE = "Invalid transaction type: ";
    String ERR_INVALID_RSK_SUBTYPE = "Invalid RSK subtype: ";

    TransactionType type();
    boolean isLegacy();
    boolean isTyped();
    boolean isRskNamespace();

    default byte subtype() {
        throw new UnsupportedOperationException("subtype is only available for RSK namespace transactions");
    }

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
        return new StandardTypedPrefix(type);
    }

    static TransactionTypePrefix rskNamespace(byte subtype) {
        return new RskNamespacePrefix(subtype);
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
        TransactionType type = TransactionType.fromByte(typeByte);
        if (type == null || type == TransactionType.LEGACY) {
            throw new IllegalArgumentException(String.format(
                    "transaction type not supported: 0x%02x", typeByte & 0xFF));
        }
        return switch (type) {
            case TYPE_1, TYPE_3, TYPE_4 -> new StandardTypedPrefix(type);
            case TYPE_2 -> {
                if (TransactionType.hasRskNamespacePrefix(rawData)) {
                    yield new RskNamespacePrefix(rawData[1]);
                }
                yield new StandardTypedPrefix(type);
            }
            default -> throw new IllegalArgumentException(String.format(
                    "transaction type not supported: 0x%02x", typeByte & 0xFF));
        };
    }

    /** Returns the RLP payload after removing the given prefix. */
    static BytesSlice stripPrefix(byte[] rawData, TransactionTypePrefix prefix) {
        BytesSlice slice = Bytes.of(rawData);
        int len = prefix.length();
        return len == 0 ? slice : slice.slice(len, rawData.length);
    }


     static TransactionTypePrefix fromHex(String type, String rskSubType) {
         if (type == null) {
             if (rskSubType != null) {
                 throw RskJsonRpcRequestException.invalidParamError(
                         "RSK subtype cannot be provided without a transaction type");
             }
             return TransactionTypePrefix.legacy(); // or  TransactionTypePrefix.LEGACY_INSTANCE;
         }

        byte typeByte;
        try {
            BigInteger value = HexUtils.strHexOrStrNumberToBigInteger(type);
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(TransactionType.MAX_TYPE_VALUE)) > 0) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + type);
            }
            typeByte = value.byteValue();
        } catch (RskJsonRpcRequestException e) {
            throw e;
        } catch (Exception ex) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + type, ex);
        }

         TransactionType transactionType = TransactionType.fromByte(typeByte);
         if (transactionType == null) {
             throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + type);
         }
         if (transactionType == TransactionType.LEGACY) {
             throw RskJsonRpcRequestException.invalidParamError(
                     ERR_INVALID_TX_TYPE + type +
                             "; explicit type 0x00 is not allowed, omit the type field for legacy transactions");
         }
         Byte rskSubtype = hexToRskSubtype(rskSubType);
         if (rskSubtype != null && transactionType != TransactionType.TYPE_2) {
             throw RskJsonRpcRequestException.invalidParamError(
                     "rskSubtype can only be used with type 0x02 (RSK namespace), got type: " + type);
         }

         try {
             return TransactionTypePrefix.of(transactionType, rskSubtype);
         } catch (IllegalArgumentException ex) {
             throw RskJsonRpcRequestException.invalidParamError(ex.getMessage(), ex);
         }
    }

    static Byte hexToRskSubtype(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            BigInteger value = HexUtils.strHexOrStrNumberToBigInteger(hex);
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(TransactionType.MAX_TYPE_VALUE)) > 0) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_RSK_SUBTYPE + hex);
            }
            return value.byteValue();
        } catch (RskJsonRpcRequestException e) {
            throw e;
        } catch (Exception ex) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_RSK_SUBTYPE + hex, ex);
        }
    }
}
