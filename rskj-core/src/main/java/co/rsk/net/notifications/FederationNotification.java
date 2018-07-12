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

import co.rsk.crypto.Keccak256;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.MessageType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FederationNotification extends Message {
    private ECDSASignature signature;

    private FederationNotificationSender sender;

    private Instant timestamp;
    private Instant expiration;

    private boolean frozen;

    private List<Confirmation> confirmations;

    public FederationNotification(Instant timestamp, Instant expiration, boolean frozen, ECDSASignature signature) {
        this.timestamp = timestamp;
        this.expiration = expiration;
        this.frozen = frozen;
        this.signature = signature;
        this.confirmations = new ArrayList<>();
        this.sender = null;
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
        return encode(true);
    }

    private byte[] encode(boolean includeSignature) {
        int elementCount = 4 + (includeSignature ? 1 : 0);
        byte[][] elements = new byte[elementCount][];

        elements[0] = RLP.encodeBigInteger(BigInteger.valueOf(timestamp.toEpochMilli()));
        elements[1] = RLP.encodeBigInteger(BigInteger.valueOf(expiration.toEpochMilli()));
        elements[2] = RLP.encodeElement(frozen ? RLP.TRUE : RLP.FALSE);

        byte[][] encodedConfirmations = new byte[confirmations.size()][];
        for (int i = 0; i < confirmations.size(); i++) {
            encodedConfirmations[i] = confirmations.get(i).getEncoded();
        }
        elements[3] = RLP.encodeList(encodedConfirmations);

        if (includeSignature) {
            byte[] r = RLP.encodeBigInteger(signature.r);
            byte[] s = RLP.encodeBigInteger(signature.s);
            byte[] v = RLP.encodeElement(new byte[]{signature.v});
            elements[4] = RLP.encodeList(r, s, v);
        }

        return RLP.encodeList(elements);
    }

    public byte[] getHashForSignature() {
        return HashUtil.keccak256(encode(false));
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public int getConfirmationCount() {
        return confirmations.size();
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
        // Signature implies the sender, which is cached upon calculation
        this.sender = null;
    }

    public FederationNotificationSender getSender() {
        if (sender != null) {
            return sender;
        }

        if (signature == null) {
            throw new IllegalStateException("Can't extract sender from the FederationNotification: no signature given");
        }

        // Try and extract the public key from the signature
        try {
            ECKey publicKey = ECKey.signatureToKey(getHashForSignature(), getSignature().toBase64());

            sender = new FederationNotificationSender(publicKey);

            return sender;
        } catch (SignatureException e) {
            throw new IllegalStateException("Couldn't extract sender from the FederationNotification", e);
        }
    }

    public boolean isCurrent() {
        Instant now = Instant.now();

        return timestamp.compareTo(now) <= 0 && expiration.compareTo(now) >= 0;
    }

    public boolean isAuthentic() {
        if (signature == null) {
            return false;
        }

        ECKey publicKey = getSender().getPublicKey();
        ECDSASignature signature = getSignature();
        byte[] hash = getHashForSignature();

        return publicKey.verify(hash, signature);
    }
}
