/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

public class SnapStateChunkV2RequestMessage extends MessageWithId {

    private final long id;

    private final byte[] blockHash;

    private final byte[] fromKey;

    public SnapStateChunkV2RequestMessage(long id, byte[] blockHash, byte[] fromKey) {
        this.id = id;
        this.blockHash = blockHash;
        this.fromKey = fromKey;
    }

    @Override
    public long getId() {
        return this.id;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public byte[] getFromKey() {
        return fromKey;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpFromKey = RLP.encodeElement(this.fromKey);
        return RLP.encodeList(rlpBlockHash, rlpFromKey);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATE_CHUNK_V2_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.SNAP_STATE_CHUNK_V2_RESPONSE_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }

    public static Message decodeMessage(RLPList list) {
        byte[] rlpId = list.get(0).getRLPData();

        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
        byte[] rlpBlockHash = message.get(0).getRLPData();
        byte[] rlpFromKey = message.get(1).getRLPData();

        return new SnapStateChunkV2RequestMessage(id, rlpBlockHash, rlpFromKey);
    }
}
