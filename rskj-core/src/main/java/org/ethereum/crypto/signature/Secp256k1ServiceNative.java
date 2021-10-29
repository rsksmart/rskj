/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.crypto.signature;

import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Exception;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static java.lang.System.arraycopy;

/**
 * Implementation of SignatureService with Native library.
 */
public class Secp256k1ServiceNative extends Secp256k1ServiceBC {

    private static final Logger logger = LoggerFactory.getLogger(Secp256k1ServiceNative.class);
    private static final byte[] ZERO_PUB = {0};

    @Nullable
    @Override
    public ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed) {
        check(recId >= 0, "recId must be positive");
        check(recId <= 3, "recId must be less than or equal to 3");
        check(sig.getR().signum() >= 0, "r must be positive");
        check(sig.getS().signum() >= 0, "s must be positive");
        check(messageHash != null, "messageHash must not be null");

        // to be compatible with BC implementation. and to prevent garbage in signature truncation when concatenate.
        if (!sig.validateComponentsWithoutV()) {
            return null;
        }

        byte[] pbKey;
        try {
            byte[] sigBytes = concatenate(sig);
            logger.trace("Recovering key from signature: comporessed[{}] - recId[{}] - sig[{}] - msgHash[{}].", compressed, recId, sigBytes, messageHash);
            try {
                pbKey = NativeSecp256k1.ecdsaRecover(sigBytes, messageHash, recId, compressed);
            } catch (NativeSecp256k1Exception e) {
                if (NativeSecp256k1.isInfinity(sigBytes, messageHash, recId)) {
                    return ECKey.fromPublicOnly(ZERO_PUB);
                }
                throw e;
            }
        } catch (Exception e) {
            logger.error("Couldnt recover key from signature.", e);
            return null;
        }
        return ECKey.fromPublicOnly(pbKey);
    }

    /**
     * Returns a (r.length + s.length) bytes array long
     * <p>
     * Note: we take 32 bytes from "r" and 32 bytes from "s".
     *
     * @param sig {r,s}
     * @return r + s (64 length byte array)
     */
    byte[] concatenate(ECDSASignature sig) {
        byte[] rBytes = sig.getR().toByteArray();
        byte[] sBytes = sig.getS().toByteArray();
        byte[] result = new byte[64];
        int rLength = getLength(rBytes);
        int sLength = getLength(sBytes);
        arraycopy(rBytes, getStartIndex(rBytes), result, 32 - rLength, rLength);
        arraycopy(sBytes, getStartIndex(sBytes), result, 64 - sLength, sLength);
        return result;
    }

    /**
     * Get the length of valid data to copy from the array, with a max of 32 bytes.
     *
     * @param rs
     * @return
     */
    private static final int getLength(byte[] rs) {
        return Math.min(rs.length, 32);
    }

    /**
     * If bytes length  is greater than 32, we keep the last 32 bytes at the right.
     * - So starting byte index will be = length - 32.
     * If not
     * -  Starting byte index = 0.
     *
     * @param rs
     * @return
     */
    private static final int getStartIndex(byte[] rs) {
        return Math.max(rs.length - 32, 0);
    }
}
