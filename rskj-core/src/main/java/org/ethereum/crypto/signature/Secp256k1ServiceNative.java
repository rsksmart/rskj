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

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Implementation of SignatureService with Native library.
 * TODO: once integrated native implementation, should implement all methods.
 */
public class Secp256k1ServiceNative extends Secp256k1ServiceBC {

    @Nullable
    @Override
    public ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed) {
        check(recId >= 0, "recId must be positive");
        check(sig.getR().signum() >= 0, "r must be positive");
        check(sig.getS().signum() >= 0, "s must be positive");
        check(messageHash != null, "messageHash must not be null");
        byte[] sigBytes = concatenate(sig);
        byte[] pbKey = new byte[0];
        try {
            pbKey = NativeSecp256k1.ecdsaRecover(sigBytes, messageHash, recId, compressed);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ECKey.fromPublicOnly(pbKey);
    }

    @Override
    public boolean verify(byte[] data, ECDSASignature signature, byte[] pub) {
        try {
            return NativeSecp256k1.verify(data, concatenate(signature), pub);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    byte[] concatenate(ECDSASignature sig) {
        byte[] rBytes = sig.getR().toByteArray();
        byte[] sBytes = sig.getS().toByteArray();
        byte[] allByteArray = new byte[64];
        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        buff.put(Arrays.copyOfRange(rBytes, rBytes.length - 32, rBytes.length));
        buff.put(Arrays.copyOfRange(sBytes, sBytes.length - 32, sBytes.length));
        return buff.array();
    }
}
