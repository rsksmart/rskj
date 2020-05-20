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
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BlockHeadersMessage extends LightClientMessage {

    private long id;
    private List<BlockHeader> blockHeaders;

    public BlockHeadersMessage(long id, List<BlockHeader> blockHeaders) {
        this.id = id;
        this.blockHeaders = new ArrayList<>(blockHeaders);
        this.code = LightClientMessageCodes.BLOCK_HEADER.asByte();
    }

    public BlockHeadersMessage(byte[] encoded, BlockFactory blockFactory) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList rlpBlockHeaders = (RLPList)RLP.decode2(paramsList.get(1).getRLPData()).get(0);
        List<BlockHeader> headers = new ArrayList<>();
        
        for (int k = 0; k < rlpBlockHeaders.size(); k++) {
            RLPElement element = rlpBlockHeaders.get(k);
            BlockHeader header = blockFactory.decodeHeader(element.getRLPData());
            headers.add(header);
        }

        this.blockHeaders = headers;
        this.code = LightClientMessageCodes.BLOCK_HEADER.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[][] rlpBlockHeader = this.blockHeaders.stream()
                .map(BlockHeader::getFullEncoded).map(RLP::encodeElement)
                .toArray(byte[][]::new);

        return RLP.encodeList(rlpId, RLP.encodeList(rlpBlockHeader));
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    public List<BlockHeader> getBlockHeaders() {
        return new ArrayList<>(blockHeaders);
    }

    public long getId() {
        return id;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
