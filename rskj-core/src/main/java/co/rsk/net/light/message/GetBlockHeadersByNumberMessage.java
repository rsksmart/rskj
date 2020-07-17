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
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetBlockHeadersByNumberMessage extends GetBlockHeadersMessage {

    private final long startBlockNumber;

    public GetBlockHeadersByNumberMessage(long id, long startBlockNumber, int maxAmountOfheaders, int skip, boolean reverse) {
        super(id, maxAmountOfheaders, skip, reverse);
        this.startBlockNumber = startBlockNumber;
        this.code = LightClientMessageCodes.GET_BLOCK_HEADER_BY_NUMBER.asByte();
    }

    public GetBlockHeadersByNumberMessage(byte[] encoded) {
        super(encoded);
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpBlockNumber = paramsList.get(1).getRLPData();
        this.startBlockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();
        this.code = LightClientMessageCodes.GET_BLOCK_HEADER_BY_NUMBER.asByte();
    }

    public long getStartBlockNumber() {
        return startBlockNumber;
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpMax = RLP.encodeBigInteger(BigInteger.valueOf(getMaxAmountOfHeaders()));
        byte[] rlpSkip = RLP.encodeBigInteger(BigInteger.valueOf(getSkip()));
        byte[] rlpReverse = RLP.encodeByte((byte)(isReverse() ? 0x01 : 0x00));
        byte[] rlpNumber = RLP.encodeBigInteger(BigInteger.valueOf(getStartBlockNumber()));

        return RLP.encodeList(rlpId, rlpNumber, rlpMax, rlpSkip, rlpReverse);
    }

    @Override
    public String toString() {
        return "GetBlockHeaderMessage{" +
                "\nid= " + getId() +
                "\nblockNumber= " + getStartBlockNumber() +
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
