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

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class BlockReceiptsMessage extends LightClientMessage {

    /**
     * Id to identify request/response correlation
     */
    private long id;

    /**
     * List of receipts from the block
     */
    private List<TransactionReceipt> blockReceipts;

    public BlockReceiptsMessage(long requestId, List<TransactionReceipt> receipts) {
        this.id = requestId;
        this.blockReceipts = receipts;
    }

    public BlockReceiptsMessage(byte[] encoded) {

        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList rlpReceipts = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
        List<TransactionReceipt> receipts = new LinkedList<>();
        for (int k = 0; k < rlpReceipts.size(); k++) {
            byte[] rpData = rlpReceipts.get(k).getRLPData();
            TransactionReceipt rp = new TransactionReceipt(rpData);
            receipts.add(rp);
        }

        this.blockReceipts = receipts;
    }

    public List<TransactionReceipt> getBlockReceipts() { return new ArrayList<>(blockReceipts); }

    public long getId() {
        return this.id;
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[][] rlpReceipts = new byte[this.blockReceipts.size()][];

        for (int k = 0; k < this.blockReceipts.size(); k++) {
            rlpReceipts[k] = this.blockReceipts.get(k).getEncoded();
        }

        return RLP.encodeList(rlpId, RLP.encodeList(rlpReceipts));
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }
}
