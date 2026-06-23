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
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Type2RSKTransactionEncoderTest {

    private static final RskAddress RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");
    private static final byte[] PRIVATE_KEY = new ECKey().getPrivKeyBytes();
    private final Type2RSKTransactionEncoder encoder = new Type2RSKTransactionEncoder();

    @Test
    void encodeSigned_unsignedWithChainId_usesChainIdPlaceholder() {
        Transaction tx = unsignedRskNamespace((byte) 33);

        byte[] encoded = encoder.encodeSigned(tx);

        assertEquals(0x02, encoded[0]);
        assertEquals(0x03, encoded[1]);
        assertTrue((encoded[2] & 0xFF) >= 0xC0, "Payload must be an RLP list");
    }

    @Test
    void encodeSigned_signedWithChainId_includesSignatureFields() {
        Transaction tx = unsignedRskNamespace((byte) 33);
        tx.sign(PRIVATE_KEY);

        byte[] encoded = encoder.encodeSigned(tx);

        assertEquals(0x02, encoded[0]);
        assertTrue(encoded.length > 10);
    }

    @Test
    void encodeForSigning_withEip1559Fields_throws() {
        Transaction tx = mock(Transaction.class);
        when(tx.getMaxPriorityFeePerGas()).thenReturn(Coin.valueOf(1));
        when(tx.getMaxFeePerGas()).thenReturn(Coin.valueOf(2));

        assertThrows(IllegalStateException.class, () -> encoder.encodeForSigning(tx));
    }

    @Test
    void encodeForSigning_withoutChainId_omitsSignatureFields() {
        Transaction tx = unsignedRskNamespace((byte) 0);

        byte[] raw = encoder.encodeForSigning(tx);

        assertTrue((raw[0] & 0xFF) >= 0xC0, "Legacy-shaped payload must start with RLP list marker");
    }

    @Test
    void encodeForSigning_withChainId_includesChainIdField() {
        Transaction tx = unsignedRskNamespace((byte) 33);

        byte[] raw = encoder.encodeForSigning(tx);

        assertEquals(0x02, raw[0]);
        assertEquals(0x03, raw[1]);
    }

    private static Transaction unsignedRskNamespace(byte chainId) {
        return Transaction.builder()
                .type(TransactionType.TYPE_2, (byte) 0x03)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasPrice(Coin.valueOf(10))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(RECEIVER)
                .value(Coin.ZERO)
                .build();
    }
}
