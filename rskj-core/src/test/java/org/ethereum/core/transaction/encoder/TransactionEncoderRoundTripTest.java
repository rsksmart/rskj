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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.HIGH_CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.assertCoreFieldsMatch;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedLegacy;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType1;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType2;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType4;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-type encoder/parser round trip: {@code parse(encodeSigned(tx))} must reproduce
 * the transaction field-by-field, re-encode to the same wire bytes, produce the same
 * signing payload ({@code getEncodedRaw}/{@code getRawHash}), and recover the same sender.
 *
 * <p>This catches encoder/parser asymmetries that per-side unit tests miss, because a
 * defect in only one side breaks the trip. Note it cannot catch a matching defect on
 * both sides — that is what {@link GoldenTransactionEncoderTest} pins with independently
 * generated vectors.
 */
class TransactionEncoderRoundTripTest {

    private static Stream<Arguments> roundTripCases() {
        return Stream.of(
                Arguments.of("legacy-chain0", (Supplier<Transaction>) () -> unsignedLegacy((byte) 0), null),
                Arguments.of("legacy-chain33", (Supplier<Transaction>) () -> unsignedLegacy(CHAIN_ID), null),
                Arguments.of("type1-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType1, (byte) 0x01),
                Arguments.of("type2-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType2, (byte) 0x02),
                Arguments.of("type4-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType4, (byte) 0x04),
                // ChainIds in the 128-255 range: stored as a negative signed byte, so these lock in the
                // unsigned-byte encode/parse path that prevents cross-chain replay via silent truncation.
                Arguments.of("type1-chain200", (Supplier<Transaction>) () -> unsignedType1(HIGH_CHAIN_ID), (byte) 0x01),
                Arguments.of("type2-chain200", (Supplier<Transaction>) () -> unsignedType2(HIGH_CHAIN_ID), (byte) 0x02),
                Arguments.of("type4-chain200", (Supplier<Transaction>) () -> unsignedType4(HIGH_CHAIN_ID), (byte) 0x04)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTripCases")
    void signedTransaction_roundTripsThroughEncoderAndParser(
            String id, Supplier<Transaction> transactionSupplier, Byte expectedTypeByte) {
        Transaction original = transactionSupplier.get();
        original.sign(PRIVATE_KEY);

        byte[] wire = TransactionEncoderFactory.getEncoder(original).encodeSigned(original);
        assertArrayEquals(original.getEncoded(), wire, "encodeSigned must match getEncoded");
        assertLeadingByte(wire, expectedTypeByte, "wire encoding");

        Transaction decoded = Transaction.fromRaw(wire);

        assertCoreFieldsMatch(original, decoded);
        assertArrayEquals(wire, decoded.getEncoded(), "re-encoding the parsed transaction must be byte-identical");
        assertEquals(original.getHash(), decoded.getHash(), "transaction id must survive the round trip");
        assertEquals(original.getSender(), decoded.getSender(), "recovered sender must survive the round trip");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTripCases")
    void signingPayload_isPreservedAcrossRoundTrip(
            String id, Supplier<Transaction> transactionSupplier, Byte expectedTypeByte) {
        Transaction original = transactionSupplier.get();
        byte[] signingPayload = original.getEncodedRaw();
        assertLeadingByte(signingPayload, expectedTypeByte, "signing payload");

        original.sign(PRIVATE_KEY);
        Transaction decoded = Transaction.fromRaw(original.getEncoded());

        assertArrayEquals(signingPayload, decoded.getEncodedRaw(),
                "signing payload must be identical after a wire round trip, otherwise "
                        + "signature verification diverges between the signer and the node");
        assertEquals(original.getRawHash(), decoded.getRawHash(), "raw (signing) hash must survive the round trip");
    }

    /**
     * RSKIP-543: typed transactions sign over {@code type || payload}; legacy transactions
     * sign over a bare RLP list. A missing or spurious prefix here is a consensus bug.
     */
    private static void assertLeadingByte(byte[] encoded, Byte expectedTypeByte, String what) {
        if (expectedTypeByte == null) {
            assertTrue((encoded[0] & 0xFF) >= 0xC0,
                    what + " of a legacy transaction must start with an RLP list marker");
        } else {
            assertEquals(expectedTypeByte.byteValue(), encoded[0],
                    what + " must start with the RSKIP-543 type byte");
        }
    }
}
