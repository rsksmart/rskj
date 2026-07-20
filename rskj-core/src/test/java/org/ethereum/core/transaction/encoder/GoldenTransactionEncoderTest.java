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
 * transaction type against golden vectors produced by {@link ReferenceRlpEncoder}, an
 * independent RLP implementation following the canonical RSKIP-543 typed-transaction
 * envelope (RSKIP-546 for type 1/2, RSKIP-545 for type 4; legacy per EIP-155).
 *
 * <p>Round-trip tests stay green when the encoder and parser drift from the spec together;
 * this test is the only one that fails in that case. If it fails, the encoder output no
 * longer matches what other compliant clients would produce - fix the encoder, do not
 * "sync" {@link ReferenceRlpEncoder} to the production output.
 */
class GoldenTransactionEncoderTest {

    private static final Map<String, String[]> VECTORS = ReferenceRlpEncoder.goldenVectors();

    private static Stream<Arguments> goldenCases() {
        return Stream.of(
                Arguments.of("legacy-chain0", (Supplier<Transaction>) () -> unsignedLegacy((byte) 0), FIXED_V),
                Arguments.of("legacy-chain33", (Supplier<Transaction>) () -> unsignedLegacy(CHAIN_ID), FIXED_V),
                Arguments.of("type1-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType1, FIXED_V),
                Arguments.of("type2-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType2, FIXED_V),
                Arguments.of("type2-chain33-yParity0", (Supplier<Transaction>) EncoderTestSupport::unsignedType2, FIXED_V_Y_PARITY_0),
                Arguments.of("type2-chain200", (Supplier<Transaction>) () -> EncoderTestSupport.unsignedType2(HIGH_CHAIN_ID), FIXED_V),
                Arguments.of("type4-chain33", (Supplier<Transaction>) EncoderTestSupport::unsignedType4, FIXED_V)
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

    private static String[] vector(String id) {
        return Objects.requireNonNull(VECTORS.get(id), "missing golden vector: " + id);
    }
}
