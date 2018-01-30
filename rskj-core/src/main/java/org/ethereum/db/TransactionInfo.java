/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.db;

import co.rsk.core.commons.Keccak256;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.util.ArrayList;

/**
 * Created by Ruben on 8/1/2016.
 */
public class TransactionInfo {

    TransactionReceipt receipt;
    Keccak256 blockHash;
    int index;

    public TransactionInfo(TransactionReceipt receipt, Keccak256 blockHash, int index) {
        this.receipt = receipt;
        this.blockHash = blockHash;
        this.index = index;
    }

    public TransactionInfo(byte[] rlp) {
        ArrayList<RLPElement> params = RLP.decode2(rlp);
        RLPList txInfo = (RLPList) params.get(0);
        RLPList receiptRLP = (RLPList) txInfo.get(0);
        RLPItem blockHashRLP  = (RLPItem) txInfo.get(1);
        RLPItem indexRLP = (RLPItem) txInfo.get(2);

        receipt = new TransactionReceipt(receiptRLP.getRLPData());
        blockHash = new Keccak256(blockHashRLP.getRLPData());
        if (indexRLP.getRLPData() == null) {
            index = 0;
        } else {
            index = BigIntegers.fromUnsignedByteArray(indexRLP.getRLPData()).intValue();
        }
    }

    public void setTransaction(Transaction tx){
        receipt.setTransaction(tx);
    }

    /* [receipt, blockHash, index] */
    public byte[] getEncoded() {

        byte[] receiptRLP = this.receipt.getEncoded();
        byte[] blockHashRLP = RLP.encodeElement(blockHash.getBytes());
        byte[] indexRLP = RLP.encodeInt(index);

        byte[] rlpEncoded = RLP.encodeList(receiptRLP, blockHashRLP, indexRLP);

        return rlpEncoded;
    }

    public TransactionReceipt getReceipt(){
        return receipt;
    }

    public Keccak256 getBlockHash() { return blockHash; }

    public int getIndex() { return index; }
}
