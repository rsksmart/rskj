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

import co.rsk.core.exception.InvalidRskAddressException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypedEnvelopeSchemaTest {

    private static final byte[] EMPTY_LIST = RLP.encodeList();

    private static RLPList envelope(byte[]... encodedItems) {
        return (RLPList) RLP.decode2(RLP.encodeList(encodedItems)).get(0);
    }

    private static byte[] item(int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        return RLP.encodeElement(data);
    }

    @Test
    void validate_wrongFieldCount_isRejected() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Type-9 transaction must have exactly 2 elements", e.getMessage());
    }

    @Test
    void validate_listWhereBytesExpected_isRejectedWithFieldIndex() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(1), EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Transaction field at index 1 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_byteStringWhereListExpected_passesFraming() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.list());

        String result = schema.validate(envelope(item(1), item(2, 3)), fields -> "reached");

        assertEquals("reached", result);
    }

    @Test
    void validate_listWhereListExpected_isSkippedByByteStringFraming() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.list());

        String result = schema.validate(envelope(item(1), EMPTY_LIST), fields -> "reached");

        assertEquals("reached", result);
    }

    @Test
    void validate_addressOfWrongLength_isRejected() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());
        byte[] nineteenBytes = new byte[19];
        RLPList txFields = envelope(RLP.encodeElement(nineteenBytes), item(1));

        InvalidRskAddressException e = assertThrows(InvalidRskAddressException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("An RSK address must be 20 bytes long", e.getMessage());
    }

    @Test
    void validate_addressCheckRunsBeforeFraming() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());
        byte[] nineteenBytes = new byte[19];
        RLPList txFields = envelope(RLP.encodeElement(nineteenBytes), EMPTY_LIST);

        InvalidRskAddressException e = assertThrows(InvalidRskAddressException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("An RSK address must be 20 bytes long", e.getMessage());
    }

    @Test
    void validate_nonNullAddressOnEmpty_isRejectedBeforeFraming() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.nonNullAddress("Type-9 needs a destination"), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(), EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Type-9 needs a destination", e.getMessage());
    }

    @Test
    void validate_plainAddressOnEmpty_isAccepted() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());

        String result = schema.validate(envelope(item(), item(1)), fields -> "reached");

        assertEquals("reached", result);
    }

    @Test
    void validate_rawScalarWiderThanDataWord_isRejected() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        byte[] thirtyThreeBytes = nonZeroBytes(33);
        RLPList txFields = envelope(RLP.encodeElement(thirtyThreeBytes));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Nonce is not valid", e.getMessage());
    }

    @Test
    void validate_rawScalarOfExactlyDataWord_isAccepted() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));

        String result = schema.validate(envelope(RLP.encodeElement(nonZeroBytes(32))), fields -> "reached");

        assertEquals("reached", result);
    }

    @Test
    void validate_coinScalarWiderThanDataWord_isRejected() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.coinScalar(0, "Value is not valid", "Value"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Value is not valid", e.getMessage());
    }

    @Test
    void validate_leadingZeroCoinScalarOfThirtyThreeBytes_passesDataWordAndFailsCanonical() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.coinScalar(0, "Value is not valid", "Value"));
        byte[] leadingZero = nonZeroBytes(33);
        leadingZero[0] = 0;
        RLPList txFields = envelope(RLP.encodeElement(leadingZero));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Value must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_rawScalarWithLeadingZero_isRejectedAsNonCanonical() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        RLPList txFields = envelope(item(0, 5));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Nonce must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_dataWordChecksOfAllScalarsRunBeforeAnyCanonicalCheck() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"),
                TypedEnvelopeSchema.scalar(1, "Gas Limit is not valid", "Gas Limit"));
        RLPList txFields = envelope(item(0, 5), RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("Gas Limit is not valid", e.getMessage());
    }

    @Test
    void validate_semanticPhaseRunsBeforeScalarChecks() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> schema.validate(txFields, fields -> {
                    throw new IllegalStateException("semantic phase ran");
                }));

        assertEquals("semantic phase ran", e.getMessage());
    }

    @Test
    void validate_semanticPhaseRunsAfterFraming() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> {
                    throw new IllegalStateException("semantic phase ran");
                }));

        assertEquals("Transaction field at index 0 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_dataWordChecksFollowCheckRankNotEnvelopeIndex() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(1, "Second is not valid", "Second"),
                TypedEnvelopeSchema.coinScalar(0, "First is not valid", "First"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)), RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("First is not valid", e.getMessage());
    }

    @Test
    void validate_canonicalChecksFollowCheckRankNotEnvelopeIndex() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.scalar(1, "Second is not valid", "Second"),
                TypedEnvelopeSchema.coinScalar(0, "First is not valid", "First"));
        RLPList txFields = envelope(item(0, 5), item(0, 5));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> null));

        assertEquals("First must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_returnsWhatTheSemanticPhaseReturned() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));

        byte[] result = schema.validate(envelope(item(7), item(9)), fields -> fields.get(0).getRLPData());

        assertArrayEquals(new byte[]{7}, result);
    }

    private static byte[] nonZeroBytes(int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte) 1);
        return data;
    }
}
