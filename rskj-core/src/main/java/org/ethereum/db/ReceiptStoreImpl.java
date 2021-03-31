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

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by Ruben on 6/1/2016.
 * Class used to store transaction receipts
 */

public class ReceiptStoreImpl implements ReceiptStore {
    private KeyValueDataSource receiptsDS;

    public ReceiptStoreImpl(KeyValueDataSource receiptsDS){
        this.receiptsDS = receiptsDS;
    }

    @Override
    public void add(byte[] blockHash, int transactionIndex, TransactionReceipt receipt){
        byte[] txHash = receipt.getTransaction().getHash().getBytes();

        TransactionInfo newTxInfo = new TransactionInfo(receipt, blockHash, transactionIndex);

        List<TransactionInfo> txsInfo = getAll(txHash);

        txsInfo.add(newTxInfo);

        List<byte[]> encodedTxs = new ArrayList<>();

        for (TransactionInfo ti : txsInfo) {
            encodedTxs.add(ti.getEncoded());
        }

        byte[][] txsBytes = encodedTxs.toArray(new byte[encodedTxs.size()][]);

        receiptsDS.put(receipt.getTransaction().getHash().getBytes(), RLP.encodeList(txsBytes));
    }

    @Override
    public TransactionInfo get(byte[] transactionHash){
        List<TransactionInfo> txs = getAll(transactionHash);

        if (txs.isEmpty()) {
            return null;
        }

        return txs.get(txs.size() - 1);
    }

    @Override
    public Optional<TransactionInfo> get(Keccak256 transactionHash, Keccak256 blockHash) {
        // it is not guaranteed that there will be only one matching TransactionInfo, but if there were more than one,
        // they would be exactly the same
        return getAll(transactionHash.getBytes()).stream()
                .filter(ti -> Arrays.equals(ti.getBlockHash(), blockHash.getBytes()))
                .findAny();
    }

    @Override
    public TransactionInfo getInMainChain(byte[] transactionHash, BlockStore store) {
        List<TransactionInfo> tis = this.getAll(transactionHash);

        if (tis.isEmpty()) {
            return null;
        }

        for (TransactionInfo ti : tis) {
            byte[] bhash = ti.getBlockHash();

            Block block = store.getBlockByHash(bhash);

            if (block == null) {
                continue;
            }

            Block mblock = store.getChainBlockByNumber(block.getNumber());

            if (mblock == null) {
                continue;
            }

            if (Arrays.equals(bhash, mblock.getHash().getBytes())) {
                return ti;
            }
        }

        return null;
    }

    @Override
    public List<TransactionInfo> getAll(byte[] transactionHash) {
        byte[] txsBytes = receiptsDS.get(transactionHash);

        if (txsBytes == null || txsBytes.length == 0) {
            return new ArrayList<TransactionInfo>();
        }

        List<TransactionInfo> txsInfo = new ArrayList<>();
        RLPList txsList = (RLPList) RLP.decode2(txsBytes).get(0);

        for (int i = 0; i < txsList.size(); ++i) {
            RLPList rlpData = ((RLPList) txsList.get(i));
            txsInfo.add(new TransactionInfo(rlpData.getRLPData()));
        }

        return txsInfo;
    }

    @Override
    public void saveMultiple(byte[] blockHash, List<TransactionReceipt> receipts) {
        int i = 0;
        for (TransactionReceipt receipt : receipts) {
            this.add(blockHash, i++, receipt);
        }
    }

    @Override
    public void flush() {
        this.receiptsDS.flush();
    }
}
