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

import co.rsk.trie.TrieChunk;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class SnapStateChunkV2ResponseMessage extends MessageWithId {

    private final long id;

    private final TrieChunk chunk;

    public SnapStateChunkV2ResponseMessage(long id, TrieChunk chunk) {
        this.id = id;
        this.chunk = chunk;
    }

    @Override
    public long getId() {
        return this.id;
    }

    public TrieChunk getChunk() {
        return chunk;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        return chunk.encode();
    }

    @Override
    public MessageType getMessageType() {
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

        TrieChunk chunk = TrieChunk.decode(message);

        return new SnapStateChunkV2ResponseMessage(id, chunk);
    }
}
