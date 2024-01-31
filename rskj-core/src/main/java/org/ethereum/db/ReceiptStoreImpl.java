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

    private final KeyValueDataSource receiptsDS;

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
    public Optional<TransactionInfo> get(byte[] transactionHash, byte[] blockHash) {
        // it is not guaranteed that there will be only one matching TransactionInfo, but if there were more than one,
        // they would be exactly the same
        return getAll(transactionHash).stream()
                .filter(ti -> Arrays.equals(ti.getBlockHash(), blockHash))
                .findAny();
    }

    @Override
    public Optional<TransactionInfo> getInMainChain(byte[] transactionHash, BlockStore store) {
        List<TransactionInfo> tis = this.getAll(transactionHash);

        if (tis.isEmpty()) {
            return Optional.empty();
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
                return Optional.of(ti);
            }
        }

        return Optional.empty();
    }

    private List<TransactionInfo> getAll(byte[] transactionHash) {
        byte[] txsBytes = receiptsDS.get(transactionHash);

        if (txsBytes == null || txsBytes.length == 0) {
            return new ArrayList<>();
        }

        List<TransactionInfo> txsInfo = new ArrayList<>();
        RLPList txsList = RLP.decodeList(txsBytes);


        int txsListSize = txsList.size();
        // check if list is empty or data format is incorrect
        if (txsListSize == 0 || !(txsList.get(0) instanceof RLPList)) {
            return new ArrayList<>();
        }

        for (int i = 0; i < txsListSize; ++i) {
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

    @Override
    public void close() {
        this.receiptsDS.close();
    }
}
