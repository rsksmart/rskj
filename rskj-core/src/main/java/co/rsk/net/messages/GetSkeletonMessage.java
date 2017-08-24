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

/**
 * Wrapper around an RSK GetSkeleton message.
 */
public class GetSkeletonMessage extends Message {
    private byte[] hash_start;
    private byte[] hash_end;

    public GetSkeletonMessage(byte[] hash_start, byte[] hash_end) {
        this.hash_start = hash_start;
        this.hash_end = hash_end;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_SKELETON_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] hash_start = RLP.encodeElement(this.hash_start);
        byte[] hash_end = RLP.encodeElement(this.hash_end);

        return RLP.encodeList(hash_start, hash_end);
    }

    public byte[] getHashStart() {
        return this.hash_start;
    }

    public byte[] getHashEnd() {
        return this.hash_end;
    }
}
