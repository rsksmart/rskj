/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.discovery.message;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.security.SignatureException;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.merge;

public abstract class PeerDiscoveryMessage {
    private static final Logger logger = LoggerFactory.getLogger(PeerDiscoveryMessage.class);

    private byte[] wire;

    private byte[] mdc;
    private byte[] signature;
    private byte[] type;
    private byte[] data;

    public PeerDiscoveryMessage() {}

    public PeerDiscoveryMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data){
        this.mdc = mdc;
        this.signature = signature;
        this.type = type;
        this.data = data;
        this.wire = wire;
    }
    public PeerDiscoveryMessage encode(byte[] type, byte[] data, ECKey privKey) {
        /* [1] Calc sha3 - prepare for sig */
        byte[] payload = new byte[type.length + data.length];
        payload[0] = type[0];
        System.arraycopy(data, 0, payload, 1, data.length);
        byte[] forSig = sha3(payload);

        /* [2] Crate signature*/
        ECKey.ECDSASignature ecdsaSignature = privKey.sign(forSig);

        ecdsaSignature.v -= 27;

        byte[] sigBytes =
                merge(BigIntegers.asUnsignedByteArray(32, ecdsaSignature.r),
                        BigIntegers.asUnsignedByteArray(32, ecdsaSignature.s), new byte[]{ecdsaSignature.v});

        // [3] calculate MDC
        byte[] forSha = merge(sigBytes, type, data);

        // wrap all the data in to the packet
        this.mdc = sha3(forSha);
        this.signature = sigBytes;
        this.type = type;
        this.data = data;

        this.wire = merge(this.mdc, this.signature, this.type, this.data);

        return this;
    }

    public ECKey getKey() {

        byte[] r = new byte[32];
        byte[] s = new byte[32];
        byte v = signature[64];

        if (v == 1) {
            v = 28;
        }

        if (v == 0) {
            v = 27;
        }

        System.arraycopy(signature, 0, r, 0, 32);
        System.arraycopy(signature, 32, s, 0, 32);

        byte[] msgHash = sha3(wire, 97, wire.length - 97);

        ECKey outKey = null;
        try {
            outKey = ECKey.signatureToKey(msgHash, ECKey.ECDSASignature.fromComponents(r, s, v).toBase64());
        } catch (SignatureException e) {
            logger.error("Error generating key from message", e);
        }

        return outKey;
    }

    public byte[] getNodeId() {
        byte[] nodeID = new byte[64];

        System.arraycopy(getKey().getPubKey(), 1, nodeID, 0, 64);

        return nodeID;
    }

    public byte[] getPacket() {
        return wire;
    }

    public byte[] getMdc() {
        return mdc;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }

    public abstract void parse(byte[] data);

    public DiscoveryMessageType getMessageType() {
        return null;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("mdc", Hex.toHexString(mdc))
                .append("signature", Hex.toHexString(signature))
                .append("type", Hex.toHexString(type))
                .append("data", Hex.toHexString(data)).toString();
    }
}
