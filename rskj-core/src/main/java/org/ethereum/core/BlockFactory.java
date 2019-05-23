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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int RLP_HEADER_SIZE = 16;
    private static final int RLP_HEADER_SIZE_WITH_FORK_DATA = 17;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING = 19;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_FORK_DATA = 20;

    private final ActivationConfig activationConfig;

    public BlockFactory(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }

    public Block cloneBlockForModification(Block block) {
        return decodeBlock(block.getEncoded(), false);
    }

    public Block decodeBlock(byte[] rawData) {
        return decodeBlock(rawData, true);
    }

    private Block decodeBlock(byte[] rawData, boolean sealed) {
        RLPList block = RLP.decodeList(rawData);
        if (block.size() != 3) {
            throw new IllegalArgumentException("A block must have 3 exactly items");
        }

        RLPList rlpHeader = (RLPList) block.get(0);
        BlockHeader header = decodeHeader(rlpHeader, sealed);

        List<Transaction> transactionList = parseTxs((RLPList) block.get(1));

        RLPList uncleHeadersRlp = (RLPList) block.get(2);
        List<BlockHeader> uncleList = uncleHeadersRlp.stream()
                .map(uncleHeader -> decodeHeader((RLPList) uncleHeader, sealed))
                .collect(Collectors.toList());

        return newBlock(header, transactionList, uncleList, sealed);
    }

    public Block newBlock(BlockHeader header, List<Transaction> transactionList, List<BlockHeader> uncleList) {
        return newBlock(header, transactionList, uncleList, true);
    }

    public Block newBlock(BlockHeader header, List<Transaction> transactionList, List<BlockHeader> uncleList, boolean sealed) {
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, header.getNumber());
        return new Block(header, transactionList, uncleList, isRskip126Enabled, sealed);
    }

    public BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
            byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
            byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] miningForkDetectionData,
            byte[] minimumGasPrice, int uncleCount) {
        return newHeader(
                parentHash, unclesHash, coinbase,
                ByteUtils.clone(EMPTY_TRIE_HASH), null, ByteUtils.clone(EMPTY_TRIE_HASH),
                logsBloom, difficulty, number, gasLimit, gasUsed, timestamp, extraData, Coin.ZERO,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, miningForkDetectionData, minimumGasPrice, uncleCount
        );
    }

    public BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] stateRoot, byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
            Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
            byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
            byte[] minimumGasPrice, int uncleCount) {
        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number);
        return new BlockHeader(
                parentHash, unclesHash, new RskAddress(coinbase),
                stateRoot, txTrieRoot, receiptTrieRoot,
                logsBloom, RLP.parseBlockDifficulty(difficulty), number,
                gasLimit, gasUsed, timestamp, extraData, paidFees,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                mergedMiningForkDetectionData, RLP.parseSignedCoinNonNullZero(minimumGasPrice), uncleCount,
                false, useRskip92Encoding, includeForkDetectionData
        );
    }

    public BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp,
            byte[] extraData, byte[] minimumGasPrice, int uncleCount) {
        return newHeader(
                parentHash, unclesHash, coinbase, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                null, null, null, null,
                minimumGasPrice, uncleCount
        );
    }

    public BlockHeader decodeHeader(byte[] encoded) {
        return decodeHeader(RLP.decodeList(encoded), true);
    }

    public BlockHeader decodeHeader(RLPList rlpHeader, boolean sealed) {
        // TODO fix old tests that have other sizes
        if (rlpHeader.size() != RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_FORK_DATA &&
                rlpHeader.size() != RLP_HEADER_SIZE_WITH_MERGED_MINING &&
                rlpHeader.size() != RLP_HEADER_SIZE_WITH_FORK_DATA &&
                rlpHeader.size() != RLP_HEADER_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "A block header must have at least 16 elements. It may have 17 or 19 or 20" +
                            " depending if it includes merged-mining and fork detection data fields. It had %d",
                    rlpHeader.size()
            ));
        }

        byte[] parentHash = rlpHeader.get(0).getRLPData();
        byte[] unclesHash = rlpHeader.get(1).getRLPData();
        RskAddress coinbase = RLP.parseRskAddress(rlpHeader.get(2).getRLPData());
        byte[] stateRoot = rlpHeader.get(3).getRLPData();
        if (stateRoot == null) {
            stateRoot = EMPTY_TRIE_HASH;
        }

        byte[] txTrieRoot = rlpHeader.get(4).getRLPData();
        if (txTrieRoot == null) {
            txTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] receiptTrieRoot = rlpHeader.get(5).getRLPData();
        if (receiptTrieRoot == null) {
            receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] logsBloom = rlpHeader.get(6).getRLPData();
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(rlpHeader.get(7).getRLPData());

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        byte[] glBytes = rlpHeader.get(9).getRLPData();
        byte[] guBytes = rlpHeader.get(10).getRLPData();
        byte[] tsBytes = rlpHeader.get(11).getRLPData();

        long number = parseBigInteger(nrBytes).longValueExact();

        byte[] gasLimit = glBytes;
        long gasUsed = parseBigInteger(guBytes).longValueExact();
        long timestamp = parseBigInteger(tsBytes).longValueExact();

        byte[] extraData = rlpHeader.get(12).getRLPData();

        Coin paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(rlpHeader.get(14).getRLPData());

        int headerElementsParsed = 15;

        int uncleCount = 0;
        if (rlpHeader.size() == RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_FORK_DATA ||
                rlpHeader.size() == RLP_HEADER_SIZE_WITH_MERGED_MINING ||
                rlpHeader.size() == RLP_HEADER_SIZE_WITH_FORK_DATA ||
                rlpHeader.size() == RLP_HEADER_SIZE) {
            byte[] ucBytes = rlpHeader.get(headerElementsParsed++).getRLPData();
            uncleCount = parseBigInteger(ucBytes).intValueExact();
        }

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number);
        byte[] miningForkDetectionData = new byte[0];
        if (rlpHeader.size() == RLP_HEADER_SIZE_WITH_FORK_DATA && includeForkDetectionData) {
            miningForkDetectionData = rlpHeader.get(headerElementsParsed).getRLPData();
        }

        if(rlpHeader.size() == RLP_HEADER_SIZE_WITH_MERGED_MINING) {
            bitcoinMergedMiningHeader = rlpHeader.get(headerElementsParsed++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(headerElementsParsed++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(headerElementsParsed++).getRLPData();
        }

        if(rlpHeader.size() == RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_FORK_DATA && includeForkDetectionData) {
            bitcoinMergedMiningHeader = rlpHeader.get(headerElementsParsed++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(headerElementsParsed++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(headerElementsParsed++).getRLPData();
            miningForkDetectionData = rlpHeader.get(headerElementsParsed).getRLPData();
        }

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
        return new BlockHeader(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, miningForkDetectionData,
                minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData
        );
    }

    private static BigInteger parseBigInteger(byte[] bytes) {
        return bytes == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(bytes);
    }

    private static List<Transaction> parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (tx.isRemascTransaction(i, txTransactions.size())) {
                // It is the remasc transaction
                tx = new RemascTransaction(transactionRaw.getRLPData());
            }
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
    }
}
