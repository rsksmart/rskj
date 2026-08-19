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
package org.ethereum.core.transaction;

import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public class SetCodeAuthorization {

    private static final byte MAGIC = 0x05;
    private static final BigInteger MAX_NONCE = new BigInteger("FFFFFFFFFFFFFFFF", 16);
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    private final BigInteger chainId;
    private final RskAddress address;
    private final byte[] nonce;
    private final ECDSASignature signature;

    public SetCodeAuthorization(BigInteger chainId, RskAddress address, byte[] nonce, ECDSASignature signature) {
        this.chainId = Objects.requireNonNull(chainId, "chainId");
        this.address = Objects.requireNonNull(address, "address");
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.signature = Objects.requireNonNull(signature, "signature");
    }

    public BigInteger getChainId() {
        return chainId;
    }

    public RskAddress getAddress() {
        return address;
    }

    public byte[] getNonce() {
        return  nonce.clone();
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public byte[] getSigningHash() {
        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(chainId),
                RLP.encodeElement(address.getBytes()),
                RLP.encodeElement(nonce)
        );

        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = MAGIC;
        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);

        return HashUtil.keccak256(payload);
    }

    public void verifyNonceRange() {
        if (nonce.length == 0) {
            throw new IllegalStateException("Nonce is empty");
        }
        BigInteger nonceValue = new BigInteger(1, nonce);
        if (nonceValue.compareTo(MAX_NONCE) >= 0) {
            throw new IllegalStateException("Nonce must be < 2^64 - 1");
        }
    }

    public void verifyLowS() {
        if (signature.getS().compareTo(SECP256K1N_HALF) > 0) {
            throw new IllegalStateException("Signature s exceeds secp256k1n / 2");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SetCodeAuthorization that = (SetCodeAuthorization) o;
        return Objects.equals(chainId, that.chainId)
                && Objects.equals(address, that.address)
                && Objects.deepEquals(nonce, that.nonce)
                && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId, address, Arrays.hashCode(nonce), signature);
    }

}
