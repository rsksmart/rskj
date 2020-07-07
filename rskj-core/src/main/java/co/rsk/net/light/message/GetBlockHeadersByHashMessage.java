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
import co.rsk.net.light.LightClientMessageVisitor;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.util.ByteUtil.toHexString;

public class GetBlockHeadersByHashMessage extends GetBlockHeadersMessage {

    private final byte[] startBlockHash;

    public GetBlockHeadersByHashMessage(long id, byte[] startBlockHash, int maxAmountOfHeaders, int skip, boolean reverse) {
        super(id, maxAmountOfHeaders, skip, reverse);
        this.startBlockHash = startBlockHash.clone();
        this.code = LightClientMessageCodes.GET_BLOCK_HEADER_BY_HASH.asByte();
    }

    public GetBlockHeadersByHashMessage(byte[] encoded) {
        super(encoded);
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        this.startBlockHash = paramsList.get(1).getRLPData();
        this.code = LightClientMessageCodes.GET_BLOCK_HEADER_BY_HASH.asByte();
    }

    public byte[] getStartBlockHash() {
        return startBlockHash.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpHash = RLP.encodeElement(getStartBlockHash());
        byte[] rlpMax = RLP.encodeBigInteger(BigInteger.valueOf(getMaxAmountOfHeaders()));
        byte[] rlpSkip = RLP.encodeBigInteger(BigInteger.valueOf(getSkip()));
        byte[] rlpReverse = RLP.encodeByte((byte)(isReverse() ? 0x01 : 0x00));

        return RLP.encodeList(rlpId, rlpHash, rlpMax, rlpSkip, rlpReverse);
    }


    @Override
    public String toString() {
        return "GetBlockHeaderMessage{" +
                "\nid= " + getId() +
                "\nblockHash= " + toHexString(getStartBlockHash()) +
                "\nmax= " + getMaxAmountOfHeaders() +
                "\nskip= " + getSkip() +
                "\nreverse= " + isReverse() +
                "\n}";
    }

    @Override
    public void accept(LightClientMessageVisitor v) {
        v.apply(this);
    }
}
