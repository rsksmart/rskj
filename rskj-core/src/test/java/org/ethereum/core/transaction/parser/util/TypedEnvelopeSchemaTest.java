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
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.Field;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.ListField;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypedEnvelopeSchemaTest {

    private static final byte[] EMPTY_LIST = RLP.encodeList();

    /** Appends the four signature fields every schema must declare. */
    private static TypedEnvelopeSchema schema(Field... body) {
        Field[] signature = {
                TypedEnvelopeSchema.chainId(), TypedEnvelopeSchema.yParity(),
                TypedEnvelopeSchema.signatureR(), TypedEnvelopeSchema.signatureS()};
        return TypedEnvelopeSchema.of("Type-9", Stream.concat(Arrays.stream(body), Arrays.stream(signature))
                .toArray(Field[]::new));
    }

    /** Appends an unsigned signature block for chain 1: chainId 0x01, then empty yParity, r and s. */
    private static RLPList envelope(byte[]... body) {
        return envelopeWithSignature(body, item(1), item(), item(), item());
    }

    private static RLPList envelopeWithSignature(byte[][] body, byte[]... signature) {
        byte[][] items = Stream.concat(Arrays.stream(body), Arrays.stream(signature)).toArray(byte[][]::new);
        return (RLPList) RLP.decode2(RLP.encodeList(items)).get(0);
    }

    private static byte[] item(int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        return RLP.encodeElement(data);
    }

    private static ListField<String> failingList(String message) {
        return TypedEnvelopeSchema.list(element -> {
            throw new IllegalStateException(message);
        });
    }

    @Test
    void validate_wrongFieldCount_isRejected() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Type-9 transaction must have exactly 6 elements", e.getMessage());
    }

    @Test
    void validate_listWhereBytesExpected_isRejectedWithFieldIndex() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes(), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(1), EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Transaction field at index 1 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_listWhereSignatureFieldExpected_isRejectedWithFieldIndex() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes());
        RLPList txFields = envelopeWithSignature(new byte[][]{item(1)}, item(1), item(), EMPTY_LIST, item());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Transaction field at index 3 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_byteStringWhereListExpected_isLeftToTheListCodec() {
        ListField<String> list = TypedEnvelopeSchema.list(element -> element instanceof RLPList ? "list" : "byte string");
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes(), list);

        String decoded = schema.validate(envelope(item(1), item(2, 3))).get(list);

        assertEquals("byte string", decoded);
    }

    @Test
    void validate_listWhereListExpected_reachesTheListCodec() {
        ListField<String> list = TypedEnvelopeSchema.list(element -> element instanceof RLPList ? "list" : "byte string");
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes(), list);

        String decoded = schema.validate(envelope(item(1), EMPTY_LIST)).get(list);

        assertEquals("list", decoded);
    }

    @Test
    void validate_listCodecRunsAfterFraming() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes(), failingList("list codec ran"));
        RLPList txFields = envelope(EMPTY_LIST, EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Transaction field at index 0 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_listCodecsRunInEnvelopeOrder() {
        TypedEnvelopeSchema schema = schema(failingList("first list"), failingList("second list"));
        RLPList txFields = envelope(EMPTY_LIST, EMPTY_LIST);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> schema.validate(txFields));

        assertEquals("first list", e.getMessage());
    }

    @Test
    void validate_listCodecRunsBeforeCrossFieldChecks() {
        TypedEnvelopeSchema schema = schema(failingList("list codec ran"));
        RLPList txFields = envelope(EMPTY_LIST);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> schema.validate(txFields, fields -> {
                    throw new IllegalStateException("cross-field checks ran");
                }));

        assertEquals("list codec ran", e.getMessage());
    }

    @Test
    void validate_addressOfWrongLength_isRejected() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());
        byte[] nineteenBytes = new byte[19];
        RLPList txFields = envelope(RLP.encodeElement(nineteenBytes), item(1));

        InvalidRskAddressException e = assertThrows(InvalidRskAddressException.class, () -> schema.validate(txFields));

        assertEquals("An RSK address must be 20 bytes long", e.getMessage());
    }

    @Test
    void validate_addressCheckRunsBeforeFraming() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());
        byte[] nineteenBytes = new byte[19];
        RLPList txFields = envelope(RLP.encodeElement(nineteenBytes), EMPTY_LIST);

        InvalidRskAddressException e = assertThrows(InvalidRskAddressException.class, () -> schema.validate(txFields));

        assertEquals("An RSK address must be 20 bytes long", e.getMessage());
    }

    @Test
    void validate_nonNullAddressOnEmpty_isRejectedBeforeFraming() {
        TypedEnvelopeSchema schema = schema(
                TypedEnvelopeSchema.nonNullAddress("Type-9 needs a destination"), TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(item(), EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Type-9 needs a destination", e.getMessage());
    }

    @Test
    void validate_plainAddressOnEmpty_isAccepted() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.address(), TypedEnvelopeSchema.bytes());

        TypedEnvelopeSchema.ValidatedEnvelope validated = schema.validate(envelope(item(), item(1)));

        assertEquals(new UnsignedSignature((byte) 1), validated.signature());
    }

    @Test
    void validate_rawScalarWiderThanDataWord_isRejected() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        byte[] thirtyThreeBytes = nonZeroBytes(33);
        RLPList txFields = envelope(RLP.encodeElement(thirtyThreeBytes));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Nonce is not valid", e.getMessage());
    }

    @Test
    void validate_rawScalarOfExactlyDataWord_isAccepted() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));

        TypedEnvelopeSchema.ValidatedEnvelope validated = schema.validate(envelope(RLP.encodeElement(nonZeroBytes(32))));

        assertEquals(new UnsignedSignature((byte) 1), validated.signature());
    }

    @Test
    void validate_coinScalarWiderThanDataWord_isRejected() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.coinScalar(0, "Value is not valid", "Value"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Value is not valid", e.getMessage());
    }

    @Test
    void validate_leadingZeroCoinScalarOfThirtyThreeBytes_passesDataWordAndFailsCanonical() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.coinScalar(0, "Value is not valid", "Value"));
        byte[] leadingZero = nonZeroBytes(33);
        leadingZero[0] = 0;
        RLPList txFields = envelope(RLP.encodeElement(leadingZero));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Value must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_rawScalarWithLeadingZero_isRejectedAsNonCanonical() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        RLPList txFields = envelope(item(0, 5));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Nonce must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_dataWordChecksOfAllScalarsRunBeforeAnyCanonicalCheck() {
        TypedEnvelopeSchema schema = schema(
                TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"),
                TypedEnvelopeSchema.scalar(1, "Gas Limit is not valid", "Gas Limit"));
        RLPList txFields = envelope(item(0, 5), RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Gas Limit is not valid", e.getMessage());
    }

    @Test
    void validate_crossFieldChecksRunBeforeScalarChecks() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> schema.validate(txFields, fields -> {
                    throw new IllegalStateException("cross-field checks ran");
                }));

        assertEquals("cross-field checks ran", e.getMessage());
    }

    @Test
    void validate_crossFieldChecksRunAfterFraming() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes());
        RLPList txFields = envelope(EMPTY_LIST);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema.validate(txFields, fields -> {
                    throw new IllegalStateException("cross-field checks ran");
                }));

        assertEquals("Transaction field at index 0 must be encoded as an RLP byte string", e.getMessage());
    }

    @Test
    void validate_dataWordChecksFollowCheckRankNotEnvelopeIndex() {
        TypedEnvelopeSchema schema = schema(
                TypedEnvelopeSchema.scalar(1, "Second is not valid", "Second"),
                TypedEnvelopeSchema.coinScalar(0, "First is not valid", "First"));
        RLPList txFields = envelope(RLP.encodeElement(nonZeroBytes(33)), RLP.encodeElement(nonZeroBytes(33)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("First is not valid", e.getMessage());
    }

    @Test
    void validate_canonicalChecksFollowCheckRankNotEnvelopeIndex() {
        TypedEnvelopeSchema schema = schema(
                TypedEnvelopeSchema.scalar(1, "Second is not valid", "Second"),
                TypedEnvelopeSchema.coinScalar(0, "First is not valid", "First"));
        RLPList txFields = envelope(item(0, 5), item(0, 5));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("First must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_signatureFieldsAreChecked() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.bytes());
        RLPList txFields = envelopeWithSignature(new byte[][]{item(1)}, item(1), item(2), item(), item());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Typed transaction yParity must be 0 or 1, got: 2", e.getMessage());
    }

    @Test
    void validate_signatureFieldsRunAfterCanonicalChecks() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.scalar(0, "Nonce is not valid", "Nonce"));
        RLPList txFields = envelopeWithSignature(new byte[][]{item(0, 5)}, item(1), item(2), item(), item());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> schema.validate(txFields));

        assertEquals("Nonce must not have leading zero bytes; zero is encoded as the empty string", e.getMessage());
    }

    @Test
    void validate_returnsTheSignatureStateOfTheDeclaredFields() {
        TypedEnvelopeSchema schema = TypedEnvelopeSchema.of("Type-9",
                TypedEnvelopeSchema.signatureS(), TypedEnvelopeSchema.signatureR(),
                TypedEnvelopeSchema.yParity(), TypedEnvelopeSchema.chainId());

        TypedEnvelopeSchema.ValidatedEnvelope validated = schema.validate(
                envelopeWithSignature(new byte[0][], item(), item(), item(), item(0x21)));

        assertEquals(new UnsignedSignature((byte) 0x21), validated.signature());
    }

    @Test
    void validate_returnsWhatEachListCodecReturned() {
        ListField<String> first = TypedEnvelopeSchema.list(element -> "first");
        ListField<String> second = TypedEnvelopeSchema.list(element -> "second");
        TypedEnvelopeSchema schema = schema(first, second);

        TypedEnvelopeSchema.ValidatedEnvelope validated = schema.validate(envelope(EMPTY_LIST, EMPTY_LIST));

        assertEquals("first", validated.get(first));
        assertEquals("second", validated.get(second));
    }

    @Test
    void get_listFieldNotDeclaredInTheSchema_isRejected() {
        TypedEnvelopeSchema schema = schema(TypedEnvelopeSchema.list(element -> "declared"));
        TypedEnvelopeSchema.ValidatedEnvelope validated = schema.validate(envelope(EMPTY_LIST));
        ListField<String> undeclared = TypedEnvelopeSchema.list(element -> "undeclared");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> validated.get(undeclared));

        assertEquals("List field is not declared in this schema", e.getMessage());
    }

    @Test
    void of_withoutASignatureField_isRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TypedEnvelopeSchema.of("Type-9", TypedEnvelopeSchema.chainId(),
                        TypedEnvelopeSchema.yParity(), TypedEnvelopeSchema.signatureR()));

        assertEquals("A typed envelope schema must declare exactly one SIGNATURE_S field, got 0", e.getMessage());
    }

    @Test
    void of_withADuplicateSignatureField_isRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schema(TypedEnvelopeSchema.chainId()));

        assertEquals("A typed envelope schema must declare exactly one CHAIN_ID field, got 2", e.getMessage());
    }

    private static byte[] nonZeroBytes(int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte) 1);
        return data;
    }
}
