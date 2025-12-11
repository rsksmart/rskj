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
    public static final int BLOCK_ELEMENTS_SIZE = 3;
    private static final int INITIAL_RLP_HEADER_SIZE = 16;
    private static final int INITIAL_RLP_HEADER_SIZE_WITH_MERGED_MINING = 19;
    private final ActivationConfig activationConfig;

    public BlockFactory(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
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
        if (block.size() != BLOCK_ELEMENTS_SIZE) {
            throw new IllegalArgumentException("A block must have 3 exactly items");
        }

        RLPList rlpHeader = (RLPList) block.get(0);
        BlockHeader header = decodeHeader(rlpHeader, false, sealed);

        List<Transaction> transactionList = parseTxs((RLPList) block.get(1));

        RLPList uncleHeadersRlp = (RLPList) block.get(2);

        List<BlockHeader> uncleList = new ArrayList<>();

        for (int k = 0; k < uncleHeadersRlp.size(); k++) {
            RLPElement element = uncleHeadersRlp.get(k);
            BlockHeader uncleHeader = decodeHeader((RLPList) element, false, sealed);
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

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean compressedBlockHeader, boolean sealed) {
        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        long blockNumber = parseBigInteger(nrBytes).longValueExact();
        if (!canBeDecoded(rlpHeader, blockNumber, compressedBlockHeader)) {
            throw new IllegalArgumentException(String.format(
                "Invalid block header size: %d",
                rlpHeader.size()
            ));
        }

        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(activationConfig);
        fillFirstFields(blockHeaderBuilder, rlpHeader);

        int r = 16;
        if (hasUMMField(blockNumber)) {
            byte[] ummRoot = rlpHeader.get(r++).getRLPRawData();
            blockHeaderBuilder.setUmmRoot(ummRoot);
        }

        if (canExtendHeader(blockNumber) && !compressedBlockHeader) {
            if (hasParallelTxExecutionField(blockNumber)) {
                short[] txExecutionSublistsEdges = ByteUtil.rlpToShorts(rlpHeader.get(r++).getRLPRawData());
                blockHeaderBuilder.setTxExecutionSublistsEdges(txExecutionSublistsEdges);
            }

            if (hasBaseEventField(blockNumber)) {
                byte[] baseEvent = rlpHeader.get(r++).getRLPRawData();
                blockHeaderBuilder.setBaseEvent(baseEvent);
            }
        }
        
        fillMergedMiningFields(blockHeaderBuilder, rlpHeader, r);
        return blockHeaderBuilder.build(compressedBlockHeader, sealed);
    }

    private void fillFirstFields(BlockHeaderBuilder blockHeaderBuilder, RLPList rlpHeader) {
        byte[] parentHash = rlpHeader.get(0).getRLPData();
        blockHeaderBuilder.setParentHash(parentHash);

        byte[] unclesHash = rlpHeader.get(1).getRLPData();
        blockHeaderBuilder.setUnclesHash(unclesHash);

        byte[] coinBaseBytes = rlpHeader.get(2).getRLPData();
        RskAddress coinbase = RLP.parseRskAddress(coinBaseBytes);
        blockHeaderBuilder.setCoinbase(coinbase);

        byte[] stateRoot = rlpHeader.get(3).getRLPData();
        if (stateRoot == null) {
            stateRoot = EMPTY_TRIE_HASH;
        }
        blockHeaderBuilder.setStateRoot(stateRoot);

        byte[] txTrieRoot = rlpHeader.get(4).getRLPData();
        if (txTrieRoot == null) {
            txTrieRoot = EMPTY_TRIE_HASH;
        }
        blockHeaderBuilder.setTxTrieRoot(txTrieRoot);

        byte[] receiptTrieRoot = rlpHeader.get(5).getRLPData();
        if (receiptTrieRoot == null) {
            receiptTrieRoot = EMPTY_TRIE_HASH;
        }
        blockHeaderBuilder.setReceiptTrieRoot(receiptTrieRoot);

        byte[] extensionData = rlpHeader.get(6).getRLPData();
        blockHeaderBuilder.setLogsBloom(extensionData);

        byte[] difficultyBytes = rlpHeader.get(7).getRLPData();
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(difficultyBytes);
        blockHeaderBuilder.setDifficulty(difficulty);

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        long blockNumber = parseBigInteger(nrBytes).longValueExact();
        blockHeaderBuilder.setNumber(blockNumber);

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, blockNumber);
        blockHeaderBuilder.setUseRskip92Encoding(useRskip92Encoding);

        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, blockNumber) &&
            blockNumber >= MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION;
        blockHeaderBuilder.setIncludeForkDetectionData(includeForkDetectionData);

        byte[] glBytes = rlpHeader.get(9).getRLPData();
        blockHeaderBuilder.setGasLimit(glBytes);

        byte[] guBytes = rlpHeader.get(10).getRLPData();
        long gasUsed = parseBigInteger(guBytes).longValueExact();
        blockHeaderBuilder.setGasUsed(gasUsed);

        byte[] tsBytes = rlpHeader.get(11).getRLPData();
        long timestamp = parseBigInteger(tsBytes).longValueExact();
        blockHeaderBuilder.setTimestamp(timestamp);

        byte[] extraData = rlpHeader.get(12).getRLPData();
        blockHeaderBuilder.setExtraData(extraData);

        Coin paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        blockHeaderBuilder.setPaidFees(paidFees);

        byte[] minimumGasPriceBytes = rlpHeader.get(14).getRLPData();
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPriceBytes);
        blockHeaderBuilder.setMinimumGasPrice(minimumGasPrice);

        byte[] ucBytes = rlpHeader.get(15).getRLPData();
        int uncleCount = parseBigInteger(ucBytes).intValueExact();
        blockHeaderBuilder.setUncleCount(uncleCount);
    }

    private void fillMergedMiningFields(BlockHeaderBuilder blockHeaderBuilder, RLPList rlpHeader, int startingIndex) {
        byte[] bitcoinMergedMiningHeader = rlpHeader.get(startingIndex++).getRLPData();
        blockHeaderBuilder.setBitcoinMergedMiningHeader(bitcoinMergedMiningHeader);
        byte[] bitcoinMergedMiningMerkleProof = rlpHeader.get(startingIndex++).getRLPRawData();
        blockHeaderBuilder.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleProof);
        byte[] bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(startingIndex).getRLPData();
        blockHeaderBuilder.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
    }

    private boolean hasUMMField(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber);
    }

    private boolean canExtendHeader(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber);
    }

    private boolean hasParallelTxExecutionField(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber);
    }

    private boolean hasBaseEventField(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP535, blockNumber);
    }

    private boolean canBeDecoded(RLPList rlpHeader, long blockNumber, boolean compressed) {
        int expectedSize;

        if (!hasMergedMiningFields(blockNumber)) {
            expectedSize = INITIAL_RLP_HEADER_SIZE;
        } else {
            expectedSize = INITIAL_RLP_HEADER_SIZE_WITH_MERGED_MINING;
        }

        if (!compressed) {
            if (hasUMMField(blockNumber)) {
                expectedSize = expectedSize + 1;
            }
            if (hasParallelTxExecutionField(blockNumber)) {
                expectedSize = expectedSize + 1;
            }
            if (hasBaseEventField(blockNumber)) {
                expectedSize = expectedSize + 1;
            }
        }

        return rlpHeader.size() == expectedSize;
    }

    private boolean hasMergedMiningFields(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP98, blockNumber);
    }
}
