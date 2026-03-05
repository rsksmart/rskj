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

import static org.ethereum.core.BlockHeaderIndex.COINBASE;
import static org.ethereum.core.BlockHeaderIndex.DIFFICULTY;
import static org.ethereum.core.BlockHeaderIndex.EXTENSION_DATA;
import static org.ethereum.core.BlockHeaderIndex.EXTRA_DATA;
import static org.ethereum.core.BlockHeaderIndex.GAS_LIMIT;
import static org.ethereum.core.BlockHeaderIndex.GAS_USED;
import static org.ethereum.core.BlockHeaderIndex.MINIMUM_GAS_PRICE;
import static org.ethereum.core.BlockHeaderIndex.NUMBER;
import static org.ethereum.core.BlockHeaderIndex.PAID_FEES;
import static org.ethereum.core.BlockHeaderIndex.PARENT_HASH;
import static org.ethereum.core.BlockHeaderIndex.RECEIPT_TRIE_ROOT;
import static org.ethereum.core.BlockHeaderIndex.STATE_ROOT;
import static org.ethereum.core.BlockHeaderIndex.TIMESTAMP;
import static org.ethereum.core.BlockHeaderIndex.TX_TRIE_ROOT;
import static org.ethereum.core.BlockHeaderIndex.UNCLES_HASH;
import static org.ethereum.core.BlockHeaderIndex.UNCLE_COUNT;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int NUMBER_OF_EXTRA_HEADER_FIELDS = 3;
    // Maximum RLP header sizes (when all optional fields are present)
    private static final int MAX_RLP_HEADER_SIZE_WITHOUT_MINING = 20;
    private static final int MAX_RLP_HEADER_SIZE_WITH_MINING = 23;
    private static final int NUMBER_OF_ELEMENTS_IN_BLOCK_RLP = 3;

    private final ActivationConfig activationConfig;

    public BlockFactory(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }

    private static BigInteger parseBigInteger(byte[] bytes) {
        return bytes == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(bytes);
    }

    private static List<Transaction> parseTxs(RLPList transactionList) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < transactionList.size(); i++) {
            RLPElement transactionRaw = transactionList.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (tx.isRemascTransaction(i, transactionList.size())) {
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
        if (block.size() != NUMBER_OF_ELEMENTS_IN_BLOCK_RLP) {
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

    public Block newBlock(BlockHeader header, List<Transaction> transactionList, List<BlockHeader> uncleList,
                          boolean sealed) {
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, header.getNumber());
        return new Block(header, transactionList, uncleList, isRskip126Enabled, sealed);
    }

    public BlockHeader decodeHeader(byte[] encoded, boolean compressed) {
        return decodeHeader(RLP.decodeList(encoded), compressed, true);
    }

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean compressed, boolean sealed) {
        byte[] parentHash = rlpHeader.get(PARENT_HASH.getIndex()).getRLPData();
        byte[] unclesHash = rlpHeader.get(UNCLES_HASH.getIndex()).getRLPData();
        byte[] coinBaseBytes = rlpHeader.get(COINBASE.getIndex()).getRLPData();
        RskAddress coinbase = RLP.parseRskAddress(coinBaseBytes);
        byte[] stateRoot = rlpHeader.get(STATE_ROOT.getIndex()).getRLPData();
        if (stateRoot == null) {
            stateRoot = EMPTY_TRIE_HASH;
        }

        byte[] txTrieRoot = rlpHeader.get(TX_TRIE_ROOT.getIndex()).getRLPData();
        if (txTrieRoot == null) {
            txTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] receiptTrieRoot = rlpHeader.get(RECEIPT_TRIE_ROOT.getIndex()).getRLPData();
        if (receiptTrieRoot == null) {
            receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] extensionData = rlpHeader.get(EXTENSION_DATA.getIndex()).getRLPData(); // rskip351: logs
        // bloom when decoding
        // extended,
        // list(version,hash(extension))
        // when compressed
        byte[] difficultyBytes = rlpHeader.get(DIFFICULTY.getIndex()).getRLPData();
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(difficultyBytes);

        byte[] nrBytes = rlpHeader.get(NUMBER.getIndex()).getRLPData();
        byte[] glBytes = rlpHeader.get(GAS_LIMIT.getIndex()).getRLPData();
        byte[] guBytes = rlpHeader.get(GAS_USED.getIndex()).getRLPData();
        byte[] tsBytes = rlpHeader.get(TIMESTAMP.getIndex()).getRLPData();

        long blockNumber = parseBigInteger(nrBytes).longValueExact();

        long gasUsed = parseBigInteger(guBytes).longValueExact();
        long timestamp = parseBigInteger(tsBytes).longValueExact();

        byte[] extraData = rlpHeader.get(EXTRA_DATA.getIndex()).getRLPData();

        Coin paidFees = RLP.parseCoin(rlpHeader.get(PAID_FEES.getIndex()).getRLPData());
        byte[] minimumGasPriceBytes = rlpHeader.get(MINIMUM_GAS_PRICE.getIndex()).getRLPData();
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPriceBytes);

        if (!canBeDecoded(rlpHeader, blockNumber, compressed)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid block header size: %d",
                    rlpHeader.size()));
        }

        int r = UNCLE_COUNT.getIndex();

        byte[] ucBytes = rlpHeader.get(r++).getRLPData();
        int uncleCount = parseBigInteger(ucBytes).intValueExact();

        byte[] ummRoot = null;
        if (activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber)) {
            ummRoot = rlpHeader.get(r++).getRLPRawData();
        }

        byte version = 0x0;

        if (isBlockHeaderCompressionEnabled(blockNumber)) {
            version = compressed
                    ? RLP.decodeList(extensionData).get(0).getRLPData()[0]
                    : rlpHeader.get(r++).getRLPData()[0];
        }

        short[] txExecutionSublistsEdges = null;
        byte[] baseEvent = null;

        if ((!isBlockHeaderCompressionEnabled(blockNumber) || !compressed)
            && rlpHeader.size() > r
            && activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber)
                && activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber)) {
                txExecutionSublistsEdges = ByteUtil.rlpToShorts(rlpHeader.get(r++).getRLPRawData());
            }


        if (rlpHeader.size() > r && isBaseEventEnabled(blockNumber) && !compressed) {
            baseEvent = rlpHeader.get(r++).getRLPRawData();
        }

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        if (rlpHeader.size() > r) {
            bitcoinMergedMiningHeader = rlpHeader.get(r++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(r++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(r).getRLPData();
        }

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, blockNumber);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, blockNumber) &&
                blockNumber >= MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION;

        return createBlockHeader(compressed, sealed, parentHash, unclesHash,
                coinBaseBytes, coinbase, stateRoot, txTrieRoot, receiptTrieRoot, extensionData,
                difficultyBytes, difficulty, glBytes, blockNumber, gasUsed, timestamp, extraData,
                paidFees, minimumGasPriceBytes, minimumGasPrice, uncleCount, ummRoot, baseEvent, version,
                txExecutionSublistsEdges,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                useRskip92Encoding, includeForkDetectionData);
    }

    private BlockHeader createBlockHeader(boolean compressed, boolean sealed, byte[] parentHash, byte[] unclesHash,
                                          byte[] coinBaseBytes, RskAddress coinbase, byte[] stateRoot, byte[] txTrieRoot, byte[] receiptTrieRoot,
                                          byte[] extensionData,
                                          byte[] difficultyBytes, BlockDifficulty difficulty, byte[] glBytes, long blockNumber, long gasUsed,
                                          long timestamp, byte[] extraData,
                                          Coin paidFees, byte[] minimumGasPriceBytes, Coin minimumGasPrice, int uncleCount, byte[] ummRoot,
                                          byte[] baseEvent, byte version, short[] txExecutionSublistsEdges,
                                          byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                                          byte[] bitcoinMergedMiningCoinbaseTransaction,
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

        if (version == 2) {
            return new BlockHeaderV2(
                    parentHash, unclesHash, coinbase, stateRoot,
                    txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                    blockNumber, glBytes, gasUsed, timestamp, extraData,
                    paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                    bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                    minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                    ummRoot, baseEvent, txExecutionSublistsEdges, compressed);
        }

        if (version == 1) {
            return new BlockHeaderV1(
                    parentHash, unclesHash, coinbase, stateRoot,
                    txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                    blockNumber, glBytes, gasUsed, timestamp, extraData,
                    paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                    bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                    minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                    ummRoot, txExecutionSublistsEdges, compressed);
        }

        return new BlockHeaderV0(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                blockNumber, glBytes, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                ummRoot, baseEvent, txExecutionSublistsEdges);
    }

    /**
     * Validates whether the RLP header can be decoded based on its size and the
     * activation rules
     * at the given block number.
     * <p>
     * The size calculation follows a pattern: we start with the maximum possible
     * header size
     * and subtract 1 for each optional field that is NOT active at this block
     * height.
     *
     * @param rlpHeader   The RLP-encoded header to validate
     * @param blockNumber The block number (used to check activation rules)
     * @param compressed  Whether the header uses compressed encoding (RSKIP351)
     * @return true if the header size matches expected size with or without merged
     *         mining fields
     */
    private boolean canBeDecoded(RLPList rlpHeader, long blockNumber, boolean compressed) {
        int expectedSizeWithoutMining = calculateExpectedHeaderSize(blockNumber, compressed, false);
        int expectedSizeWithMining = calculateExpectedHeaderSize(blockNumber, compressed, true);

        return rlpHeader.size() == expectedSizeWithoutMining ||
                rlpHeader.size() == expectedSizeWithMining;
    }

    /**
     * Calculates the expected RLP header size based on active consensus rules.
     *
     * @param blockNumber      The block number (used to check activation rules)
     * @param compressed       Whether compressed encoding is used
     * @param withMergedMining Whether to include merged mining fields in the
     *                         calculation
     * @return The expected number of RLP elements in the header
     */
    private int calculateExpectedHeaderSize(long blockNumber, boolean compressed, boolean withMergedMining) {
        // Expected size with all the optional fields present.
        int expectedFullSize = withMergedMining ? MAX_RLP_HEADER_SIZE_WITH_MINING : MAX_RLP_HEADER_SIZE_WITHOUT_MINING;
        // Subtract 1 for each optional field that is NOT present
        if (!activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber)) {
            // RSKIPUMM is not active, field ummRoot is not present
            expectedFullSize -= 1;
        }
        if (!(activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber) && activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))) {
            // Parallel tx execution is not active, field txExecutionSublistsEdges is not
            // present
            expectedFullSize -= 1;
        }
        if (!isBaseEventEnabled(blockNumber)) {
            // baseEvent is not enabled, field baseEvent is not present
            expectedFullSize -= 1;
        }
        expectedFullSize -= getRSKIP351SizeAdjustmentFromExtensionData(blockNumber, compressed);

        return expectedFullSize;

    }

    private boolean isBlockHeaderCompressionEnabled(long blockNumber) {
        return activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber);
    }

    private boolean isBaseEventEnabled(long blockNumber) {
        return (activationConfig.isActive(ConsensusRule.RSKIP535, blockNumber) &&
                isBlockHeaderCompressionEnabled(blockNumber));
    }

    /**
     * Calculates the size adjustment for RSKIP351-related fields.
     * <p>
     * RSKIP351 introduces versioned block headers with optional compressed
     * encoding.
     * When compressed, the version, edges, and baseEvent fields are stored in an
     * extension
     * structure rather than as separate RLP elements.
     *
     * @param blockNumber The block number for activation config lookup
     * @param compressed  Whether the header is compressed
     * @return The size adjustment value
     */
    private int getRSKIP351SizeAdjustmentFromExtensionData(long blockNumber, boolean compressed) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP351, blockNumber)) {
            return 1; // Remove version field (not present before RSKIP351)
        }

        if (compressed) {
            // In compressed mode, version, edges, and baseEvent are stored in extension
            int extraHeaderFieldsToRemoveWhenCompressed = NUMBER_OF_EXTRA_HEADER_FIELDS;
            // If PTE deactivated, there is no need to remove edges in the counting
            if (!(activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber) && activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber))) {
                extraHeaderFieldsToRemoveWhenCompressed -= 1;
            }
            // If baseEvent deactivated, there is no need to remove baseEvent in the
            // counting
            if (!isBaseEventEnabled(blockNumber)) {
                extraHeaderFieldsToRemoveWhenCompressed -= 1;
            }
            return extraHeaderFieldsToRemoveWhenCompressed;
        }

        return 0; // Version field is present in full encoding
    }
}
