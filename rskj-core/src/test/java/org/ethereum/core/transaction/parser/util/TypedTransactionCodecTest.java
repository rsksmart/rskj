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

import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TypedTransactionCodec}.
 *
 * <p>Typed transactions (EIP-2718 Type 1 / Type 2) use yParity instead of EIP-155 V,
 * and carry an explicit chainId that must be 1–255.
 */
class TypedTransactionCodecTest {

    // -------------------------------------------------------------------------
    // parseRequiredTypedChainId
    // -------------------------------------------------------------------------

    @Test
    void parseRequiredTypedChainId_null_throws() {
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> TypedTransactionCodec.parseRequiredTypedChainId(null));
        assertTrue(ex.getMessage().contains("chainId"),
                "Error must mention chainId, got: " + ex.getMessage());
    }

    @Test
    void parseRequiredTypedChainId_explicitZero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> TypedTransactionCodec.parseRequiredTypedChainId("0x0"));
    }

    @Test
    void parseRequiredTypedChainId_chainIdExceeds255_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> TypedTransactionCodec.parseRequiredTypedChainId("0x100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x1", "0x21", "0xff"})
    void parseRequiredTypedChainId_validChainId_returnsCorrectByte(String hex) {
        assertDoesNotThrow(() -> TypedTransactionCodec.parseRequiredTypedChainId(hex));
    }

    @Test
    void parseRequiredTypedChainId_regtestChainId_parsesCorrectly() {
        byte result = TypedTransactionCodec.parseRequiredTypedChainId("0x21");
        assertEquals((byte) 33, result);
    }

    // -------------------------------------------------------------------------
    // parseTypedSignatureState — unsigned (both r and s absent)
    // -------------------------------------------------------------------------

    @Test
    void parseTypedSignatureState_bothRsAbsent_returnsUnsignedWithChainId() {
        // Fields at indices: 0=chainId, 1=yParity, 2=r, 3=s
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{33}),  // chainId = 33
                RLP.encodeElement(null),              // yParity absent
                RLP.encodeElement(null),              // r absent
                RLP.encodeElement(null)               // s absent
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = TypedTransactionCodec.parseTypedSignatureState(list, 0, 1, 2, 3);

        assertInstanceOf(UnsignedSignature.class, state);
        assertFalse(state.isSigned());
        assertEquals(33, ((UnsignedSignature) state).chainId() & 0xFF);
    }

    // -------------------------------------------------------------------------
    // parseTypedSignatureState — incomplete signature (only one of r, s)
    // -------------------------------------------------------------------------

    @Test
    void parseTypedSignatureState_rPresentSAbsent_throws() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{33}),
                RLP.encodeElement(new byte[]{0}),
                RLP.encodeElement(new byte[32]),    // r present
                RLP.encodeElement(null)              // s absent
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> TypedTransactionCodec.parseTypedSignatureState(list, 0, 1, 2, 3));
    }

    @Test
    void parseTypedSignatureState_rAbsentSPresent_throws() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{33}),
                RLP.encodeElement(new byte[]{0}),
                RLP.encodeElement(null),             // r absent
                RLP.encodeElement(new byte[32])      // s present
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> TypedTransactionCodec.parseTypedSignatureState(list, 0, 1, 2, 3));
    }

    // -------------------------------------------------------------------------
    // parseTypedSignatureState — fully signed
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(bytes = {0, 1})
    void parseTypedSignatureState_validYParity_returnsSignedSignature(byte yParity) {
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{33}),       // chainId = 33
                RLP.encodeElement(new byte[]{yParity}),  // yParity
                RLP.encodeElement(dummyRS),              // r
                RLP.encodeElement(dummyRS)               // s
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        SignatureState state = TypedTransactionCodec.parseTypedSignatureState(list, 0, 1, 2, 3);

        assertInstanceOf(SignedSignature.class, state);
        assertTrue(state.isSigned());
        assertEquals(33, ((SignedSignature) state).chainId() & 0xFF);
    }

    @Test
    void parseTypedSignatureState_yParityOutOfRange_throws() {
        byte[] dummyRS = new byte[32];
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{33}),
                RLP.encodeElement(new byte[]{2}),   // invalid yParity
                RLP.encodeElement(dummyRS),
                RLP.encodeElement(dummyRS)
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> TypedTransactionCodec.parseTypedSignatureState(list, 0, 1, 2, 3));
    }
}
