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

public class SnapStateChunkResponseMessage extends MessageWithId {
    private final long to;
    private final long id;
    private final byte[] chunkOfTrieKeyValue;

    private final long from;

    private final boolean complete;
    private final long blockNumber;

    public SnapStateChunkResponseMessage(long id, byte[] chunkOfTrieKeyValue, long blockNumber, long from, long to, boolean complete) {
        this.id = id;
        this.chunkOfTrieKeyValue = chunkOfTrieKeyValue;
        this.blockNumber = blockNumber;
        this.from = from;
        this.to = to;
        this.complete = complete;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.STATE_CHUNK_RESPONSE_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }

    @Override
    public long getId() {
        return this.id;
    }


    @Override
    protected byte[] getEncodedMessageWithoutId() {
        try {
            byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
            byte[] rlpFrom = RLP.encodeBigInteger(BigInteger.valueOf(this.from));
            byte[] rlpTo = RLP.encodeBigInteger(BigInteger.valueOf(this.to));
            byte[] rlpComplete = new byte[]{this.complete ? (byte) 1 : (byte) 0};
            return RLP.encodeList(chunkOfTrieKeyValue, rlpBlockNumber, rlpFrom, rlpTo, rlpComplete);
        } catch (Exception e) {
            throw e;
        }
    }

    public static Message create(BlockFactory blockFactory, RLPList list) {
        try {
            byte[] rlpId = list.get(0).getRLPData();
            RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] chunkOfTrieKeys = message.get(0).getRLPData();
            byte[] rlpBlockNumber = message.get(1).getRLPData();
            byte[] rlpFrom = message.get(2).getRLPData();
            byte[] rlpTo = message.get(3).getRLPData();
            byte[] rlpComplete = message.get(4).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();
            long from = rlpFrom == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpFrom).longValue();
            long to = rlpTo == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpTo).longValue();
            boolean complete = rlpComplete == null ? Boolean.FALSE : rlpComplete[0] != 0;
            return new SnapStateChunkResponseMessage(id, chunkOfTrieKeys, blockNumber, from, to, complete);
        } catch (Exception e) {
            throw e;
        }
    }

    public byte[] getChunkOfTrieKeyValue() {
        return chunkOfTrieKeyValue;
    }

    public long getFrom() {
        return from;
    }

    public boolean isComplete() {
        return complete;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getTo() {
        return to;
    }
}
