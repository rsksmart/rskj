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

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;


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
    private final String paidFees;
    private final String cumulativeDifficulty;

    private BlockResultDTO(
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
            BlockDifficulty cumulativeDifficulty,
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
            byte[] hashForMergedMining,
            Coin paidFees) {
        this.number = number != null ? HexUtils.toQuantityJsonHex(number) : null;
        this.hash = hash != null ? hash.toJsonString() : null;
        this.parentHash = parentHash.toJsonString();
        this.sha3Uncles = HexUtils.toUnformattedJsonHex(sha3Uncles);
        this.logsBloom = logsBloom != null ? HexUtils.toUnformattedJsonHex(logsBloom) : null;
        this.transactionsRoot = HexUtils.toUnformattedJsonHex(transactionsRoot);
        this.stateRoot = HexUtils.toUnformattedJsonHex(stateRoot);
        this.receiptsRoot = HexUtils.toUnformattedJsonHex(receiptsRoot);
        this.miner = miner != null ? HexUtils.toUnformattedJsonHex(miner.getBytes()) : null;

        this.difficulty = HexUtils.toQuantityJsonHex(difficulty.getBytes());
        this.totalDifficulty = HexUtils.toQuantityJsonHex(totalDifficulty.getBytes());
        this.cumulativeDifficulty = HexUtils.toQuantityJsonHex(cumulativeDifficulty.getBytes());

        this.extraData = HexUtils.toUnformattedJsonHex(extraData);
        this.size = HexUtils.toQuantityJsonHex(size);
        this.gasLimit = HexUtils.toQuantityJsonHex(gasLimit);
        this.gasUsed = HexUtils.toQuantityJsonHex(gasUsed);
        this.timestamp = HexUtils.toQuantityJsonHex(timestamp);

        this.transactions = Collections.unmodifiableList(transactions);
        this.uncles = Collections.unmodifiableList(uncles);

        this.minimumGasPrice = minimumGasPrice != null ? HexUtils.toQuantityJsonHex(minimumGasPrice.getBytes()) : null;
        this.bitcoinMergedMiningHeader = HexUtils.toUnformattedJsonHex(bitcoinMergedMiningHeader);
        this.bitcoinMergedMiningCoinbaseTransaction = HexUtils.toUnformattedJsonHex(bitcoinMergedMiningCoinbaseTransaction);
        this.bitcoinMergedMiningMerkleProof = HexUtils.toUnformattedJsonHex(bitcoinMergedMiningMerkleProof);
        this.hashForMergedMining = HexUtils.toUnformattedJsonHex(hashForMergedMining);
        this.paidFees = paidFees != null ? HexUtils.toQuantityJsonHex(paidFees.getBytes()) : null;
    }

    public static BlockResultDTO fromBlock(Block b, boolean fullTx, BlockStore blockStore, boolean skipRemasc) {
        if (b == null) {
            return null;
        }

        byte[] mergeHeader = b.getBitcoinMergedMiningHeader();
        boolean isPending = (mergeHeader == null || mergeHeader.length == 0) && !b.isGenesis();

        Coin mgp = b.getMinimumGasPrice();

        List<Transaction> blockTransactions = b.getTransactionsList();
        // For full tx will present as TransactionResultDTO otherwise just as transaction hash
        List<Object> transactions = IntStream.range(0, blockTransactions.size())
                .mapToObj(txIndex -> toTransactionResult(txIndex, b, fullTx, skipRemasc))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> uncles = new ArrayList<>();

        for (BlockHeader header : b.getUncleList()) {
            uncles.add(header.getHash().toJsonString());
        }

        // Useful for geth integration
        byte[] transactionsRoot = skipRemasc &&
                b.getTransactionsList().size() == 1 &&
                b.getTransactionsList().get(0).isRemascTransaction(0,1) ?
                    EMPTY_TRIE_HASH :
                    b.getTxTrieRoot();

        return new BlockResultDTO(
                isPending ? null : b.getNumber(),
                isPending ? null : b.getHash(),
                b.getParentHash(),
                b.getUnclesHash(),
                isPending ? null : b.getLogBloom(),
                transactionsRoot,
                b.getStateRoot(),
                b.getReceiptsRoot(),
                isPending ? null : b.getCoinbase(),
                b.getDifficulty(),
                blockStore.getTotalDifficultyForHash(b.getHash().getBytes()),
                b.getCumulativeDifficulty(),
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
                b.getHashForMergedMining(),
                b.getFeesPaidToMiner()
        );
    }

    private static Object toTransactionResult(int transactionIndex, Block block, boolean fullTx, boolean skipRemasc) {
        Transaction transaction = block.getTransactionsList().get(transactionIndex);

        if(skipRemasc && transaction.isRemascTransaction(transactionIndex, block.getTransactionsList().size())) {
            return null;
        }
        
        if(fullTx) {
            return new TransactionResultDTO(block, transactionIndex, transaction);
        }

        return transaction.getHash().toJsonString();
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

    public String getCumulativeDifficulty() { return cumulativeDifficulty; }

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

    public String getPaidFees() {
        return paidFees;
    }
}