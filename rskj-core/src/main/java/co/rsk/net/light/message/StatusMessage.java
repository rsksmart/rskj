/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.net.light.LightStatus;
import co.rsk.net.light.LightClientMessageVisitor;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.STATUS;
import static org.ethereum.util.ByteUtil.toHexString;

public class StatusMessage extends LightClientMessage {


    private final long id;
    private final LightStatus status;
    private boolean txRelay; // 1 byte - 0x00 = false or 0x01 = true

    public StatusMessage(long id, LightStatus status, boolean txRelay) {
        this.id = id;
        this.status = status;
        this.txRelay = txRelay;
        this.code = STATUS.asByte();
    }

    public StatusMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.status = new LightStatus(list.get(1).getRLPData());
        byte[] rlpTxRelay = list.get(2).getRLPData();
        this.txRelay = rlpTxRelay != null;
        this.code = STATUS.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpStatus = getStatus().getEncoded();
        byte[] rlpTxRelay = RLP.encodeByte((byte)(isTxRelay() ? 0x01 : 0x00));
        return RLP.encodeList(rlpId, rlpStatus, rlpTxRelay);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "StatusMessage {" +
                "\nid= " + getId() +
                "\nstatus= " + getStatus().toString() +
                "\n}";
    }

    @Override
    public LightClientMessageCodes getCommand() {
        return STATUS;
    }

    public LightStatus getStatus() {
        return status;
    }

    public long getId() {
        return id;
    }

    public boolean isTxRelay() {
        return txRelay;
    }

    @Override
    public void accept(LightClientMessageVisitor v) {
        v.apply(this);
    }
}
