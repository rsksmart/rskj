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

import co.rsk.core.Coin;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.MAX_FEE;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.MAX_PRIORITY_FEE;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Type2TransactionEncoder} (RSKIP-546).
 */
class Type2TransactionEncoderTest {

    private static final Type2TransactionEncoder ENCODER = new Type2TransactionEncoder();

    @Test
    void encodeForSigning_nullMaxPriorityFee_throws() {
        Transaction tx = mockWithFees(null, MAX_FEE);

        assertThrows(IllegalStateException.class, () -> ENCODER.encodeForSigning(tx));
    }

    @Test
    void encodeForSigning_nullMaxFee_throws() {
        Transaction tx = mockWithFees(MAX_PRIORITY_FEE, null);

        assertThrows(IllegalStateException.class, () -> ENCODER.encodeForSigning(tx));
    }

    @Test
    void encodeSigned_bothFeesNull_throws() {
        Transaction tx = mockWithFees(null, null);

        assertThrows(IllegalStateException.class, () -> ENCODER.encodeSigned(tx));
    }

    @Test
    void encodeForSigning_startsWithTypePrefixAndHasNineFields() {
        Transaction tx = unsignedType2();

        byte[] raw = ENCODER.encodeForSigning(tx);

        assertEquals(TransactionType.TYPE_2.getByteCode(), raw[0],
                "Signing payload must start with the 0x02 type prefix (RSKIP-546 signs over it)");
        RLPList fields = decodePayload(raw);
        assertEquals(9, fields.size(), "Unsigned Type 2 payload must have 9 fields");
        assertArrayEquals(new byte[]{CHAIN_ID}, fields.get(0).getRLPData(), "First field must be the chainId");
    }

    @Test
    void encodeSigned_unsigned_usesPlaceholderSignatureFields() {
        Transaction tx = unsignedType2();

        byte[] encoded = ENCODER.encodeSigned(tx);

        assertEquals(TransactionType.TYPE_2.getByteCode(), encoded[0]);
        RLPList fields = decodePayload(encoded);
        assertEquals(12, fields.size(), "Signed Type 2 layout must have 12 fields");
        assertNullOrZero(fields.get(9).getRLPData(), "yParity defaults to 0 when unsigned");
        assertNullOrEmpty(fields.get(10).getRLPData(), "r must be empty when unsigned");
        assertNullOrEmpty(fields.get(11).getRLPData(), "s must be empty when unsigned");
    }

    @Test
    void encodeSigned_signed_encodesYParityRAndS() {
        Transaction tx = unsignedType2();
        tx.sign(PRIVATE_KEY);

        RLPList fields = decodePayload(ENCODER.encodeSigned(tx));

        byte[] yParityData = fields.get(9).getRLPData();
        byte yParity = yParityData == null || yParityData.length == 0 ? 0 : yParityData[0];
        assertTrue(yParity == 0 || yParity == 1, "yParity must be 0 or 1 (not a legacy chainId-encoded v)");
        assertTrue(fields.get(10).getRLPData().length > 0, "r must be present when signed");
        assertTrue(fields.get(11).getRLPData().length > 0, "s must be present when signed");
    }

    @Test
    void encodeSigned_matchesTransactionGetEncoded() {
        Transaction tx = unsignedType2();
        tx.sign(PRIVATE_KEY);

        assertArrayEquals(tx.getEncoded(), ENCODER.encodeSigned(tx));
    }

    @Test
    void encodeForSigning_matchesTransactionGetEncodedRaw() {
        Transaction tx = unsignedType2();

        assertArrayEquals(tx.getEncodedRaw(), ENCODER.encodeForSigning(tx));
    }

    private static Transaction mockWithFees(Coin maxPriorityFeePerGas, Coin maxFeePerGas) {
        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_2));
        when(tx.getMaxPriorityFeePerGas()).thenReturn(maxPriorityFeePerGas);
        when(tx.getMaxFeePerGas()).thenReturn(maxFeePerGas);
        return tx;
    }

    private static RLPList decodePayload(byte[] encoded) {
        return RLP.decodeList(Arrays.copyOfRange(encoded, 1, encoded.length));
    }

    private static void assertNullOrEmpty(byte[] value, String message) {
        assertTrue(value == null || value.length == 0, message);
    }

    private static void assertNullOrZero(byte[] value, String message) {
        assertTrue(value == null || value.length == 0 || value[0] == 0, message);
    }
}
