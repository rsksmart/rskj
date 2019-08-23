/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.rpc.dto;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.rpc.TypeConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

public class BlockResultDTO {
    private final String number; // QUANTITY - the block number. null when its pending block.
    private final String hash; // DATA, 32 Bytes - hash of the block. null when its pending block.
    private final String parentHash; // DATA, 32 Bytes - hash of the parent block.
    private final String sha3Uncles; // DATA, 32 Bytes - SHA3 of the uncles data in the block.
    private final String logsBloom; // DATA, 256 Bytes - the bloom filter for the logs of the block. null when its pending block.
    private final String transactionsRoot; // DATA, 32 Bytes - the root of the transaction trie of the block.
    private final String stateRoot; // DATA, 32 Bytes - the root of the final state trie of the block.
    private final String receiptsRoot; // DATA, 32 Bytes - the root of the receipts trie of the block.
    private final String miner; // DATA, 20 Bytes - the address of the beneficiary to whom the mining rewards were given.
    private final String difficulty; // QUANTITY - integer of the difficulty for this block.
    private final String totalDifficulty; // QUANTITY - integer of the total difficulty of the chain until this block.
    private final String extraData; // DATA - the "extra data" field of this block
    private final String size;//QUANTITY - integer the size of this block in bytes.
    private final String gasLimit;//: QUANTITY - the maximum gas allowed in this block.
    private final String gasUsed; // QUANTITY - the total used gas by all transactions in this block.
    private final String timestamp; //: QUANTITY - the unix timestamp for when the block was collated.
    private final List<Object> transactions; //: Collection - Collection of transaction objects, or 32 Bytes transaction hashes depending on the last given parameter.
    private final List<String> uncles; //: Collection - Collection of uncle hashes.
    private final String minimumGasPrice;
    private final String bitcoinMergedMiningHeader;
    private final String bitcoinMergedMiningCoinbaseTransaction;
    private final String bitcoinMergedMiningMerkleProof;
    private final String hashForMergedMining;

    public BlockResultDTO(
            Long number,
            Keccak256 hash,
            Keccak256 parentHash,
            byte[] sha3Uncles,
            byte[] logsBloom,
            byte[] transactionsRoot,
            byte[] stateRoot,
            byte[] receiptsRoot,
            RskAddress miner,
            BlockDifficulty difficulty,
            BlockDifficulty totalDifficulty,
            byte[] extraData,
            int size,
            byte[] gasLimit,
            long gasUsed,
            long timestamp,
            List<Object> transactions,
            List<String> uncles,
            Coin minimumGasPrice,
            byte[] bitcoinMergedMiningHeader,
            byte[] bitcoinMergedMiningCoinbaseTransaction,
            byte[] bitcoinMergedMiningMerkleProof,
            byte[] hashForMergedMining) {
        this.number = number != null ? TypeConverter.toJsonHex(number) : null;
        this.hash = hash != null ? TypeConverter.toJsonHex(hash.getBytes()) : null;
        this.parentHash = TypeConverter.toJsonHex(parentHash.getBytes());
        this.sha3Uncles = TypeConverter.toJsonHex(sha3Uncles);
        this.logsBloom = logsBloom != null ? TypeConverter.toJsonHex(logsBloom) : null;
        this.transactionsRoot = TypeConverter.toJsonHex(transactionsRoot);
        this.stateRoot = TypeConverter.toJsonHex(stateRoot);
        this.receiptsRoot = TypeConverter.toJsonHex(receiptsRoot);
        this.miner = miner != null ? TypeConverter.toJsonHex(miner.getBytes()) : null;
        this.difficulty = TypeConverter.toJsonHex(difficulty.getBytes());

        this.totalDifficulty = TypeConverter.toJsonHex(totalDifficulty.getBytes());
        this.extraData = TypeConverter.toJsonHex(extraData);
        this.size = TypeConverter.toJsonHex(size);
        this.gasLimit = TypeConverter.toJsonHex(gasLimit);
        this.gasUsed = TypeConverter.toJsonHex(gasUsed);
        this.timestamp = TypeConverter.toJsonHex(timestamp);

        this.transactions = Collections.unmodifiableList(transactions);
        this.uncles = Collections.unmodifiableList(uncles);

        this.minimumGasPrice = minimumGasPrice != null ? TypeConverter.toJsonHex(minimumGasPrice.getBytes()) : null;
        this.bitcoinMergedMiningHeader = TypeConverter.toJsonHex(bitcoinMergedMiningHeader);
        this.bitcoinMergedMiningCoinbaseTransaction = TypeConverter.toJsonHex(bitcoinMergedMiningCoinbaseTransaction);
        this.bitcoinMergedMiningMerkleProof = TypeConverter.toJsonHex(bitcoinMergedMiningMerkleProof);
        this.hashForMergedMining = TypeConverter.toJsonHex(hashForMergedMining);
    }

    public static BlockResultDTO fromBlock(Block b, boolean fullTx, BlockStore blockStore) {
        if (b == null) {
            return null;
        }

        byte[] mergeHeader = b.getBitcoinMergedMiningHeader();
        boolean isPending = (mergeHeader == null || mergeHeader.length == 0) && !b.isGenesis();

        Coin mgp = b.getMinimumGasPrice();

        List<Object> transactions = new ArrayList<>();
        List<Transaction> blockTransactions = b.getTransactionsList();
        if (fullTx) {
            for (int i = 0; i < blockTransactions.size(); i++) {
                transactions.add(new TransactionResultDTO(b, i, blockTransactions.get(i)));
            }
        } else {
            for (Transaction tx : blockTransactions) {
                transactions.add(tx.getHash().toJsonString());
            }
        }

        List<String> uncles = new ArrayList<>();

        for (BlockHeader header : b.getUncleList()) {
            uncles.add(toJsonHex(header.getHash().getBytes()));
        }

        return new BlockResultDTO(
                isPending ? null : b.getNumber(),
                isPending ? null : b.getHash(),
                b.getParentHash(),
                b.getUnclesHash(),
                isPending ? null : b.getLogBloom(),
                b.getTxTrieRoot(),
                b.getStateRoot(),
                b.getReceiptsRoot(),
                isPending ? null : b.getCoinbase(),
                b.getDifficulty(),
                blockStore.getTotalDifficultyForHash(b.getHash().getBytes()),
                b.getExtraData(),
                b.getEncoded().length,
                b.getGasLimit(),
                b.getGasUsed(),
                b.getTimestamp(),
                transactions,
                uncles,
                mgp,
                b.getBitcoinMergedMiningHeader(),
                b.getBitcoinMergedMiningCoinbaseTransaction(),
                b.getBitcoinMergedMiningMerkleProof(),
                b.getHashForMergedMining()
        );
    }

    public String getNumber() {
        return number;
    }

    public String getHash() {
        return hash;
    }

    public String getParentHash() {
        return parentHash;
    }

    public String getSha3Uncles() {
        return sha3Uncles;
    }

    public String getLogsBloom() {
        return logsBloom;
    }

    public String getTransactionsRoot() {
        return transactionsRoot;
    }

    public String getStateRoot() {
        return stateRoot;
    }

    public String getReceiptsRoot() {
        return receiptsRoot;
    }

    public String getMiner() {
        return miner;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getTotalDifficulty() {
        return totalDifficulty;
    }

    public String getExtraData() {
        return extraData;
    }

    public String getSize() {
        return size;
    }

    public String getGasLimit() {
        return gasLimit;
    }

    public String getGasUsed() {
        return gasUsed;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public List<Object> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public List<String> getUncles() { return Collections.unmodifiableList(uncles); }

    public String getMinimumGasPrice() {
        return minimumGasPrice;
    }

    public String getBitcoinMergedMiningHeader() {
        return bitcoinMergedMiningHeader;
    }

    public String getBitcoinMergedMiningCoinbaseTransaction() {
        return bitcoinMergedMiningCoinbaseTransaction;
    }

    public String getBitcoinMergedMiningMerkleProof() {
        return bitcoinMergedMiningMerkleProof;
    }

    public String getHashForMergedMining() {
        return hashForMergedMining;
    }
}

