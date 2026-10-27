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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.exception.InvalidRskAddressException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RSKIP-545 Type 4 (EIP-7702 set-code) transaction encoding and decoding.
 *
 * <p>Parallel to {@link TypedTransactionTest} coverage for Type 1 / Type 2 under RSKIP-546.
 */
class Rskip545TypedTransactionTest {

    private static final byte[] EMPTY_DATA = new byte[0];
    private static final ECKey TEST_KEY = new ECKey();
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x1234567890123456789012345678901234567890");
    private static final byte CHAIN_ID = 33;
    private static final byte[] OVERSIZE_WORD = oversizeDataWord();

    private record FieldOverride(int index, byte[] encoded) {}

    private static FieldOverride field(int index, byte[] encoded) {
        return new FieldOverride(index, encoded);
    }

    private static byte[] oversizeDataWord() {
        byte[] word = new byte[33];
        word[0] = 0x01;
        return word;
    }

    private static void assertDecodeRejects(byte[] raw, String expectedMessageFragment) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(raw));
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                "Expected message containing '" + expectedMessageFragment + "', got: " + ex.getMessage());
    }

    private static byte[] rawType4(FieldOverride... overrides) {
        byte[][] fields = defaultSignedType4Fields();
        applyOverrides(fields, overrides);
        return Rskip545TestSupport.buildRawType4Bytes(fields);
    }

    private static byte[] rawUnsignedType4(FieldOverride... overrides) {
        byte[][] fields = defaultSignedType4Fields();
        fields[10] = RLP.encodeElement(null);
        fields[11] = RLP.encodeElement(null);
        fields[12] = RLP.encodeElement(null);
        applyOverrides(fields, overrides);
        return Rskip545TestSupport.buildRawType4Bytes(fields);
    }

    private static byte[][] defaultSignedType4Fields() {
        return Rskip545TestSupport.defaultSignedType4Fields(
                TEST_ADDRESS,
                Rskip545TestSupport.defaultAuthListBytes()
        );
    }

    private static void applyOverrides(byte[][] fields, FieldOverride... overrides) {
        for (FieldOverride override : overrides) {
            fields[override.index()] = override.encoded();
        }
    }

    @Test
    void type4Encoding_startsWithTypePrefix() {
        Transaction tx = createSignedType4(EMPTY_DATA);
        byte[] encoded = tx.getEncoded();

        assertEquals(TransactionType.TYPE_4.getByteCode(), encoded[0]);
        assertTrue((encoded[1] & 0xFF) >= 0xc0,
                "After 0x04 prefix, payload must be an RLP list");
    }

    @Test
    void signedType4_encodeDecode_preservesCoreFields() {
        Transaction original = createSignedType4(EMPTY_DATA);
        byte[] encoded = original.getEncoded();

        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_4, decoded.getType());
        assertArrayEquals(original.getNonce(), decoded.getNonce());
        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.getReceiveAddress(), decoded.getReceiveAddress());
        assertEquals(original.getMaxPriorityFeePerGas(), decoded.getMaxPriorityFeePerGas());
        assertEquals(original.getMaxFeePerGas(), decoded.getMaxFeePerGas());
        assertEquals(1, decoded.getAuthorizationList().size());
        assertEquals(original.getAuthorizationList().get(0),
                decoded.getAuthorizationList().get(0));
    }

    @Test
    void signedType4_doubleEncode_producesIdenticalBytes() {
        Transaction original = createSignedType4(EMPTY_DATA);
        byte[] first = original.getEncoded();

        Transaction decoded = new Transaction(first);
        byte[] second = decoded.getEncoded();

        assertArrayEquals(first, second);
    }

    @Test
    void type4RawEncoding_startsWithTypePrefix() {
        Transaction tx = createType4(EMPTY_DATA);
        byte[] raw = tx.getEncodedRaw();

        assertEquals(TransactionType.TYPE_4.getByteCode(), raw[0]);
    }

    @Test
    void type4FromRpcArgs_encodeDecodePreservesBytes() {
        org.ethereum.rpc.CallArguments args = new org.ethereum.rpc.CallArguments();
        args.setFrom("0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826");
        args.setTo("0x7986b3df570230288501eea3d890bd66948c9b79");
        args.setGas("0x5208");
        args.setMaxPriorityFeePerGas("0x3b9aca00");
        args.setMaxFeePerGas("0x77359400");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setChainId("0x21");
        args.setType("0x4");

        org.ethereum.rpc.CallArguments.AuthorizationListEntry entry =
                new org.ethereum.rpc.CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress("0x0000000000000000000000000000000000000003");
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0x01");
        entry.setS("0x01");
        args.setAuthorizationList(List.of(entry));

        Transaction tx = Transaction.fromCallArguments(args, () -> "0", CHAIN_ID);
        tx.sign(TEST_KEY.getPrivKeyBytes());

        byte[] encoded = tx.getEncoded();
        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_4, decoded.getType());
        assertArrayEquals(encoded, decoded.getEncoded());
    }

    @Test
    void type4_builderWithoutAuthorizationList_rejectedAtBuild() {
        assertThrows(RskJsonRpcRequestException.class, () -> Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ONE.toByteArray())
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(100))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(TEST_ADDRESS)
                .value(Coin.ZERO)
                .data(EMPTY_DATA)
                .build());
    }

    @Test
    void type4_builderWithAuthorizationList_buildsViaParser() {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ONE.toByteArray())
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(100))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(TEST_ADDRESS)
                .value(Coin.ZERO)
                .data(EMPTY_DATA)
                .authorizationList(List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)))
                .build();

        assertEquals(TransactionType.TYPE_4, tx.getType());
        assertEquals(1, tx.getAuthorizationList().size());
    }

    @Test
    void signedType4_decodeRoundTrip_preservesSigningHash() {
        Transaction original = createSignedType4(EMPTY_DATA);
        Transaction decoded = new ImmutableTransaction(original.getEncoded());

        assertEquals(original.getRawHash(), decoded.getRawHash());
        assertArrayEquals(original.getEncoded(), decoded.getEncoded());
    }

    @Test
    void decode_rejectsInvalidRlpPayload() {
        byte[] raw = ByteUtil.merge(
                new byte[]{TransactionType.TYPE_4.getByteCode()},
                new byte[]{(byte) 0xff, 0x01});

        assertThrows(Exception.class, () -> new ImmutableTransaction(raw));
    }

    @Test
    void decode_rejectsToAddressWrongLength() {
        byte[] badTo = new byte[21];
        badTo[20] = 0x01;
        byte[] raw = rawType4(field(5, RLP.encodeElement(badTo)));

        assertThrows(InvalidRskAddressException.class, () -> new ImmutableTransaction(raw));
    }

    @Test
    void decode_rejectsTruncatedAuthorizationListRlp() {
        byte[] truncatedAuthList = new byte[] {(byte) 0xC4, 0x01};
        byte[] raw = rawType4(field(9, truncatedAuthList));

        assertThrows(Exception.class, () -> new ImmutableTransaction(raw));
    }

    @Test
    void decode_rejectsOversizeNonce() {
        assertDecodeRejects(rawUnsignedType4(field(1, RLP.encodeElement(OVERSIZE_WORD))), "Nonce is not valid");
    }

    @Test
    void decode_rejectsOversizeGasLimit() {
        assertDecodeRejects(rawUnsignedType4(field(4, RLP.encodeElement(OVERSIZE_WORD))), "Gas Limit is not valid");
    }

    @Test
    void decode_rejectsOversizeValue() {
        assertDecodeRejects(rawUnsignedType4(field(6, RLP.encodeElement(OVERSIZE_WORD))), "Value is not valid");
    }

    @Test
    void decode_rejectsOversizeEffectiveGasPrice() {
        assertDecodeRejects(rawUnsignedType4(
                field(2, RLP.encodeCoinNonNullZero(new Coin(OVERSIZE_WORD))),
                field(3, RLP.encodeCoinNonNullZero(new Coin(OVERSIZE_WORD)))
        ), "Gas Price is not valid");
    }

    @Test
    void decode_rejectsOversizeSignatureR() {
        assertDecodeRejects(rawType4(
                field(10, RLP.encodeByte((byte) 0)),
                field(11, RLP.encodeElement(OVERSIZE_WORD)),
                field(12, RLP.encodeElement(new byte[32]))
        ), "Signature R is not valid");
    }

    @Test
    void decode_rejectsOversizeSignatureS() {
        assertDecodeRejects(rawType4(
                field(10, RLP.encodeByte((byte) 0)),
                field(11, RLP.encodeElement(new byte[32])),
                field(12, RLP.encodeElement(OVERSIZE_WORD))
        ), "Signature S is not valid");
    }

    @ParameterizedTest(name = "rskip543={0}, rskip546={1}, rskip545={2} -> blocked={3}")
    @MethodSource("type4ActivationMatrix")
    void isTypedTransactionNotAllowed_respectsRskip545Gate(
            boolean rskip543, boolean rskip546, boolean rskip545, boolean expectBlocked) {
        Transaction tx = createType4(EMPTY_DATA);
        ActivationConfig.ForBlock activations = mockActivations(rskip543, rskip546, rskip545);

        assertEquals(expectBlocked, tx.isTypedTransactionNotAllowed(activations));
    }

    private static Stream<Arguments> type4ActivationMatrix() {
        return Stream.of(
                Arguments.of(false, false, false, true),
                Arguments.of(true, false, false, true),
                Arguments.of(true, true, false, true),
                Arguments.of(true, true, true, false)
        );
    }

    private static Transaction createType4(byte[] data) {
        CallArguments args = Rskip545TestSupport.defaultType4CallArguments(data);
        args.setTo(TEST_ADDRESS.toJsonString());
        args.setMaxPriorityFeePerGas("0x3b9aca00");
        args.setMaxFeePerGas("0x77359400");
        args.setValue("0xde0b6b3a7640000");
        return Transaction.fromCallArguments(args, () -> "1", CHAIN_ID);
    }

    private static Transaction createSignedType4(byte[] data) {
        Transaction tx = createType4(data);
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }

    private static ActivationConfig.ForBlock mockActivations(
            boolean rskip543, boolean rskip546, boolean rskip545) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP543)).thenReturn(rskip543);
        when(activations.isActive(ConsensusRule.RSKIP546)).thenReturn(rskip546);
        when(activations.isActive(ConsensusRule.RSKIP545)).thenReturn(rskip545);
        return activations;
    }
}
