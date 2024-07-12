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

import co.rsk.core.types.bytes.Bytes;
import co.rsk.net.NodeID;
import co.rsk.net.discovery.PeerDiscoveryException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.ethereum.crypto.HashUtil.keccak256;
import static org.ethereum.util.ByteUtil.merge;

public abstract class PeerDiscoveryMessage {
    private static final Logger logger = LoggerFactory.getLogger(PeerDiscoveryMessage.class);

    private static final String INVALID_MESSAGE_ID = "%s needs valid messageId";

    private static final int UUID_VERSION_4 = 4;

    private byte[] wire;

    private byte[] mdc;
    private byte[] signature;
    private byte[] type;
    private byte[] data;
    private OptionalInt networkId;

    protected PeerDiscoveryMessage() {
    }

    protected PeerDiscoveryMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
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
        byte[] forSig = HashUtil.keccak256(payload);

        /* [2] Crate signature*/
        ECDSASignature ecdsaSignature = ECDSASignature.fromSignature(privKey.sign(forSig));

        ecdsaSignature.setV((byte) (ecdsaSignature.getV() - 27));

        byte[] sigBytes =
                merge(BigIntegers.asUnsignedByteArray(32, ecdsaSignature.getR()),
                        BigIntegers.asUnsignedByteArray(32, ecdsaSignature.getS()), new byte[]{ecdsaSignature.getV()});

        // [3] calculate MDC
        byte[] forSha = merge(sigBytes, type, data);

        // wrap all the data in to the packet
        this.mdc = HashUtil.keccak256(forSha);
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

        byte[] msgHash = keccak256(wire, 97, wire.length - 97);

        ECKey outKey = null;
        try {
            outKey = Secp256k1.getInstance().signatureToKey(msgHash, ECDSASignature.fromComponents(r, s, v));
        } catch (SignatureException e) {
            logger.error("Error generating key from message", e);
        }

        return outKey;
    }

    public OptionalInt getNetworkId() {
        return this.networkId;
    }

    protected void setNetworkId(final OptionalInt networkId) {
        this.networkId = networkId;
    }

    protected void setNetworkIdWithRLP(final RLPElement networkId) {
        Integer setValue = null;
        if (networkId != null) {
            setValue = ByteUtil.byteArrayToInt(networkId.getRLPData());
        }
        this.setNetworkId(Optional.ofNullable(setValue).map(OptionalInt::of).orElseGet(OptionalInt::empty));
    }

    public NodeID getNodeId() {
        byte[] nodeID = new byte[64];

        System.arraycopy(getKey().getPubKey(), 1, nodeID, 0, 64);

        return new NodeID(nodeID);
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

    protected abstract void parse(byte[] data);

    public DiscoveryMessageType getMessageType() {
        return null;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("mdc", Bytes.of(mdc))
                .append("signature", Bytes.of(signature))
                .append("type", Bytes.of(type))
                .append("data", Bytes.of(data)).toString();
    }

    protected String extractMessageId(RLPItem chk) {
        String rawMessageId = new String(chk.getRLPData(), StandardCharsets.UTF_8);
        try {
            UUID uuid = UUID.fromString(rawMessageId);
            if (uuid.version() != UUID_VERSION_4) {
                throw new IllegalArgumentException("Received an UUID with version != 4");
            }
            return uuid.toString();
        } catch (IllegalArgumentException iae) {
            logger.error("Received message '{}' is not a v4 UUID", rawMessageId, iae);
            String concreteMessageType = this.getClass().getSimpleName();
            throw new PeerDiscoveryException(String.format(INVALID_MESSAGE_ID, concreteMessageType));
        }
    }
}
