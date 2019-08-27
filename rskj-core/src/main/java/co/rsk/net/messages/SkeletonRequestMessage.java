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

package co.rsk.net.messages;

import org.ethereum.util.RLP;

import java.math.BigInteger;

/**
 * Wrapper around an RSK GetSkeleton message.
 */
public class SkeletonRequestMessage extends MessageWithId {
    private long id;
    private long startNumber;

    public SkeletonRequestMessage(long id, long startNumber) {
        this.id = id;
        this.startNumber = startNumber;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SKELETON_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.SKELETON_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpStartNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.startNumber));
        return RLP.encodeList(rlpStartNumber);
    }

    public long getId() { return this.id; }

    public long getStartNumber() {
        return this.startNumber;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
