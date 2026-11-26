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
package org.ethereum.core.transaction.encoder;

import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedLegacy;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.withFixedSignature;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Type0TransactionEncoder}.
 */
class Type0TransactionEncoderTest {

    private static final Type0TransactionEncoder ENCODER = new Type0TransactionEncoder();

    @Test
    void encodeForSigning_chainIdZero_isBareSixFieldList() {
        Transaction tx = unsignedLegacy((byte) 0);

        byte[] raw = ENCODER.encodeForSigning(tx);

        assertTrue((raw[0] & 0xFF) >= 0xC0, "Legacy signing payload must start with an RLP list marker");
        assertEquals(6, RLP.decodeList(raw).size(),
                "Pre-EIP-155 signing payload must have 6 fields (no chainId placeholder)");
    }

    @Test
    void encodeForSigning_withChainId_appendsEip155Placeholders() {
        Transaction tx = unsignedLegacy(CHAIN_ID);

        byte[] raw = ENCODER.encodeForSigning(tx);

        assertTrue((raw[0] & 0xFF) >= 0xC0, "Legacy signing payload must start with an RLP list marker");
        RLPList fields = RLP.decodeList(raw);
        assertEquals(9, fields.size(), "EIP-155 signing payload must have 9 fields");
        assertArrayEquals(new byte[]{CHAIN_ID}, fields.get(6).getRLPData(), "Field 6 must be the chainId");
        assertNullOrEmpty(fields.get(7).getRLPData(), "Field 7 (r placeholder) must be empty");
        assertNullOrEmpty(fields.get(8).getRLPData(), "Field 8 (s placeholder) must be empty");
    }

    @Test
    void encodeSigned_unsignedChainIdZero_usesEmptyPlaceholders() {
        Transaction tx = unsignedLegacy((byte) 0);

        RLPList fields = RLP.decodeList(ENCODER.encodeSigned(tx));

        assertEquals(9, fields.size());
        assertNullOrEmpty(fields.get(6).getRLPData(), "v must be empty when unsigned and chainId is 0");
        assertNullOrEmpty(fields.get(7).getRLPData(), "r must be empty when unsigned");
        assertNullOrEmpty(fields.get(8).getRLPData(), "s must be empty when unsigned");
    }

    @Test
    void encodeSigned_unsignedWithChainId_usesChainIdAsVPlaceholder() {
        Transaction tx = unsignedLegacy(CHAIN_ID);

        RLPList fields = RLP.decodeList(ENCODER.encodeSigned(tx));

        assertEquals(9, fields.size());
        assertArrayEquals(new byte[]{CHAIN_ID}, fields.get(6).getRLPData(),
                "v must carry the chainId when unsigned (EIP-155 placeholder)");
    }

    @Test
    void encodeSigned_signedWithChainId_encodesEip155V() {
        Transaction tx = unsignedLegacy(CHAIN_ID);
        tx.sign(PRIVATE_KEY);

        RLPList fields = RLP.decodeList(ENCODER.encodeSigned(tx));

        byte expectedV = (byte) ((tx.getSignature().getV() - Transaction.LOWER_REAL_V)
                + (CHAIN_ID * 2 + Transaction.CHAIN_ID_INC));
        assertEquals(9, fields.size());
        assertArrayEquals(new byte[]{expectedV}, fields.get(6).getRLPData(),
                "v must be EIP-155 encoded: yParity + chainId * 2 + 35");
        assertTrue(fields.get(7).getRLPData().length > 0, "r must be present when signed");
        assertTrue(fields.get(8).getRLPData().length > 0, "s must be present when signed");
    }

    @Test
    void encodeSigned_signedChainIdZero_encodesRawV() {
        Transaction tx = unsignedLegacy((byte) 0);
        tx.sign(PRIVATE_KEY);

        RLPList fields = RLP.decodeList(ENCODER.encodeSigned(tx));

        assertArrayEquals(new byte[]{tx.getSignature().getV()}, fields.get(6).getRLPData(),
                "v must be the raw 27/28 recovery id when chainId is 0");
    }

    @Test
    void encodeSigned_matchesTransactionGetEncoded() {
        Transaction tx = unsignedLegacy(CHAIN_ID);
        tx.sign(PRIVATE_KEY);

        assertArrayEquals(tx.getEncoded(), ENCODER.encodeSigned(tx));
    }

    @Test
    void encodeForSigning_matchesTransactionGetEncodedRaw() {
        Transaction tx = unsignedLegacy(CHAIN_ID);

        assertArrayEquals(tx.getEncodedRaw(), ENCODER.encodeForSigning(tx));
    }

    private static Stream<Arguments> highChainIdCases() {
        int yParity0 = Transaction.LOWER_REAL_V;
        int yParity1 = Transaction.LOWER_REAL_V + 1;
        return Stream.of(
                //          chainId  signature v  expected wire v
                Arguments.of(111,    yParity0,    0x01),
                Arguments.of(111,    yParity1,    0x02),
                Arguments.of(127,    yParity0,    0x21),
                Arguments.of(127,    yParity1,    0x22),
                Arguments.of(128,    yParity0,    0x23),
                Arguments.of(128,    yParity1,    0x24),
                Arguments.of(200,    yParity0,    0xb3),
                Arguments.of(200,    yParity1,    0xb4),
                Arguments.of(255,    yParity0,    0x21),
                Arguments.of(255,    yParity1,    0x22));
    }

    @ParameterizedTest(name = "chainId {0}, signature v {1}, wire v {2}")
    @MethodSource("highChainIdCases")
    void encodeSigned_highChainId_keepsVToOneByteTheParserAccepts(int chainId, int sigV, int expectedV) {
        Transaction tx = withFixedSignature(unsignedLegacy((byte) chainId), (byte) sigV);

        byte[] encoded = ENCODER.encodeSigned(tx);
        byte[] wireV = RLP.decodeList(encoded).get(6).getRLPData();

        assertArrayEquals(new byte[]{(byte) expectedV}, wireV, "v must be one byte");
        assertEquals(expectedV, tx.getEncodedV() & 0xFF, "RPC v must match the wire v");
        assertDoesNotThrow(() -> Transaction.fromRaw(encoded), "the parser must accept what the encoder emits");
    }

    private static final Map<Integer, Integer> V_NOT_ROUND_TRIPPED = Map.ofEntries(
            Map.entry(0, 2), Map.entry(2, 4), Map.entry(4, 6), Map.entry(6, 8), Map.entry(8, 10),
            Map.entry(10, 12), Map.entry(12, 14), Map.entry(14, 16), Map.entry(16, 18), Map.entry(18, 20),
            Map.entry(20, 22), Map.entry(22, 24), Map.entry(24, 26), Map.entry(26, 28), Map.entry(30, 32),
            Map.entry(32, 34), Map.entry(34, 28), Map.entry(35, 27), Map.entry(36, 28));

    private static IntStream everyOneByteV() {
        return IntStream.rangeClosed(0, 255);
    }

    @ParameterizedTest(name = "v {0}")
    @MethodSource("everyOneByteV")
    void decodeThenEncode_anyOneByteV_preservesTheBytes(int v) {
        Transaction base = withFixedSignature(unsignedLegacy(CHAIN_ID), Transaction.LOWER_REAL_V);
        RLPList fields = RLP.decodeList(base.getEncoded());
        byte[][] items = new byte[9][];
        for (int i = 0; i < 9; i++) {
            items[i] = RLP.encodeElement(fields.get(i).getRLPData());
        }
        items[6] = RLP.encodeElement(new byte[]{(byte) v});
        byte[] raw = RLP.encodeList(items);

        Transaction decoded = Transaction.fromRaw(raw);
        byte[] reencoded = decoded.getEncoded();
        byte[] wireV = RLP.decodeList(reencoded).get(6).getRLPData();

        assertEquals(1, wireV.length, "re-encoded v must be one byte");
        assertEquals(wireV[0], decoded.getEncodedV(), "RPC v must match the wire v");
        assertDoesNotThrow(() -> Transaction.fromRaw(reencoded), "the parser must accept the re-encoding");

        int expectedV = V_NOT_ROUND_TRIPPED.getOrDefault(v, v);
        assertEquals(expectedV, wireV[0] & 0xFF, "v " + v + " must re-encode as " + expectedV);
        if (expectedV == v) {
            assertArrayEquals(raw, reencoded, "a preserved v must not move any other byte either");
        }
    }

    @Test
    void encodeSigned_chainId110YParity0_isTheLastVThatRoundTripsExactly() {
        // 0 + 110 * 2 + 35 = 255: the largest EIP-155 v that fits in one byte, so nothing is truncated.
        Transaction tx = withFixedSignature(unsignedLegacy((byte) 110), Transaction.LOWER_REAL_V);

        byte[] encoded = ENCODER.encodeSigned(tx);
        Transaction reparsed = Transaction.fromRaw(encoded);

        assertArrayEquals(new byte[]{(byte) 0xFF}, RLP.decodeList(encoded).get(6).getRLPData());
        assertEquals((byte) 110, reparsed.getChainId(), "the chainId must survive the round trip");
        assertArrayEquals(encoded, reparsed.getEncoded(), "re-encoding must give the same bytes");
    }

    @Test
    void encodeSigned_chainId110YParity1_truncatesVToZeroAndReparsesAsUnsigned() {
        // 1 + 110 * 2 + 35 = 256 truncates to 0, which RLP writes as the empty string, the unsigned placeholder
        Transaction tx = withFixedSignature(unsignedLegacy((byte) 110), (byte) (Transaction.LOWER_REAL_V + 1));

        byte[] encoded = ENCODER.encodeSigned(tx);
        byte[] wireV = RLP.decodeList(encoded).get(6).getRLPData();

        assertTrue(wireV == null || wireV.length == 0, "v of 256 truncates to 0, encoded as the empty string");
        assertEquals(0, tx.getEncodedV(), "RPC v must match the wire v");
        Transaction reparsed = assertDoesNotThrow(() -> Transaction.fromRaw(encoded));
        assertNull(reparsed.getSignature(), "an empty v reads back as an unsigned legacy tx");
    }

    private static void assertNullOrEmpty(byte[] value, String message) {
        assertTrue(value == null || value.length == 0, message);
    }
}
