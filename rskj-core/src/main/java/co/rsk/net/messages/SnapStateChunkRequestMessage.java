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

public class SnapStateChunkRequestMessage extends MessageWithId {
    private final long id;
    private final long from;
    private final long blockNumber;

    private final long chunkSize; // TODO: remove this field in the future, when it is not used anymore

    public SnapStateChunkRequestMessage(long id, long blockNumber, long from, long chunkSize) {
        this.id = id;
        this.from = from;
        this.blockNumber = blockNumber;
        this.chunkSize = chunkSize;
    }

    public SnapStateChunkRequestMessage(long id, long blockNumber, long from) {
        this.id = id;
        this.from = from;
        this.blockNumber = blockNumber;
        this.chunkSize = 0;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATE_CHUNK_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.SNAP_STATE_CHUNK_RESPONSE_MESSAGE;
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
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpFrom = RLP.encodeBigInteger(BigInteger.valueOf(this.from));
        byte[] rlpChunkSize = RLP.encodeBigInteger(BigInteger.valueOf(this.chunkSize));
        return RLP.encodeList(rlpBlockNumber, rlpFrom, rlpChunkSize);
    }

    public static Message decodeMessage(RLPList list) {
        byte[] rlpId = list.get(0).getRLPData();
        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
        byte[] rlpBlockNumber = message.get(0).getRLPData();
        byte[] rlpFrom = message.get(1).getRLPData();
        long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();
        long from = rlpFrom == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpFrom).longValue();
        return new SnapStateChunkRequestMessage(id, blockNumber, from);
    }

    public long getFrom() {
        return from;
    }

    @SuppressWarnings("unused")
    public long getChunkSize() {
        return chunkSize;
    }

    public long getBlockNumber() {
        return blockNumber;
    }
}
