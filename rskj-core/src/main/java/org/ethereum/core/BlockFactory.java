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
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int RLP_HEADER_SIZE = 19;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING = 22;

    private final ActivationConfig activationConfig;

    public BlockFactory(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }

    public BlockHeaderBuilder getBlockHeaderBuilder() {
        return new BlockHeaderBuilder(activationConfig);
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
        BlockHeader header = decodeHeader(rlpHeader, false, sealed);

        List<Transaction> transactionList = parseTxs((RLPList) block.get(1));

        RLPList uncleHeadersRlp = (RLPList) block.get(2);

        List<BlockHeader> uncleList = new ArrayList<>();

        for (int k = 0; k < uncleHeadersRlp.size(); k++) {
            RLPElement element = uncleHeadersRlp.get(k);
            BlockHeader uncleHeader = decodeHeader((RLPList)element, false, sealed);
            uncleList.add(uncleHeader);
        }

        return newBlock(header, transactionList, uncleList, sealed);
    }

    public Block newBlock(BlockHeader header, List<Transaction> transactionList, List<BlockHeader> uncleList) {
        return newBlock(header, transactionList, uncleList, true);
    }

    public Block newBlock(BlockHeader header, List<Transaction> transactionList, List<BlockHeader> uncleList, boolean sealed) {
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, header.getNumber());
        return new Block(header, transactionList, uncleList, isRskip126Enabled, sealed);
    }

    public BlockHeader decodeHeader(byte[] encoded, boolean compressed) {
        return decodeHeader(RLP.decodeList(encoded), compressed, true);
    }

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean compressed, boolean sealed) {
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

        byte[] extensionData = rlpHeader.get(6).getRLPData(); // rskip351: logs bloom when decoding extended, list(version, hash(extension)) when compressed
        byte[] difficultyBytes = rlpHeader.get(7).getRLPData();
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(difficultyBytes);

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        byte[] glBytes = rlpHeader.get(9).getRLPData();
        byte[] guBytes = rlpHeader.get(10).getRLPData();
        byte[] tsBytes = rlpHeader.get(11).getRLPData();

        long blockNumber = parseBigInteger(nrBytes).longValueExact();

        long gasUsed = parseBigInteger(guBytes).longValueExact();
        long timestamp = parseBigInteger(tsBytes).longValueExact();

        byte[] extraData = rlpHeader.get(12).getRLPData();

        Coin paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        byte[] minimumGasPriceBytes = rlpHeader.get(14).getRLPData();
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPriceBytes);

        if (!canBeDecoded(rlpHeader, blockNumber, compressed)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid block header size: %d",
                    rlpHeader.size()
            ));
        }

        int r = 15;

        byte[] ucBytes = rlpHeader.get(r++).getRLPData();
        int uncleCount = parseBigInteger(ucBytes).intValueExact();

        byte[] ummRoot = null;
        if (activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber)) {
            ummRoot = rlpHeader.get(r++).getRLPRawData();
        }

        byte version = 0x0;

        if (activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber)) {
            version = compressed
                    ? RLP.decodeList(extensionData).get(0).getRLPData()[0]
                    : rlpHeader.get(r++).getRLPData()[0];
        }

        short[] txExecutionSublistsEdges = null;

        if ((!activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber) || !compressed) &&
                (rlpHeader.size() > r && activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))) {
            txExecutionSublistsEdges = ByteUtil.rlpToShorts(rlpHeader.get(r++).getRLPRawData());
        }

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        if (rlpHeader.size() > r) {
            bitcoinMergedMiningHeader = rlpHeader.get(r++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(r++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(r++).getRLPData();
        }

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, blockNumber);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, blockNumber) &&
                blockNumber >= MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION;

        return createBlockHeader(compressed, sealed, parentHash, unclesHash,
                coinBaseBytes, coinbase, stateRoot, txTrieRoot, receiptTrieRoot, extensionData,
                difficultyBytes, difficulty, glBytes, blockNumber, gasUsed, timestamp, extraData,
                paidFees, minimumGasPriceBytes, minimumGasPrice, uncleCount, ummRoot, version, txExecutionSublistsEdges,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                useRskip92Encoding, includeForkDetectionData);
    }

    private BlockHeader createBlockHeader(boolean compressed, boolean sealed, byte[] parentHash, byte[] unclesHash,
                                          byte[] coinBaseBytes, RskAddress coinbase, byte[] stateRoot, byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] extensionData,
                                          byte[] difficultyBytes, BlockDifficulty difficulty, byte[] glBytes, long blockNumber, long gasUsed, long timestamp, byte[] extraData,
                                          Coin paidFees, byte[] minimumGasPriceBytes, Coin minimumGasPrice, int uncleCount, byte[] ummRoot, byte version, short[] txExecutionSublistsEdges,
                                          byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof, byte[] bitcoinMergedMiningCoinbaseTransaction,
                                          boolean useRskip92Encoding, boolean includeForkDetectionData) {
        if (blockNumber == Genesis.NUMBER) {
            return new GenesisHeader(
                    parentHash,
                    unclesHash,
                    extensionData,
                    difficultyBytes,
                    blockNumber,
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

        if (version == 1) {
            return new BlockHeaderV1(
                    parentHash, unclesHash, coinbase, stateRoot,
                    txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                    blockNumber, glBytes, gasUsed, timestamp, extraData,
                    paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                    bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                    minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                    ummRoot, txExecutionSublistsEdges, compressed
            );
        }

        return new BlockHeaderV0(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                blockNumber, glBytes, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                ummRoot, txExecutionSublistsEdges
        );
    }

    private boolean canBeDecoded(RLPList rlpHeader, long blockNumber, boolean compressed) {
        int preUmmHeaderSizeAdjustment = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? 0 : 1;
        int preParallelSizeAdjustment = activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber) ? 0 : 1;
        int preRSKIP351SizeAdjustment = getRSKIP351SizeAdjustment(blockNumber, compressed, preParallelSizeAdjustment);

        int expectedSize = RLP_HEADER_SIZE - preUmmHeaderSizeAdjustment - preParallelSizeAdjustment - preRSKIP351SizeAdjustment;
        int expectedSizeMM = RLP_HEADER_SIZE_WITH_MERGED_MINING - preUmmHeaderSizeAdjustment - preParallelSizeAdjustment - preRSKIP351SizeAdjustment;

        return rlpHeader.size() == expectedSize || rlpHeader.size() == expectedSizeMM;
    }

    private int getRSKIP351SizeAdjustment(long blockNumber, boolean compressed, int preParallelSizeAdjustment) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber)) {
            return 1; // remove version
        }

        if (compressed) {
            return 2 - preParallelSizeAdjustment; // remove version and edges if existent
        }

        return 0;
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
