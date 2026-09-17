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
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.FIXED_R;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.FIXED_S;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.FIXED_V_Y_PARITY_0;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType1;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.withFixedSignature;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Type1TransactionEncoder} (RSKIP-546, access-list transaction).
 */
class Type1TransactionEncoderTest {

    private static final Type1TransactionEncoder ENCODER = new Type1TransactionEncoder();

    @Test
    void encodeForSigning_startsWithTypePrefixAndHasEightFields() {
        Transaction tx = unsignedType1();

        byte[] raw = ENCODER.encodeForSigning(tx);

        assertEquals(TransactionType.TYPE_1.getByteCode(), raw[0],
                "Signing payload must start with the 0x01 type prefix (RSKIP-546 signs over it)");
        RLPList fields = decodePayload(raw);
        assertEquals(8, fields.size(), "Unsigned Type 1 payload must have 8 fields");
        assertArrayEquals(new byte[]{CHAIN_ID}, fields.get(0).getRLPData(), "First field must be the chainId");
    }

    @Test
    void encodeSigned_unsigned_usesPlaceholderSignatureFields() {
        Transaction tx = unsignedType1();

        byte[] encoded = ENCODER.encodeSigned(tx);

        assertEquals(TransactionType.TYPE_1.getByteCode(), encoded[0]);
        RLPList fields = decodePayload(encoded);
        assertEquals(11, fields.size(), "Signed Type 1 layout must have 11 fields");
        assertNullOrEmpty(fields.get(8).getRLPData(),
                "yParity defaults to empty RLP byte (0x80) when unsigned - not a bare 0x00");
        assertNullOrEmpty(fields.get(9).getRLPData(), "r must be empty when unsigned");
        assertNullOrEmpty(fields.get(10).getRLPData(), "s must be empty when unsigned");
    }

    @Test
    void encodeSigned_yParity0_encodesAsEmptyRlpByte() {
        Transaction tx = withFixedSignature(unsignedType1(), FIXED_V_Y_PARITY_0);

        RLPList fields = decodePayload(ENCODER.encodeSigned(tx));

        assertNullOrEmpty(fields.get(8).getRLPData(),
                "yParity 0 must encode as empty RLP byte (0x80), not 0x00");
        assertArrayEquals(FIXED_R, fields.get(9).getRLPData());
        assertArrayEquals(FIXED_S, fields.get(10).getRLPData());
    }

    @Test
    void encodeSigned_signed_encodesYParityRAndS() {
        Transaction tx = unsignedType1();
        tx.sign(PRIVATE_KEY);

        RLPList fields = decodePayload(ENCODER.encodeSigned(tx));

        byte[] yParityData = fields.get(8).getRLPData();
        byte yParity = yParityData == null || yParityData.length == 0 ? 0 : yParityData[0];
        assertTrue(yParity == 0 || yParity == 1, "yParity must be 0 or 1 (not a legacy chainId-encoded v)");
        assertTrue(fields.get(9).getRLPData().length > 0, "r must be present when signed");
        assertTrue(fields.get(10).getRLPData().length > 0, "s must be present when signed");
    }

    @Test
    void encodeSigned_matchesTransactionGetEncoded() {
        Transaction tx = unsignedType1();
        tx.sign(PRIVATE_KEY);

        assertArrayEquals(tx.getEncoded(), ENCODER.encodeSigned(tx));
    }

    @Test
    void encodeForSigning_matchesTransactionGetEncodedRaw() {
        Transaction tx = unsignedType1();

        assertArrayEquals(tx.getEncodedRaw(), ENCODER.encodeForSigning(tx));
    }

    @Test
    void encodeForSigning_changesWhenAccessListChanges() {
        byte[] nonEmptyAccessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(new byte[20]),
                        RLP.encodeList(RLP.encodeElement(new byte[32]))
                )
        );

        byte[] withEmptyList = ENCODER.encodeForSigning(unsignedType1());
        byte[] withAccessList = ENCODER.encodeForSigning(unsignedType1(nonEmptyAccessList));

        assertFalse(Arrays.equals(withEmptyList, withAccessList),
                "Access list must be part of the signing payload");
    }

    private static RLPList decodePayload(byte[] encoded) {
        return RLP.decodeList(Arrays.copyOfRange(encoded, 1, encoded.length));
    }

    private static void assertNullOrEmpty(byte[] value, String message) {
        assertTrue(value == null || value.length == 0, message);
    }
}
