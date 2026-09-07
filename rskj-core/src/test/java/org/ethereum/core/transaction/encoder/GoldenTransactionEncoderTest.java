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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.FIXED_V;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.FIXED_V_Y_PARITY_0;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.HIGH_CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedLegacy;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.withFixedSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@code encodeForSigning} and {@code encodeSigned} output for every supported
 * transaction type against immutable golden hex in {@link GoldenTransactionVectors}
 * (originally produced by {@link ReferenceRlpEncoder}).
 *
 * <p>Round-trip tests stay green when the encoder and parser drift from the spec together;
 * this test is the only one that fails in that case. If it fails, the encoder output no
 * longer matches what other compliant clients would produce — fix the encoder, do not
 * rewrite the pinned vectors from production output.
 *
 * <p>{@link #referenceEncoder_matchesPinnedGoldenVectors()} separately pins
 * {@link ReferenceRlpEncoder} to the same constants so an accidental edit of the reference
 * implementation also fails.
 */
class GoldenTransactionEncoderTest {

    private static Stream<Arguments> goldenCases() {
        return Stream.of(
                Arguments.of("legacy-chain0", (Supplier<Transaction>) () -> unsignedLegacy((byte) 0), FIXED_V),
                Arguments.of("legacy-chain33", (Supplier<Transaction>) () -> unsignedLegacy(CHAIN_ID), FIXED_V),
                Arguments.of("legacy-chain200", (Supplier<Transaction>) () -> unsignedLegacy(HIGH_CHAIN_ID), FIXED_V),
                Arguments.of("legacy-contract-creation",
                        (Supplier<Transaction>) EncoderTestSupport::unsignedLegacyContractCreation, FIXED_V),
                Arguments.of("legacy-long-data",
                        (Supplier<Transaction>) EncoderTestSupport::unsignedLegacyWithLongData, FIXED_V),
                Arguments.of("type1-chain0", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType1((byte) 0), FIXED_V),
                Arguments.of("type1-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType1, FIXED_V),
                Arguments.of("type1-chain200", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType1(HIGH_CHAIN_ID), FIXED_V),
                Arguments.of("type1-access-list",
                        (Supplier<Transaction>) EncoderTestSupport::unsignedType1WithAccessList, FIXED_V),
                Arguments.of("type2-chain0", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType2((byte) 0), FIXED_V),
                Arguments.of("type2-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType2, FIXED_V),
                Arguments.of("type2-chain33-yParity0", (Supplier<Transaction>) EncoderTestSupport::unsignedType2, FIXED_V_Y_PARITY_0),
                Arguments.of("type2-chain200", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType2(HIGH_CHAIN_ID), FIXED_V),
                Arguments.of("type4-chain0", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType4((byte) 0), FIXED_V),
                Arguments.of("type4-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType4, FIXED_V),
                Arguments.of("type4-chain200", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType4(HIGH_CHAIN_ID), FIXED_V)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void encodeForSigning_matchesGoldenVector(String id, Supplier<Transaction> transactionSupplier, byte unusedV) {
        Transaction tx = transactionSupplier.get();
        TransactionEncoder encoder = TransactionEncoderFactory.getEncoder(tx);

        assertEquals(vector(id)[0], Hex.toHexString(encoder.encodeForSigning(tx)),
                "signing payload deviates from the canonical wire format");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void encodeSigned_matchesGoldenVector(String id, Supplier<Transaction> transactionSupplier, byte v) {
        Transaction tx = withFixedSignature(transactionSupplier.get(), v);
        TransactionEncoder encoder = TransactionEncoderFactory.getEncoder(tx);

        assertEquals(vector(id)[1], Hex.toHexString(encoder.encodeSigned(tx)),
                "wire encoding deviates from the canonical wire format");
    }

    @Test
    void referenceEncoder_matchesPinnedGoldenVectors() {
        Map<String, String[]> computed = ReferenceRlpEncoder.goldenVectors();
        assertEquals(GoldenTransactionVectors.VECTORS.keySet(), computed.keySet(),
                "reference encoder case ids drifted from pinned golden vectors");
        for (Map.Entry<String, String[]> pinned : GoldenTransactionVectors.VECTORS.entrySet()) {
            String[] actual = computed.get(pinned.getKey());
            assertEquals(pinned.getValue()[0], actual[0],
                    "reference encodeForSigning drifted for " + pinned.getKey());
            assertEquals(pinned.getValue()[1], actual[1],
                    "reference encodeSigned drifted for " + pinned.getKey());
        }
    }

    private static String[] vector(String id) {
        return Objects.requireNonNull(GoldenTransactionVectors.VECTORS.get(id), "missing golden vector: " + id);
    }
}
