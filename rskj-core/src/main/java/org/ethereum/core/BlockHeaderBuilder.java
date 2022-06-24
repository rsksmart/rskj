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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockHeaderBuilder {

    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    private byte[] parentHash;
    private byte[] unclesHash;
    private RskAddress coinbase;
    private byte[] stateRoot;
    private byte[] txTrieRoot;
    private byte[] receiptTrieRoot;
    private byte[] logsBloom;
    private BlockDifficulty difficulty;
    private long timestamp;
    private long number;
    private byte[] gasLimit;
    private long gasUsed;
    private Coin paidFees;

    private byte[] extraData;
    private byte[] bitcoinMergedMiningHeader;
    private byte[] bitcoinMergedMiningMerkleProof;
    private byte[] bitcoinMergedMiningCoinbaseTransaction;
    private byte[] mergedMiningForkDetectionData;
    private byte[] ummRoot;
    private short[] txExecutionSublistsEdges;

    private Coin minimumGasPrice;
    private int uncleCount;

    private boolean useRskip92Encoding;
    private boolean includeForkDetectionData;

    private final ActivationConfig activationConfig;

    private boolean createConsensusCompliantHeader;
    private boolean createUmmCompliantHeader;
    private boolean createParallelCompliantHeader;

    public BlockHeaderBuilder(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
        createConsensusCompliantHeader = true;
        createUmmCompliantHeader = true;
        createParallelCompliantHeader = true;
    }

    public BlockHeaderBuilder setCreateConsensusCompliantHeader(boolean createConsensusCompliantHeader) {
        this.createConsensusCompliantHeader = createConsensusCompliantHeader;
        return this;
    }

    public BlockHeaderBuilder setCreateUmmCompliantHeader(boolean createUmmCompliantHeader) {
        this.createUmmCompliantHeader = createUmmCompliantHeader;
        return this;
    }

    public BlockHeaderBuilder setCreateParallelCompliantHeader(boolean createParallelCompliantHeader) {
        this.createParallelCompliantHeader = createParallelCompliantHeader;

        if (!createParallelCompliantHeader) {
            this.txExecutionSublistsEdges = null;
        }
        return this;
    }

    public BlockHeaderBuilder setStateRoot(byte[] stateRoot) {
        this.stateRoot = copy(stateRoot);
        return this;
    }

    public BlockHeaderBuilder setDifficulty(BlockDifficulty difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public BlockHeaderBuilder setPaidFees(Coin paidFees) {
        this.paidFees = paidFees;
        return this;
    }

    public BlockHeaderBuilder setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
        return this;
    }

    public BlockHeaderBuilder setLogsBloom(byte[] logsBloom) {
        this.logsBloom = copy(logsBloom);
        return this;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        this.bitcoinMergedMiningHeader = copy(bitcoinMergedMiningHeader);
        return this;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningMerkleProof(byte[] bitcoinMergedMiningMerkleProof) {
        this.bitcoinMergedMiningMerkleProof = copy(bitcoinMergedMiningMerkleProof);
        return this;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningCoinbaseTransaction(byte[] bitcoinMergedMiningCoinbaseTransaction) {
        this.bitcoinMergedMiningCoinbaseTransaction = copy(bitcoinMergedMiningCoinbaseTransaction);
        return this;
    }

    public BlockHeaderBuilder setTxTrieRoot(byte[] txTrieRoot) {
        this.txTrieRoot = copy(txTrieRoot);
        return this;
    }

    public BlockHeaderBuilder setEmptyTxTrieRoot() {
        this.txTrieRoot = EMPTY_TRIE_HASH;
        return this;
    }

    public BlockHeaderBuilder setReceiptTrieRoot(byte[] receiptTrieRoot) {
        this.receiptTrieRoot = copy(receiptTrieRoot);
        return this;
    }

    public BlockHeaderBuilder setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public BlockHeaderBuilder setNumber(long number) {
        this.number = number;
        return this;
    }

    public BlockHeaderBuilder setGasLimit(byte[] gasLimit) {
        this.gasLimit = copy(gasLimit);
        return this;
    }

    public BlockHeaderBuilder setExtraData(byte[] extraData) {
        this.extraData = copy(extraData);
        return this;
    }

    public BlockHeaderBuilder setMergedMiningForkDetectionData(byte[] mergedMiningForkDetectionData) {
        this.mergedMiningForkDetectionData = copy(mergedMiningForkDetectionData);
        return this;
    }

    public BlockHeaderBuilder setMinimumGasPrice(Coin minimumGasPrice) {
        this.minimumGasPrice = minimumGasPrice;
        return this;
    }

    public BlockHeaderBuilder setUncleCount(int uncleCount) {
        this.uncleCount = uncleCount;
        return this;
    }

    public BlockHeaderBuilder setUseRskip92Encoding(boolean useRskip92Encoding) {
        this.useRskip92Encoding = useRskip92Encoding;
        return this;
    }

    public BlockHeaderBuilder setIncludeForkDetectionData(boolean includeForkDetectionData) {
        this.includeForkDetectionData = includeForkDetectionData;
        return this;
    }

    public BlockHeaderBuilder setParentHashFromKeccak256(Keccak256 parentHash) {
        this.parentHash = copy(parentHash);
        return this;
    }

    public BlockHeaderBuilder setParentHash(byte[] parentHash) {
        this.parentHash = copy(parentHash);
        return this;
    }

    public BlockHeaderBuilder setEmptyUnclesHash() {
        this.unclesHash = EMPTY_LIST_HASH;
        return this;
    }

    public BlockHeaderBuilder setUnclesHash(byte[] unclesHash) {
        this.unclesHash = copy(unclesHash);
        return this;
    }

    public BlockHeaderBuilder setCoinbase(RskAddress coinbase) {
        this.coinbase = coinbase;
        return this;
    }

    public BlockHeaderBuilder setDifficultyFromBytes(@Nullable byte[] data) {
        // This is to make it compatible with RLP.parseBlockDifficulty() which was previously
        // user (but I think it was wrongly included in the RLP class, because these arguments
        // do not come from any RLP parsing).
        if (data != null) {
            difficulty = new BlockDifficulty(new BigInteger(data));
        } else {
            difficulty = null;
        }
        return this;
    }

    public BlockHeaderBuilder setEmptyMergedMiningForkDetectionData() {
        mergedMiningForkDetectionData = new byte[12];
        return this;
    }

    public BlockHeaderBuilder setEmptyExtraData() {
        extraData = new byte[]{};
        return this;
    }

    public BlockHeaderBuilder setEmptyLogsBloom() {
        logsBloom = copy(new Bloom().getData());
        return this;
    }

    public BlockHeaderBuilder setEmptyStateRoot() {
        stateRoot = EMPTY_TRIE_HASH;
        return this;
    }

    public BlockHeaderBuilder setEmptyReceiptTrieRoot() {
        receiptTrieRoot = EMPTY_TRIE_HASH;
        return this;
    }

    public BlockHeaderBuilder setUmmRoot(byte[] ummRoot) {
        this.ummRoot = copy(ummRoot, null);
        return this;
    }

    public BlockHeaderBuilder setTxExecutionSublistsEdges(short[] edges) {
        if (edges != null) {
            this.txExecutionSublistsEdges = new short[edges.length];
            System.arraycopy(edges, 0, this.txExecutionSublistsEdges, 0, edges.length);
            this.createParallelCompliantHeader = true;
        } else {
            this.txExecutionSublistsEdges = null;
            this.createParallelCompliantHeader = false;
        }
        return this;
    }

    private void initializeWithDefaultValues() {
        extraData = normalizeValue(extraData, new byte[0]);
        bitcoinMergedMiningHeader = normalizeValue(bitcoinMergedMiningHeader, new byte[0]);
        bitcoinMergedMiningMerkleProof = normalizeValue(bitcoinMergedMiningMerkleProof, new byte[0]);
        bitcoinMergedMiningCoinbaseTransaction = normalizeValue(bitcoinMergedMiningCoinbaseTransaction, new byte[0]);

        unclesHash = normalizeValue(unclesHash, EMPTY_LIST_HASH);
        coinbase = normalizeValue(coinbase, RskAddress.nullAddress());
        stateRoot = normalizeValue(stateRoot, EMPTY_TRIE_HASH);
        txTrieRoot = normalizeValue(txTrieRoot, EMPTY_TRIE_HASH);
        receiptTrieRoot = normalizeValue(receiptTrieRoot, EMPTY_TRIE_HASH);
        logsBloom = normalizeValue(logsBloom, new Bloom().getData());
        paidFees = normalizeValue(paidFees, Coin.ZERO);
        minimumGasPrice = normalizeValue(minimumGasPrice, Coin.ZERO);

        mergedMiningForkDetectionData = normalizeValue(mergedMiningForkDetectionData, new byte[12]);
    }

    private <T> T normalizeValue(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private byte[] copy(Keccak256 hash) {
        return copy(hash.getBytes());
    }

    private byte[] copy(byte[] bytes) {
        return copy(bytes, new byte[0]);
    }

    private byte[] copy(byte[] bytes, byte[] defaultValue) {
        if (bytes == null) {
            return defaultValue;
        }

        return Arrays.copyOf(bytes, bytes.length);
    }

    public BlockHeader build() {
        // Initial null values in some fields are replaced by empty
        // arrays
        initializeWithDefaultValues();

        if (createConsensusCompliantHeader) {
            useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
            includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                    mergedMiningForkDetectionData.length > 0;
        }

        if (createUmmCompliantHeader) {
            if (activationConfig.isActive(ConsensusRule.RSKIPUMM, number)) {
                if (ummRoot == null) {
                    ummRoot = new byte[0];
                }
            }
        }

        if (createParallelCompliantHeader && txExecutionSublistsEdges == null) {
            txExecutionSublistsEdges = new short[0];
        }

        return new BlockHeader(
                parentHash, unclesHash, coinbase,
                stateRoot, txTrieRoot, receiptTrieRoot,
                logsBloom, difficulty, number,
                gasLimit, gasUsed, timestamp, extraData, paidFees,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount,
                false, useRskip92Encoding,
                includeForkDetectionData, ummRoot, txExecutionSublistsEdges
        );
    }
}