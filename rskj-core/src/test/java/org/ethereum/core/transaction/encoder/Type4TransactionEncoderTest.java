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
import co.rsk.core.RskAddress;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Type4TransactionEncoder}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Type4TransactionEncoderTest {

    private static final byte CHAIN_ID = 33;
    private static final Type4TransactionEncoder ENCODER = new Type4TransactionEncoder();

    @Test
    void encodeForSigning_bothMaxFeesNull_throws() {
        Transaction tx = mockType4Transaction(
                null,
                null,
                List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeForSigning(tx));

        assertTrue(ex.getMessage().contains("maxPriorityFeePerGas and maxFeePerGas"),
                "Expected max fee error, got: " + ex.getMessage());
    }

    @Test
    void encodeForSigning_nullMaxPriorityFeeOnly_throws() {
        Transaction tx = mockType4Transaction(
                null,
                Coin.valueOf(100),
                List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeForSigning(tx));

        assertTrue(ex.getMessage().contains("maxPriorityFeePerGas and maxFeePerGas"),
                "Expected max fee error, got: " + ex.getMessage());
    }

    @Test
    void encodeForSigning_nullMaxFeeOnly_throws() {
        Transaction tx = mockType4Transaction(
                Coin.valueOf(10),
                null,
                List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeForSigning(tx));

        assertTrue(ex.getMessage().contains("maxPriorityFeePerGas and maxFeePerGas"),
                "Expected max fee error, got: " + ex.getMessage());
    }

    @Test
    void encodeForSigning_nullAuthorizationList_throws() {
        Transaction tx = mockType4Transaction(Coin.valueOf(10), Coin.valueOf(100), null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeForSigning(tx));

        assertTrue(ex.getMessage().contains("authorization list"),
                "Expected authorization list error, got: " + ex.getMessage());
    }

    @Test
    void encodeForSigning_emptyAuthorizationList_throws() {
        Transaction tx = mockType4Transaction(Coin.valueOf(10), Coin.valueOf(100), List.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeForSigning(tx));

        assertTrue(ex.getMessage().contains("authorization list"),
                "Expected authorization list error, got: " + ex.getMessage());
    }

    @Test
    void encodeSigned_nullMaxFeeOnly_throws() {
        Transaction tx = mockType4Transaction(
                Coin.valueOf(10),
                null,
                List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeSigned(tx));

        assertTrue(ex.getMessage().contains("maxPriorityFeePerGas and maxFeePerGas"),
                "Expected max fee error, got: " + ex.getMessage());
    }

    @Test
    void encodeSigned_nullAuthorizationList_throws() {
        Transaction tx = mockType4Transaction(Coin.valueOf(10), Coin.valueOf(100), null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ENCODER.encodeSigned(tx));

        assertTrue(ex.getMessage().contains("authorization list"),
                "Expected authorization list error, got: " + ex.getMessage());
    }

    @Test
    void encodeForSigning_unsignedPayload_hasNoSignatureFields() {
        Transaction tx = validUnsignedType4();
        byte[] raw = ENCODER.encodeForSigning(tx);

        assertEquals(TransactionType.TYPE_4.getByteCode(), raw[0]);
        RLPList fields = RLP.decodeList(copyOfRange(raw, 1, raw.length));
        assertEquals(10, fields.size(), "Unsigned Type 4 payload must have 10 fields (no signature)");
    }

    @Test
    void encodeSigned_withoutSignature_usesPlaceholderSignatureFields() {
        Transaction tx = validUnsignedType4();
        assertNull(tx.getSignature());

        byte[] encoded = ENCODER.encodeSigned(tx);
        RLPList fields = decodeSignedPayload(encoded);

        assertEquals(13, fields.size());
        assertNullOrEmpty(fields.get(11).getRLPData(), "r must be empty when unsigned");
        assertNullOrEmpty(fields.get(12).getRLPData(), "s must be empty when unsigned");
        byte[] yParityData = fields.get(10).getRLPData();
        assertTrue(yParityData == null || yParityData.length == 0 || yParityData[0] == 0,
                "yParity defaults to 0 when unsigned");
    }

    @Test
    void encodeSigned_withSignature_encodesYParityRAndS() {
        Transaction tx = validUnsignedType4();
        tx.sign(new ECKey().getPrivKeyBytes());
        assertNotNull(tx.getSignature());

        byte[] encoded = ENCODER.encodeSigned(tx);
        RLPList fields = decodeSignedPayload(encoded);

        assertTrue(fields.get(11).getRLPData().length > 0, "r must be present when signed");
        assertTrue(fields.get(12).getRLPData().length > 0, "s must be present when signed");
        byte[] yParityData = fields.get(10).getRLPData();
        byte yParity = yParityData == null || yParityData.length == 0 ? 0 : yParityData[0];
        assertTrue(yParity == 0 || yParity == 1, "yParity must be 0 or 1");
    }

    @Test
    void encodeSigned_roundTripMatchesTransactionGetEncoded() {
        Transaction tx = validUnsignedType4();
        tx.sign(new ECKey().getPrivKeyBytes());

        assertArrayEquals(tx.getEncoded(), ENCODER.encodeSigned(tx));
    }

    @Test
    void encodeForSigning_changesWhenMaxPriorityFeePerGasChanges() {
        Transaction low = validUnsignedType4();
        Transaction high = Rskip545TestSupport.unsignedType4(
                new RskAddress("0x0000000000000000000000000000000000000002"),
                Coin.valueOf(20),
                Coin.valueOf(100),
                new byte[0],
                Rskip545TestSupport.EMPTY_ACCESS_LIST);

        assertNotEquals(ENCODER.encodeForSigning(low), ENCODER.encodeForSigning(high));
    }

    @Test
    void encodeForSigning_changesWhenMaxFeePerGasChanges() {
        Transaction low = validUnsignedType4();
        Transaction high = Rskip545TestSupport.unsignedType4(
                new RskAddress("0x0000000000000000000000000000000000000002"),
                Coin.valueOf(10),
                Coin.valueOf(200),
                new byte[0],
                Rskip545TestSupport.EMPTY_ACCESS_LIST);

        assertNotEquals(ENCODER.encodeForSigning(low), ENCODER.encodeForSigning(high));
    }

    @Test
    void encodeForSigning_changesWhenAccessListChanges() {
        byte[] nonEmptyAccessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(new byte[20]),
                        RLP.encodeList(RLP.encodeElement(new byte[32]))
                )
        );
        Transaction empty = validUnsignedType4();
        Transaction withAccess = Rskip545TestSupport.unsignedType4(
                new RskAddress("0x0000000000000000000000000000000000000002"),
                Coin.valueOf(10),
                Coin.valueOf(100),
                new byte[0],
                nonEmptyAccessList);

        assertNotEquals(ENCODER.encodeForSigning(empty), ENCODER.encodeForSigning(withAccess));
    }

    @Test
    void encodeForSigning_includesAuthorizationListBytes() {
        Transaction tx = validUnsignedType4();
        byte[] signingPayload = ENCODER.encodeForSigning(tx);

        assertEquals(TransactionType.TYPE_4.getByteCode(), signingPayload[0]);
        assertTrue(signingPayload.length > 1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Transaction mockType4Transaction(
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            List<SetCodeAuthorization> authorizationList) {
        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.getMaxPriorityFeePerGas()).thenReturn(maxPriorityFeePerGas);
        when(tx.getMaxFeePerGas()).thenReturn(maxFeePerGas);
        when(tx.getAuthorizationList()).thenReturn(authorizationList);
        when(tx.getChainId()).thenReturn(CHAIN_ID);
        when(tx.getNonce()).thenReturn(new byte[]{0x01});
        when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(21_000).toByteArray());
        when(tx.getReceiveAddress()).thenReturn(new RskAddress("0x0000000000000000000000000000000000000002"));
        when(tx.getValue()).thenReturn(Coin.ZERO);
        when(tx.getData()).thenReturn(new byte[0]);
        when(tx.getAccessListBytes()).thenReturn(RLP.encodeList());
        return tx;
    }

    private static Transaction validUnsignedType4() {
        return Rskip545TestSupport.unsignedType4();
    }

    private static RLPList decodeSignedPayload(byte[] encoded) {
        assertEquals(TransactionType.TYPE_4.getByteCode(), encoded[0]);
        return RLP.decodeList(copyOfRange(encoded, 1, encoded.length));
    }

    private static void assertNullOrEmpty(byte[] value, String message) {
        assertTrue(value == null || value.length == 0, message);
    }

    private static byte[] copyOfRange(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }
}
