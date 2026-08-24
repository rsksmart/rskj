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
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public final class TypedTransactionCodec {

    private static final byte LOWER_REAL_V = 27;

    private TypedTransactionCodec() {
    }

    public static SignatureState parseTypedSignatureState(RLPList txFields,
                                                      int chainIdIndex,
                                                      int yParityIndex,
                                                      int rIndex,
                                                      int sIndex) {
        byte[] r = txFields.get(rIndex).getRLPData();
        byte[] s = txFields.get(sIndex).getRLPData();
        // Validated before the unsigned early return so the field is checked on every path.
        // Callers run requireFieldCount first, so the index always exists.
        byte yParity = parseTypedYParity(txFields.get(yParityIndex).getRLPData());

        if (r == null && s == null) {
            byte chainId = parseTypedTxChainId(txFields.get(chainIdIndex).getRLPData());
            return new UnsignedSignature(chainId);
        }

        if (r == null || s == null) {
            throw new IllegalArgumentException("Typed transaction signature is incomplete");
        }
        CommonParsingUtils.requireSignatureComponent(r, "Signature R is not valid");
        CommonParsingUtils.requireSignatureComponent(s, "Signature S is not valid");
        byte v = (byte) (LOWER_REAL_V + yParity);
        byte chainId = parseTypedTxChainId(txFields.get(chainIdIndex).getRLPData());
        return new SignedSignature(chainId, ECDSASignature.fromComponents(r, s, v));
    }

    /**
     * Parses the yParity field of a typed transaction envelope.
     *
     * <p>Rejects payloads wider than one byte and values outside {@code {0, 1}}. A multi-byte
     * payload such as {@code 0x00 0x01} would otherwise read as yParity 0 and recover a different
     * address than the signer.
     *
     * <p>Both encodings of zero are accepted: the canonical empty item that RLP produces for the
     * integer 0, and a single {@code 0x00} byte. The latter is non-canonical per EIP-2718, but it
     * cannot be used for malleability here — {@code Transaction.getEncoded} re-encodes the parsed
     * fields through {@code TransactionEncoderFactory} instead of retaining the raw bytes, so both
     * spellings yield the same transaction hash.
     */
    private static byte parseTypedYParity(byte[] yParityData) {
        if (yParityData == null || yParityData.length == 0) {
            return 0;
        }
        if (yParityData.length > 1) {
            throw new IllegalArgumentException("Typed transaction yParity must fit in a single byte");
        }
        byte yParity = yParityData[0];
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException("Typed transaction yParity must be 0 or 1, got: " + (yParity & 0xFF));
        }
        return yParity;
    }

    /**
     * Parses the chain ID for typed transactions (Type 1 / Type 2).
     * Per EIP-2718, the chain ID must be a canonical integer that fits in a single unsigned byte
     * (values 1–255). A zero chain ID is rejected: typed transactions require a chain ID.
     * Values larger than 255 are rejected to prevent cross-chain replay via silent truncation.
     */
    private static byte parseTypedTxChainId(byte[] chainIdData) {
        if (chainIdData == null || chainIdData.length == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero or absent");
        }
        BigInteger chainIdValue = new BigInteger(1, chainIdData);
        if (chainIdValue.signum() == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero");
        }
        if (chainIdValue.compareTo(BigInteger.valueOf(255)) > 0) {
            throw new IllegalArgumentException("Typed transaction chainId exceeds maximum supported value of 255, got: " + chainIdValue);
        }
        return chainIdValue.byteValue();
    }
}
