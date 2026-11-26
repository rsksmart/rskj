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
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Declarative description of a typed transaction envelope, validated in one call.
 *
 * <p>{@link #validate} reproduces the order the typed parsers have always checked in, so the first
 * failing check decides the error: field count, {@code to} address, byte-string framing, each list
 * field's codec in envelope order, the caller's cross-field checks, data-word size of every scalar,
 * canonical encoding of every scalar, then the signature fields together. Scalars are checked in
 * {@code checkRank} order, which need not match envelope order.
 *
 * <p>Every schema declares exactly one each of {@link #chainId()}, {@link #yParity()},
 * {@link #signatureR()} and {@link #signatureS()}, and every {@link #list} field carries the codec
 * that checks it, so no field can be declared without its checks. A list field is excluded from
 * byte-string framing; its codec checks its framing. Nonce and gas limit are measured on their raw
 * length, while value and fee fields are measured on {@link co.rsk.core.Coin#getBytes()}, which is
 * signed two's complement, so the sign byte counts. A leading-zero 33-byte coin therefore passes the
 * size check and fails the canonical one, and a canonical 32-byte coin with the top bit set fails
 * the size check.
 */
public final class TypedEnvelopeSchema {

    private final String typeName;
    private final Field[] fields;
    private final int[] scalarIndicesByRank;
    private final int chainIdIndex;
    private final int yParityIndex;
    private final int rIndex;
    private final int sIndex;

    private TypedEnvelopeSchema(String typeName, Field[] fields) {
        this.typeName = typeName;
        this.fields = fields;
        this.scalarIndicesByRank = IntStream.range(0, fields.length)
                .filter(i -> fields[i].kind == Kind.SCALAR || fields[i].kind == Kind.COIN_SCALAR)
                .boxed()
                .sorted(Comparator.comparingInt(i -> fields[i].checkRank))
                .mapToInt(Integer::intValue)
                .toArray();
        this.chainIdIndex = soleIndexOf(fields, Kind.CHAIN_ID);
        this.yParityIndex = soleIndexOf(fields, Kind.Y_PARITY);
        this.rIndex = soleIndexOf(fields, Kind.SIGNATURE_R);
        this.sIndex = soleIndexOf(fields, Kind.SIGNATURE_S);
    }

    private static int soleIndexOf(Field[] fields, Kind kind) {
        int[] indices = IntStream.range(0, fields.length).filter(i -> fields[i].kind == kind).toArray();
        if (indices.length != 1) {
            throw new IllegalArgumentException(
                    "A typed envelope schema must declare exactly one " + kind + " field, got " + indices.length);
        }
        return indices[0];
    }

    /** Expected field count is {@code fields.length}; fields are listed in envelope index order. */
    public static TypedEnvelopeSchema of(String typeName, Field... fields) {
        return new TypedEnvelopeSchema(typeName, fields.clone());
    }

    /** A byte-string field with no further checks. */
    public static Field bytes() {
        return new Field(Kind.BYTES, null, 0, null, null);
    }

    /**
     * A list-valued field, excluded from byte-string framing and checked by {@code codec}, which
     * receives the raw element and returns the decoded value; read it back with
     * {@link ValidatedEnvelope#get}.
     */
    public static <V> ListField<V> list(Function<RLPElement, V> codec) {
        return new ListField<>(codec);
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

    /** The chainId field, checked with the other signature fields by {@link TypedTransactionCodec}. */
    public static Field chainId() {
        return new Field(Kind.CHAIN_ID, null, 0, null, null);
    }

    /** The yParity field, checked with the other signature fields by {@link TypedTransactionCodec}. */
    public static Field yParity() {
        return new Field(Kind.Y_PARITY, null, 0, null, null);
    }

    /** The signature r field, checked with the other signature fields by {@link TypedTransactionCodec}. */
    public static Field signatureR() {
        return new Field(Kind.SIGNATURE_R, null, 0, null, null);
    }

    /** The signature s field, checked with the other signature fields by {@link TypedTransactionCodec}. */
    public static Field signatureS() {
        return new Field(Kind.SIGNATURE_S, null, 0, null, null);
    }

    /** Validates an envelope that has no cross-field checks. */
    public ValidatedEnvelope validate(RLPList txFields) {
        return validate(txFields, fields -> { });
    }

    /**
     * Runs the envelope checks around {@code crossFieldChecks}, which runs after the list codecs and
     * before the scalar checks, and returns the list values and the signature state.
     */
    public ValidatedEnvelope validate(RLPList txFields, Consumer<RLPList> crossFieldChecks) {
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
        Object[] listValues = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] instanceof ListField<?> listField) {
                listValues[i] = listField.codec.apply(txFields.get(i));
            }
        }
        crossFieldChecks.accept(txFields);
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
        SignatureState signature = TypedTransactionCodec.parseTypedSignatureState(
                txFields, chainIdIndex, yParityIndex, rIndex, sIndex);
        return new ValidatedEnvelope(fields, listValues, signature);
    }

    private enum Kind { BYTES, LIST, ADDRESS, SCALAR, COIN_SCALAR, CHAIN_ID, Y_PARITY, SIGNATURE_R, SIGNATURE_S }

    /** One envelope slot; build with the static factories of {@link TypedEnvelopeSchema}. */
    public static class Field {
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

    /** A list-valued slot and the codec that checks and decodes it; build with {@link #list}. */
    public static final class ListField<V> extends Field {
        private final Function<RLPElement, V> codec;

        private ListField(Function<RLPElement, V> codec) {
            super(Kind.LIST, null, 0, null, null);
            this.codec = codec;
        }
    }

    /** The outcome of a successful {@link #validate}: the decoded list values and the signature state. */
    public static final class ValidatedEnvelope {
        private final Field[] fields;
        private final Object[] listValues;
        private final SignatureState signature;

        private ValidatedEnvelope(Field[] fields, Object[] listValues, SignatureState signature) {
            this.fields = fields;
            this.listValues = listValues;
            this.signature = signature;
        }

        /** The value {@code field}'s codec returned; {@code field} must be declared in this schema. */
        @SuppressWarnings("unchecked")
        public <V> V get(ListField<V> field) {
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == field) {
                    return (V) listValues[i];
                }
            }
            throw new IllegalArgumentException("List field is not declared in this schema");
        }

        public SignatureState signature() {
            return signature;
        }
    }
}
