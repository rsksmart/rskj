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
package co.rsk.net.messages;

import org.ethereum.core.BlockIdentifier;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around an RSK Skeleton message.
 */
public class SkeletonMessage extends Message {

    private long id;
    private List<BlockIdentifier> blockIdentifiers;

    public SkeletonMessage(long id, List<BlockIdentifier> blockIdentifiers) {
        this.id = id;
        this.blockIdentifiers = blockIdentifiers;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SKELETON_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(this.id));
        List<byte[]> encodedElements = new ArrayList<>();
        for (BlockIdentifier identifier : blockIdentifiers)
            encodedElements.add(identifier.getEncoded());
        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);
        return RLP.encodeList(rlpId, RLP.encodeList(encodedElementArray));
    }

    public long getId() {
        return this.id;
    }

    public List<BlockIdentifier> getBlockIdentifiers() {
        return blockIdentifiers;
    }

}
