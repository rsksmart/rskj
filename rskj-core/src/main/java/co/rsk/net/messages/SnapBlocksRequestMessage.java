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
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public class SnapBlocksRequestMessage extends Message {
    private final long blockNumber;

    public SnapBlocksRequestMessage(long blockNumber) {
        this.blockNumber = blockNumber;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_BLOCKS_REQUEST_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] encodedBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(blockNumber));
        return RLP.encodeList(encodedBlockNumber);
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        byte[] rlpBlockNumber = list.get(0).getRLPData();

        long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();

        return new SnapBlocksRequestMessage(blockNumber);
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
