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

import co.rsk.core.RskAddress;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Declarative description of a typed transaction envelope, validated in one call.
 *
 * <p>{@link #validate} reproduces the order the typed parsers have always checked in, so the first
 * failing check decides the error: field count, {@code to} address, byte-string framing, the
 * caller's semantic phase, data-word size of every scalar, then canonical encoding of every scalar.
 * Scalars are checked in {@code checkRank} order, which need not match envelope order.
 *
 * <p>A {@link #list()} field is only excluded from byte-string framing; the codec that owns its
 * contents checks its framing in the semantic phase. Nonce and gas limit are measured on their raw
 * length, while value and fee fields are measured on {@link co.rsk.core.Coin#getBytes()}, which is
 * signed two's complement, so the sign byte counts. A leading-zero 33-byte coin therefore passes the
 * size check and fails the canonical one, and a canonical 32-byte coin with the top bit set fails
 * the size check.
 */
public final class TypedEnvelopeSchema {

    private final String typeName;
    private final Field[] fields;
    private final int[] scalarIndicesByRank;

    private TypedEnvelopeSchema(String typeName, Field[] fields) {
        this.typeName = typeName;
        this.fields = fields;
        this.scalarIndicesByRank = IntStream.range(0, fields.length)
                .filter(i -> fields[i].kind == Kind.SCALAR || fields[i].kind == Kind.COIN_SCALAR)
                .boxed()
                .sorted(Comparator.comparingInt(i -> fields[i].checkRank))
                .mapToInt(Integer::intValue)
                .toArray();
    }

    /** Expected field count is {@code fields.length}; fields are listed in envelope index order. */
    public static TypedEnvelopeSchema of(String typeName, Field... fields) {
        return new TypedEnvelopeSchema(typeName, fields.clone());
    }

    /** A byte-string field with no further checks. */
    public static Field bytes() {
        return new Field(Kind.BYTES, null, 0, null, null);
    }

    /** A list-valued field, excluded from byte-string framing. */
    public static Field list() {
        return new Field(Kind.LIST, null, 0, null, null);
    }

    /** The {@code to} field: a non-empty payload must be a valid address, empty means contract creation. */
    public static Field address() {
        return new Field(Kind.ADDRESS, null, 0, null, null);
    }

    /** Like {@link #address()}, but an empty address is rejected with {@code emptyMessage}. */
    public static Field nonNullAddress(String emptyMessage) {
        return new Field(Kind.ADDRESS, emptyMessage, 0, null, null);
    }

    /** A scalar measured on its raw byte length (nonce, gas limit). */
    public static Field scalar(int checkRank, String dataWordMessage, String canonicalLabel) {
        return new Field(Kind.SCALAR, null, checkRank, dataWordMessage, canonicalLabel);
    }

    /** A scalar measured on {@link co.rsk.core.Coin#getBytes()}, signed two's complement so the sign byte counts (value, fees). */
    public static Field coinScalar(int checkRank, String dataWordMessage, String canonicalLabel) {
        return new Field(Kind.COIN_SCALAR, null, checkRank, dataWordMessage, canonicalLabel);
    }

    /**
     * Runs the envelope checks around {@code semanticPhase}, which runs after framing and before the
     * scalar checks, and returns its result.
     */
    public <T> T validate(RLPList txFields, Function<RLPList, T> semanticPhase) {
        CommonParsingUtils.requireFieldCount(txFields, fields.length, typeName);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].kind == Kind.ADDRESS) {
                RskAddress address = RLP.parseRskAddress(txFields.get(i).getRLPData());
                if (fields[i].emptyAddressMessage != null && address.equals(RskAddress.nullAddress())) {
                    throw new IllegalArgumentException(fields[i].emptyAddressMessage);
                }
            }
        }
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].kind != Kind.LIST) {
                CommonParsingUtils.requireByteStringFramed(txFields.get(i), "Transaction field at index " + i);
            }
        }
        T result = semanticPhase.apply(txFields);
        for (int index : scalarIndicesByRank) {
            Field field = fields[index];
            if (field.kind == Kind.SCALAR) {
                CommonParsingUtils.requireDataWordBytes(
                        CommonParsingUtils.nullToEmpty(txFields.get(index).getRLPData()), field.dataWordMessage);
            } else {
                CommonParsingUtils.requireDataWordCoin(
                        RLP.parseCoinNullZero(txFields.get(index).getRLPData()), field.dataWordMessage);
            }
        }
        for (int index : scalarIndicesByRank) {
            CommonParsingUtils.requireCanonicalScalar(txFields.get(index).getRLPData(), fields[index].canonicalLabel);
        }
        return result;
    }

    private enum Kind { BYTES, LIST, ADDRESS, SCALAR, COIN_SCALAR }

    /** One envelope slot; build with the static factories of {@link TypedEnvelopeSchema}. */
    public static final class Field {
        private final Kind kind;
        private final String emptyAddressMessage;
        private final int checkRank;
        private final String dataWordMessage;
        private final String canonicalLabel;

        private Field(Kind kind, String emptyAddressMessage, int checkRank, String dataWordMessage, String canonicalLabel) {
            this.kind = kind;
            this.emptyAddressMessage = emptyAddressMessage;
            this.checkRank = checkRank;
            this.dataWordMessage = dataWordMessage;
            this.canonicalLabel = canonicalLabel;
        }
    }
}
