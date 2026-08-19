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

import java.util.*;

public class ReceiptStoreImplV2 extends ReceiptStoreImpl {

    private final KeyValueDataSource receiptsDS;

    public ReceiptStoreImplV2(KeyValueDataSource receiptsDS) {
        super(receiptsDS);

        this.receiptsDS = receiptsDS;
    }

    @Override
    public void add(byte[] blockHash, int transactionIndex, TransactionReceipt receipt) {
        byte[] txHash = receipt.getTransaction().getHash().getBytes();

        // try a new data format first
        byte[] txInfoBytes = receiptsDS.get(txHash);

        RLPList txList = null;
        int txListSize = 0;
        if (txInfoBytes != null && txInfoBytes.length > 0) {
            txList = (RLPList) RLP.decode2(txInfoBytes).get(0);
            txListSize = txList.size();
        }

        if (txListSize > 0 && txList.get(0) instanceof RLPList) { // old data format
            super.add(blockHash, transactionIndex, receipt);
        } else {
            // save tx receipt data as a separate item first
            byte[] key = getCombinedKey(txHash, blockHash);
            TransactionInfo newTxInfo = new TransactionInfo(receipt, blockHash, transactionIndex);
            receiptsDS.put(key, newTxInfo.getEncoded());

            // add block hash to the list
            byte[][] blockHashArr = new byte[txListSize + 1][];
            for (int i = 0; i < txListSize; ++i) {
                blockHashArr[i] = RLP.encodeElement(txList.get(i).getRLPData());
            }

            blockHashArr[txListSize] = RLP.encodeElement(blockHash);

            receiptsDS.put(txHash, RLP.encodeList(blockHashArr));
        }
    }

    @Override
    public Optional<TransactionInfo> get(byte[] transactionHash, byte[] blockHash) {
        // try first a new data format
        Optional<TransactionInfo> txInfoOpt = getInternal(transactionHash, blockHash);
        if (txInfoOpt.isPresent()) {
            return txInfoOpt;
        }

        // fallback with older data format
        return super.get(transactionHash, blockHash);
    }

    @Override
    public void saveMultiple(byte[] blockHash, List<TransactionReceipt> receipts) {
        if (receipts.isEmpty()) {
            return;
        }

        // Preserve tx order for deterministic tx index assignment.
        Map<ByteArrayWrapper, List<TransactionInfo>> txInfosByHash = new LinkedHashMap<>();
        int txIndex = 0;
        for (TransactionReceipt receipt : receipts) {
            byte[] txHash = receipt.getTransaction().getHash().getBytes();
            ByteArrayWrapper txHashWrapper = new ByteArrayWrapper(txHash);
            txInfosByHash.computeIfAbsent(txHashWrapper, ignored -> new ArrayList<>())
                    .add(new TransactionInfo(receipt, blockHash, txIndex++));
        }

        Map<ByteArrayWrapper, byte[]> entriesToUpdate = new HashMap<>();
        List<TransactionInfo> legacyFormatEntries = new ArrayList<>();

        for (Map.Entry<ByteArrayWrapper, List<TransactionInfo>> txInfoEntry : txInfosByHash.entrySet()) {
            byte[] txHash = txInfoEntry.getKey().getData();
            byte[] txInfoBytes = receiptsDS.get(txHash);

            RLPList txList = null;
            int txListSize = 0;
            if (txInfoBytes != null && txInfoBytes.length > 0) {
                txList = (RLPList) RLP.decode2(txInfoBytes).get(0);
                txListSize = txList.size();
            }

            if (txListSize > 0 && txList.get(0) instanceof RLPList) {
                legacyFormatEntries.addAll(txInfoEntry.getValue());
                continue;
            }

            List<TransactionInfo> transactionInfos = txInfoEntry.getValue();
            for (TransactionInfo txInfo : transactionInfos) {
                entriesToUpdate.put(
                        new ByteArrayWrapper(getCombinedKey(txHash, txInfo.getBlockHash())),
                        txInfo.getEncoded()
                );
            }

            byte[][] blockHashArr = new byte[txListSize + transactionInfos.size()][];
            for (int i = 0; i < txListSize; ++i) {
                blockHashArr[i] = RLP.encodeElement(txList.get(i).getRLPData());
            }
            for (int i = 0; i < transactionInfos.size(); i++) {
                blockHashArr[txListSize + i] = RLP.encodeElement(transactionInfos.get(i).getBlockHash());
            }

            entriesToUpdate.put(new ByteArrayWrapper(txHash), RLP.encodeList(blockHashArr));
        }

        if (!entriesToUpdate.isEmpty()) {
            receiptsDS.updateBatch(entriesToUpdate, Collections.emptySet());
        }

        for (TransactionInfo txInfo : legacyFormatEntries) {
            super.add(txInfo.getBlockHash(), txInfo.getIndex(), txInfo.getReceipt());
        }
    }

    @Override
    public Optional<TransactionInfo> getInMainChain(byte[] transactionHash, BlockStore store) {
        // try first a new data format
        byte[] txInfoBytes = receiptsDS.get(transactionHash);
        List<byte[]> blockHashList = parseBlockHashList(txInfoBytes);
        if (!blockHashList.isEmpty()) {
            for (byte[] blockHash : blockHashList) {
                Block block = store.getBlockByHash(blockHash);

                if (block == null) {
                    continue;
                }

                Block mblock = store.getChainBlockByNumber(block.getNumber());

                if (mblock == null) {
                    continue;
                }

                if (Arrays.equals(blockHash, mblock.getHash().getBytes())) {
                    return getInternal(transactionHash, blockHash);
                }
            }

            return Optional.empty();
        }

        // fallback with older data format
        return super.getInMainChain(transactionHash, store);
    }

    private Optional<TransactionInfo> getInternal(byte[] transactionHash, byte[] blockHash) {
        byte[] key = getCombinedKey(transactionHash, blockHash);
        byte[] txInfoBytes = receiptsDS.get(key);
        if (txInfoBytes != null) {
            return Optional.of(new TransactionInfo(txInfoBytes));
        }

        return Optional.empty();
    }

    private static List<byte[]> parseBlockHashList(byte[] txsBytes) {
        if (txsBytes == null || txsBytes.length == 0) {
            return Collections.emptyList();
        }

        RLPList txHashList = (RLPList) RLP.decode2(txsBytes).get(0);
        if (txHashList.size() == 0 || txHashList.get(0) instanceof RLPList) {
            return Collections.emptyList();
        }

        ArrayList<byte[]> result = new ArrayList<>(txHashList.size());
        for (int i = 0; i < txHashList.size(); i++) {
            result.add(txHashList.get(i).getRLPData());
        }

        return result;
    }

    private static byte[] getCombinedKey(byte[] txHash, byte[] blockHash) {
        byte[] key = new byte[blockHash.length + txHash.length];

        System.arraycopy(txHash, 0, key, 0, txHash.length);
        System.arraycopy(blockHash, 0, key, txHash.length, blockHash.length);

        return key;
    }
}
