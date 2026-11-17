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

import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Type0SignatureUtils}.
 *
 * <p>Covers EIP-155 V encoding: V=27/28 means no chain-id protection; higher values embed
 * the chain ID as {@code (V - 35) / 2}.
 */
class Type0SignatureUtilsTest {

    @Test
    void parseType0SignatureState_vNull_returnsUnsignedWithNoChainId() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(null),
                RLP.encodeElement(new byte[]{0x01}),
                RLP.encodeElement(new byte[]{0x02})
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(UnsignedSignature.class, state);
        assertNull(((UnsignedSignature) state).chainId());
    }

    @Test
    void parseType0SignatureState_vInvalidLength_throws() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{0x00, 0x1b}),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        assertThrows(TransactionException.class,
                () -> Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2));
    }

    @ParameterizedTest
    @ValueSource(bytes = {27, 28})
    void parseType0SignatureState_preEip155V_returnsSignedWithChainIdZero(byte v) {
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{v}),
                RLP.encodeElement(dummyRS),
                RLP.encodeElement(dummyRS)
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(SignedSignature.class, state);
        assertEquals(0, ((SignedSignature) state).chainId());
        assertEquals(v, ((SignedSignature) state).signature().getV());
    }

    @Test
    void parseType0SignatureState_eip155EvenV_returnsSignedWithChainIdAndRealV28() {
        // V=102 → chainId=33, real V=28 (even EIP-155 V)
        byte v = (byte) 102;
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{v}),
                RLP.encodeElement(dummyRS),
                RLP.encodeElement(dummyRS)
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(SignedSignature.class, state);
        assertEquals(33, ((SignedSignature) state).chainId() & 0xFF);
        assertEquals(28, ((SignedSignature) state).signature().getV());
    }

    @Test
    void parseType0SignatureState_eip155OddV_returnsSignedWithChainIdAndRealV27() {
        // V=101 → chainId=33, real V=27 (odd EIP-155 V)
        byte v = (byte) 101;
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{v}),
                RLP.encodeElement(dummyRS),
                RLP.encodeElement(dummyRS)
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(SignedSignature.class, state);
        assertEquals(33, ((SignedSignature) state).chainId() & 0xFF);
        assertEquals(27, ((SignedSignature) state).signature().getV());
    }

    @Test
    void parseType0SignatureState_eip155ChainId1_extractsCorrectly() {
        // V = chainId*2 + 35 + yParity → chainId=1 even parity: 1*2+35=37
        byte v = (byte) 37;
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{v}),
                RLP.encodeElement(dummyRS),
                RLP.encodeElement(dummyRS)
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(SignedSignature.class, state);
        assertEquals(1, ((SignedSignature) state).chainId() & 0xFF);
    }
}
