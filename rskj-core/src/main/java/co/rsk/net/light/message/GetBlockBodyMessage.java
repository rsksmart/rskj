/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message;


import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.light.MessageVisitor;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetBlockBodyMessage extends LightClientMessage {

    private final long id;
    private final byte[] blockHash;

    public GetBlockBodyMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        blockHash = list.get(1).getRLPData();

        this.code = LightClientMessageCodes.GET_BLOCK_BODY.asByte();
    }

    public GetBlockBodyMessage(long id, byte[] blockHash) {
        this.id = id;
        this.blockHash = blockHash.clone();

        this.code = LightClientMessageCodes.GET_BLOCK_BODY.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpHash = RLP.encodeElement(getBlockHash());
        return RLP.encodeList(rlpId, rlpHash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return BlockBodyMessage.class;
    }

    @Override
    public String toString() {
        return "";
    }

    public long getId() {
        return id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
