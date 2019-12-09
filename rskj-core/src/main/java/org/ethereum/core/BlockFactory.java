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

import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int RLP_HEADER_SIZE = 16;
    private static final int RLP_HEADER_SIZE_WITH_PARALLELTXS = 17;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING = 19;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_PARALLELTXS = 20;

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
                bitcoinMergedMiningCoinbaseTransaction, miningForkDetectionData, minimumGasPrice, uncleCount,
                new int[]{}
        );
    }

    public BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] stateRoot, byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
            Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
            byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
            byte[] minimumGasPrice, int uncleCount, int[] partitionEnds) {
        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                mergedMiningForkDetectionData.length > 0;
        boolean useParallelTxExecution = activationConfig.isActive(ConsensusRule.RSKIP144, number);
        return new BlockHeader(
                parentHash, unclesHash, new RskAddress(coinbase),
                stateRoot, txTrieRoot, receiptTrieRoot,
                logsBloom, RLP.parseBlockDifficulty(difficulty), number,
                gasLimit, gasUsed, timestamp, extraData, paidFees,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                mergedMiningForkDetectionData, RLP.parseSignedCoinNonNullZero(minimumGasPrice), uncleCount,
                partitionEnds, false, useRskip92Encoding, includeForkDetectionData, useParallelTxExecution
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
                null, null, null, new byte[0],
                minimumGasPrice, uncleCount
        );
    }

    public BlockHeader decodeHeader(byte[] encoded) {
        return decodeHeader(RLP.decodeList(encoded), true);
    }

    @VisibleForTesting
    public static int[] decodePartitionEnds(RLPElement partitionEndsRLPEncoded) {
        List<RLPElement> headerDecoded4 = RLP.decodeList(partitionEndsRLPEncoded.getRLPData());
        int[] decodedPartitionEnds = new int[headerDecoded4.size()];
        for (int i = 0; i < headerDecoded4.size(); i++) {
            byte[] encodedInt = headerDecoded4.get(i).getRLPRawData();
            if (Arrays.equals(encodedInt, ByteUtil.EMPTY_BYTE_ARRAY)) {
                // Caution : encoding byte[]{0} gives byte[]{OFFSET_SHORT_ITEM}
                // whereas decoding byte[]{OFFSET_SHORT_ITEM} gives EMPTY_BYTE_ARRAY
                // Then, in this case we need to override the decoding result to get the exact data
                // as before the encoding
                encodedInt = new byte[]{0};
            }
            decodedPartitionEnds[i] = BigIntegers.fromUnsignedByteArray(encodedInt).intValue();
        }
        return decodedPartitionEnds;
    }

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean sealed) {
        // TODO fix old tests that have other sizes
        if (rlpHeader.size() != RLP_HEADER_SIZE
                && rlpHeader.size() != RLP_HEADER_SIZE_WITH_MERGED_MINING
                && rlpHeader.size() != RLP_HEADER_SIZE_WITH_PARALLELTXS
                && rlpHeader.size() != RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_PARALLELTXS) {
            throw new IllegalArgumentException(String.format(
                    "The block header have a wrong number of elements (%d). Only allowed header with %d, %d, %d, or %d elements",
                    rlpHeader.size(),
                    RLP_HEADER_SIZE,
                    RLP_HEADER_SIZE_WITH_MERGED_MINING,
                    RLP_HEADER_SIZE_WITH_PARALLELTXS,
                    RLP_HEADER_SIZE_WITH_MERGED_MINING_AND_PARALLELTXS
            ));
        }

        byte[] parentHash = rlpHeader.get(0).getRLPData();
        byte[] unclesHash = rlpHeader.get(1).getRLPData();
        byte[] coinBaseBytes = rlpHeader.get(2).getRLPData();
        RskAddress coinbase = RLP.parseRskAddress(coinBaseBytes);
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
        byte[] difficultyBytes = rlpHeader.get(7).getRLPData();
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(difficultyBytes);

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        byte[] glBytes = rlpHeader.get(9).getRLPData();
        byte[] guBytes = rlpHeader.get(10).getRLPData();
        byte[] tsBytes = rlpHeader.get(11).getRLPData();

        long number = parseBigInteger(nrBytes).longValueExact();

        long gasUsed = parseBigInteger(guBytes).longValueExact();
        long timestamp = parseBigInteger(tsBytes).longValueExact();

        byte[] extraData = rlpHeader.get(12).getRLPData();

        Coin paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        byte[] minimumGasPriceBytes = rlpHeader.get(14).getRLPData();
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPriceBytes);

        int r = 15;

        int uncleCount = 0;
        byte[] ucBytes = rlpHeader.get(r++).getRLPData();
        uncleCount = parseBigInteger(ucBytes).intValueExact();

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        if (rlpHeader.size() > r + 2) {
            bitcoinMergedMiningHeader = rlpHeader.get(r++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(r++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(r++).getRLPData();
        }

        // RSKIP144
        int[] partitionEnds = new int[]{};
        if (rlpHeader.size() > r) {
            RLPElement encodedPartitionEnds = rlpHeader.get(r++);
            partitionEnds = decodePartitionEnds(encodedPartitionEnds);
        }

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                number >= MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION;

        boolean useParallelTxExecutions = activationConfig.isActive(ConsensusRule.RSKIP144, number);

        if (number == Genesis.NUMBER) {
            return new GenesisHeader(
                    parentHash,
                    unclesHash,
                    logsBloom,
                    difficultyBytes,
                    number,
                    glBytes,
                    gasUsed,
                    timestamp,
                    extraData,
                    bitcoinMergedMiningHeader,
                    bitcoinMergedMiningMerkleProof,
                    bitcoinMergedMiningCoinbaseTransaction,
                    minimumGasPriceBytes,
                    useRskip92Encoding,
                    coinBaseBytes,
                    stateRoot);
        }

        return new BlockHeader(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                number, glBytes, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                minimumGasPrice, uncleCount, partitionEnds, sealed, useRskip92Encoding, includeForkDetectionData,
                useParallelTxExecutions);
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
