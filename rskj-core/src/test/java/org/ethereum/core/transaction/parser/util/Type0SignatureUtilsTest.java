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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Type0SignatureUtils}.
 *
 * <p>Covers EIP-155 V encoding: V=27/28 means no chain-id protection; higher values embed
 * the chain ID as {@code (V - 35) / 2}.
 */
class Type0SignatureUtilsTest {

    // -------------------------------------------------------------------------
    // extractChainIdFromV
    // -------------------------------------------------------------------------

    @Test
    void extractChainIdFromV_v27_returnsZero() {
        assertEquals(0, Type0SignatureUtils.extractChainIdFromV((byte) 27));
    }

    @Test
    void extractChainIdFromV_v28_returnsZero() {
        assertEquals(0, Type0SignatureUtils.extractChainIdFromV((byte) 28));
    }

    @Test
    void extractChainIdFromV_eip155ChainId1_extractsCorrectly() {
        // V = chainId*2 + 35 + yParity → chainId=1 even parity: 1*2+35=37
        byte v = (byte) 37;
        assertEquals(1, Type0SignatureUtils.extractChainIdFromV(v) & 0xFF);
    }

    @Test
    void extractChainIdFromV_eip155ChainId33_extractsCorrectly() {
        // RSK mainnet chainId=33: even parity V = 33*2+35=101
        byte v = (byte) 101;
        assertEquals(33, Type0SignatureUtils.extractChainIdFromV(v) & 0xFF);
    }

    @Test
    void extractChainIdFromV_eip155ChainId33OddParity_extractsCorrectly() {
        // RSK mainnet chainId=33: odd parity V = 33*2+35+1=102
        byte v = (byte) 102;
        assertEquals(33, Type0SignatureUtils.extractChainIdFromV(v) & 0xFF);
    }

    // -------------------------------------------------------------------------
    // getRealV
    // -------------------------------------------------------------------------

    @Test
    void getRealV_v27_returns27() {
        assertEquals(27, Type0SignatureUtils.getRealV((byte) 27));
    }

    @Test
    void getRealV_v28_returns28() {
        assertEquals(28, Type0SignatureUtils.getRealV((byte) 28));
    }

    @Test
    void getRealV_eip155EvenV_returns28() {
        // Even EIP-155 V (e.g. 102 for chainId=33 with yParity=1) → real V is 28
        byte v = (byte) 102;
        assertEquals(28, Type0SignatureUtils.getRealV(v));
    }

    @Test
    void getRealV_eip155OddV_returns27() {
        // Odd EIP-155 V (e.g. 101 for chainId=33 with yParity=0) → real V is 27
        byte v = (byte) 101;
        assertEquals(27, Type0SignatureUtils.getRealV(v));
    }

    // -------------------------------------------------------------------------
    // parseType0SignatureState
    // -------------------------------------------------------------------------

    @Test
    void parseType0SignatureState_vNull_returnsUnsignedWithNoChainId() {
        // Build a 3-field RLP list: v=null, r=some, s=some
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(null),
                RLP.encodeElement(new byte[]{0x01}),
                RLP.encodeElement(new byte[]{0x02})
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = Type0SignatureUtils.parseType0SignatureState(list, 0, 1, 2);

        assertInstanceOf(UnsignedSignature.class, state);
        assertFalse(state.isSigned());
    }

    @Test
    void parseType0SignatureState_vInvalidLength_throws() {
        // V must be exactly 1 byte; two-byte V is rejected
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
        assertTrue(state.isSigned());
        assertEquals(0, ((SignedSignature) state).chainId());
    }

    @Test
    void parseType0SignatureState_eip155V_returnsSignedWithChainId() {
        // V=101 → chainId=33 (RSK mainnet)
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
    }

    // -------------------------------------------------------------------------
    // UnsignedSignature
    // -------------------------------------------------------------------------

    @Test
    void unsignedSignature_hasChainId_falseWhenNull() {
        assertFalse(new UnsignedSignature(null).hasChainId());
    }

    @Test
    void unsignedSignature_hasChainId_trueWhenPresent() {
        assertTrue(new UnsignedSignature((byte) 33).hasChainId());
    }
}
