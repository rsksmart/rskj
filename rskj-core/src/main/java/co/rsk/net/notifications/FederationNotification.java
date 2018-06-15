/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.net.notifications;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FederationNotification extends Message {


    private RskAddress source;
    private Instant timeToLive;
    private Instant timestamp;
    private boolean frozen;
    private ECDSASignature signature;
    private List<Confirmation> confirmations;

    public FederationNotification(RskAddress source, Instant timeToLive, Instant timestamp, boolean frozen, ECDSASignature signature) {
        this(source, timeToLive, timestamp, frozen);
        this.signature = signature;
    }

    public FederationNotification(RskAddress source, Instant timeToLive, Instant timestamp, boolean frozen) {
        this(source, timeToLive, timestamp);
        this.frozen = frozen;
    }

    public FederationNotification(RskAddress source, Instant timeToLive, Instant timestamp) {
        this.source = source;
        this.timeToLive = timeToLive;
        this.timestamp = timestamp;
        this.confirmations = new ArrayList<>();
        this.frozen = false;
    }

    public void addConfirmation(long blockNumber, Keccak256 blockHash) {
        Confirmation c = new Confirmation(blockNumber, blockHash);
        confirmations.add(c);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FEDERATION_NOTIFICATION;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] source = RLP.encodeRskAddress(this.source);
        byte[] timeToLive = RLP.encodeElement(longToBytes(this.timeToLive.toEpochMilli()));
        byte[] timestamp = RLP.encodeElement(longToBytes(this.timeToLive.toEpochMilli()));
        byte[] frozen = RLP.encodeElement(this.frozen ? RLP.TRUE : RLP.FALSE);
        byte[] r = RLP.encodeBigInteger(this.signature.r);
        byte[] s = RLP.encodeBigInteger(this.signature.s);
        byte[] v = RLP.encodeElement(new byte[]{this.signature.v});

        byte[][] encodedConfirmations = new byte[confirmations.size()][];
        for (int i = 0; i < confirmations.size(); i++) {
            encodedConfirmations[i] = confirmations.get(i).getEncoded();
        }

        return RLP.encodeList(source, timeToLive, timestamp, frozen, r, s, v, RLP.encodeList(encodedConfirmations));
    }

    public byte[] getHash() {
        byte[] source = RLP.encodeRskAddress(this.source);
        byte[] timeToLive = RLP.encodeElement(longToBytes(this.timeToLive.toEpochMilli()));
        byte[] timestamp = RLP.encodeElement(longToBytes(this.timeToLive.toEpochMilli()));

        byte[][] encodedConfirmations = new byte[confirmations.size()][];
        for (int i = 0; i < confirmations.size(); i++) {
            encodedConfirmations[i] = confirmations.get(i).getEncoded();
        }

        byte[] data = RLP.encodeList(source, timeToLive, timestamp, RLP.encodeList(encodedConfirmations));
        return HashUtil.keccak256(data);
    }

    public RskAddress getSource() {
        return source;
    }

    public boolean isExpired() {
        return this.timeToLive.compareTo(Instant.now()) <= 0;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public Confirmation getConfirmation(int index) {
        if (index >= confirmations.size() || index < 0) {
            throw new IllegalArgumentException("Trying to retrieve a confirmation with an illegal index: " + index);
        }

        // Return a copy to prevent modifications from the outside of the FederationNotification confirmations.
        Confirmation c = confirmations.get(index);
        return c.copy();
    }

    public boolean hasConfirmations() {
        return !confirmations.isEmpty();
    }

    public boolean isFederationFrozen() {
        return frozen;
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public void setSignature(ECDSASignature signature) {
        this.signature = signature;
    }

    public boolean verifySignature(ECKey publicKey) {
        ECDSASignature signature = getSignature();
        byte[] hash = getHash();

        return publicKey.verify(hash, signature);
    }

    private byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
