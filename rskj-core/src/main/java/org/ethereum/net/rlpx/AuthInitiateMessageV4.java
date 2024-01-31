/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.net.rlpx;

import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;
import static org.ethereum.util.ByteUtil.merge;

/**
 * Auth Initiate message defined by EIP-8
 *
 * @author mkalinin
 * @since 17.02.2016
 */
public class AuthInitiateMessageV4 {

    private ECDSASignature signature; // 65 bytes
    ECPoint publicKey; // 64 bytes - uncompressed and no type byte
    byte[] nonce; // 32 bytes
    int version = 4; // 4 bytes

    public AuthInitiateMessageV4() {
    }

    static AuthInitiateMessageV4 decode(byte[] wire) {
        AuthInitiateMessageV4 message = new AuthInitiateMessageV4();

        RLPList params = RLP.decodeList(wire);

        byte[] signatureBytes = params.get(0).getRLPData();
        int offset = 0;
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(signatureBytes, offset, r, 0, 32);
        offset += 32;
        System.arraycopy(signatureBytes, offset, s, 0, 32);
        offset += 32;
        int v = signatureBytes[offset] + 27;
        message.signature = ECDSASignature.fromComponents(r, s, (byte)v);

        byte[] publicKeyBytes = params.get(1).getRLPData();
        byte[] bytes = new byte[65];
        System.arraycopy(publicKeyBytes, 0, bytes, 1, 64);
        bytes[0] = 0x04; // uncompressed
        message.publicKey = ECKey.CURVE.getCurve().decodePoint(bytes);

        message.nonce = params.get(2).getRLPData();

        byte[] versionBytes = params.get(3).getRLPData();
        message.version = ByteUtil.byteArrayToInt(versionBytes);

        return message;
    }

    public byte[] encode() {

        byte[] rsigPad = new byte[32];
        byte[] rsig = asUnsignedByteArray(signature.getR());
        System.arraycopy(rsig, 0, rsigPad, rsigPad.length - rsig.length, rsig.length);

        byte[] ssigPad = new byte[32];
        byte[] ssig = asUnsignedByteArray(signature.getS());
        System.arraycopy(ssig, 0, ssigPad, ssigPad.length - ssig.length, ssig.length);

        byte[] publicKey = new byte[64];
        System.arraycopy(this.publicKey.getEncoded(false), 1, publicKey, 0, publicKey.length);

        byte[] sigBytes = RLP.encodeElement(merge(rsigPad, ssigPad, new byte[]{EncryptionHandshake.recIdFromSignatureV(signature.getV())}));
        byte[] publicBytes = RLP.encodeElement(publicKey);
        byte[] nonceBytes = RLP.encodeElement(nonce);
        byte[] versionBytes = RLP.encodeInt(version);

        return RLP.encodeList(sigBytes, publicBytes, nonceBytes, versionBytes);
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public void setSignature(ECDSASignature signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {

        byte[] sigBytes = merge(asUnsignedByteArray(signature.getR()),
                asUnsignedByteArray(signature.getS()), new byte[]{EncryptionHandshake.recIdFromSignatureV(signature.getV())});

        return "AuthInitiateMessage{" +
                "\n  sigBytes=" + ByteUtil.toHexString(sigBytes) +
                "\n  publicKey=" + ByteUtil.toHexString(publicKey.getEncoded(false)) +
                "\n  nonce=" + ByteUtil.toHexString(nonce) +
                "\n  version=" + version +
                "\n}";
    }
}
