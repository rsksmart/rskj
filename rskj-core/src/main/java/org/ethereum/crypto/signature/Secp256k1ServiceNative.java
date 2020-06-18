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
import org.bouncycastle.util.Arrays;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Implementation of SignatureService with Native library.
 */
public class Secp256k1ServiceNative extends Secp256k1ServiceBC {

    private static final Logger logger = LoggerFactory.getLogger(Secp256k1ServiceNative.class);

    @Nullable
    @Override
    public ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed) {
        check(recId >= 0, "recId must be positive");
        check(sig.getR().signum() >= 0, "r must be positive");
        check(sig.getS().signum() >= 0, "s must be positive");
        check(messageHash != null, "messageHash must not be null");
        byte[] pbKey;
        try {
            byte[] sigBytes = concatenate(sig, true);
            logger.trace("Recovering key from signature: comporessed[{}] - recId[{}] - sig[{}] - msgHash[{}].", compressed, recId, sigBytes, messageHash);
            pbKey = NativeSecp256k1.ecdsaRecover(sigBytes, messageHash, recId, compressed);
        } catch (Exception e) {
            logger.error("Couldnt recover key from signature.", e);
            return null;
        }
        return ECKey.fromPublicOnly(pbKey);
    }

    /**
     * It returns a 64 byte array long, with r + s
     * @param sig {r,s}
     * @return r + s (64 bytes array)
     */
    byte[] concatenate(ECDSASignature sig, boolean fixed) {
        byte[] rBytes = sig.getR().toByteArray();
        byte[] sBytes = sig.getS().toByteArray();
        byte[] allByteArray = new byte[fixed ? 64 : rBytes.length + sBytes.length];
        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        if(fixed) {
            for (int i = rBytes.length; i < 32; i++) {
                buff.put((byte) 0);
            }
        }
        buff.put(Arrays.copyOfRange(rBytes, getStartIndex(rBytes, fixed), rBytes.length));
        if(fixed) {
            for (int i = sBytes.length; i < 32; i++) {
                buff.put((byte) 0);
            }
        }
        buff.put(Arrays.copyOfRange(sBytes, getStartIndex(sBytes, fixed), sBytes.length));
        return buff.array();
    }

    /**
     *  If bytes length  is greater than 32, we keep the last 32 bytes at the right.
     *          - So starting byte index will be = length - 32.
     *  If not
     *          -  Starting byte index = 0.
     * @param sBytes
     * @return
     */
    private int getStartIndex(byte[] sBytes, boolean fixed) {
        return sBytes.length > 32 && fixed ? sBytes.length - 32 : 0;
    }
}
