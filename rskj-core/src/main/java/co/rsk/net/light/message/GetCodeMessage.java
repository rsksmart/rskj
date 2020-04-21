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

import co.rsk.net.light.MessageVisitor;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.GET_CODE;

public class GetCodeMessage extends LightClientMessage {

    private final long id;
    private final byte[] blockHash;
    private final byte[] address;

    public GetCodeMessage(long id, byte[] blockHash, byte[] address) {
        this.id = id;
        this.blockHash = blockHash.clone();
        this.address = address.clone();
        this.code = GET_CODE.asByte();
    }

    public GetCodeMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.blockHash = list.get(1).getRLPData();
        this.address = list.get(2).getRLPData();
        this.code = GET_CODE.asByte();
    }

    public long getId() {
        return id;
    }

    public byte[] getAddress() {
        return address.clone();
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpBlockHash = RLP.encodeElement(getBlockHash());
        byte[] rlpAddress = RLP.encodeElement(getAddress());
        return RLP.encodeList(rlpId, rlpBlockHash, rlpAddress);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return CodeMessage.class;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
