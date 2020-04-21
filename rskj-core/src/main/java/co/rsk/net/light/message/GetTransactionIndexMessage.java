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

public class GetTransactionIndexMessage extends LightClientMessage {

    private final long id;
    private final byte[] txHash;

    public GetTransactionIndexMessage(long id, byte[] txHash) {
        this.id = id;
        this.txHash = txHash.clone();
        this.code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
    }

    public GetTransactionIndexMessage(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        txHash = paramsList.get(1).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
    }

    public long getId() {
        return this.id;
    }

    public byte[] getTxHash() {
        return this.txHash.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpHash = RLP.encodeElement(this.txHash);
        return RLP.encodeList(rlpId, rlpHash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return TransactionIndexMessage.class;
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
