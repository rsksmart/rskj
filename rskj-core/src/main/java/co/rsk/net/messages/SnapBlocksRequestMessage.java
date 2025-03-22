/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public class SnapBlocksRequestMessage extends MessageWithId {
    private final long id;

    private final long blockNumber;

    public SnapBlocksRequestMessage(long id, long blockNumber) {
        this.id = id;
        this.blockNumber = blockNumber;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_BLOCKS_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE;
    }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[] encodedBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(blockNumber));
        return RLP.encodeList(encodedBlockNumber);
    }

    public static Message decodeMessage(RLPList list) {
        byte[] rlpId = list.get(0).getRLPData();
        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
        byte[] rlpBlockNumber = message.get(0).getRLPData();
        long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();

        return new SnapBlocksRequestMessage(id, blockNumber);
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
