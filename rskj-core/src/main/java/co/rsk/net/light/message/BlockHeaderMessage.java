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
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class BlockHeaderMessage extends LightClientMessage {

    private long id;
    private BlockHeader blockHeader;

    public BlockHeaderMessage(long id, BlockHeader blockHeader) {
        this.id = id;
        this.blockHeader = blockHeader;
        this.code = LightClientMessageCodes.BLOCK_HEADER.asByte();
    }

    public BlockHeaderMessage(byte[] encoded, BlockFactory blockFactory) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        blockHeader = blockFactory.decodeHeader(paramsList.get(1).getRLPData());
        this.code = LightClientMessageCodes.BLOCK_HEADER.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpBlockHeader = RLP.encodeElement(blockHeader.getFullEncoded());

        return RLP.encodeList(rlpId, rlpBlockHeader);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    public BlockHeader getBlockHeader() {
        return blockHeader;
    }

    public long getId() {
        return id;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
