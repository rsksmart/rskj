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
import com.google.common.primitives.Bytes;
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

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int RLP_HEADER_SIZE = 17;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING = 20;

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
        BlockHeader header = decodeHeader(rlpHeader, sealed);

        List<Transaction> transactionList = parseTxs((RLPList) block.get(1));

        RLPList uncleHeadersRlp = (RLPList) block.get(2);

        List<BlockHeader> uncleList = new ArrayList<>();

        for (int k = 0; k < uncleHeadersRlp.size(); k++) {
            RLPElement element = uncleHeadersRlp.get(k);
            BlockHeader uncleHeader = decodeHeader((RLPList)element, sealed);
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

    public BlockHeader decodeHeader(byte[] encoded) {
        return decodeHeader(RLP.decodeList(encoded), true);
    }

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean sealed) {
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

        long blockNumber = parseBigInteger(nrBytes).longValueExact();

        long gasUsed = parseBigInteger(guBytes).longValueExact();
        long timestamp = parseBigInteger(tsBytes).longValueExact();

        byte[] extraData = rlpHeader.get(12).getRLPData();

        Coin paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        byte[] minimumGasPriceBytes = rlpHeader.get(14).getRLPData();
        Coin minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPriceBytes);

        if (!canBeDecoded(rlpHeader, blockNumber)) {
            throw new IllegalArgumentException(String.format(
                    "A block header must have 16/17 elements or 19/20 including merged-mining fields but it had %d",
                    rlpHeader.size()
            ));
        }

        int r = 15;

        boolean isUmm = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber);

        boolean includeUncleCount = isUmm ||
            // sizes prior to UMM activation
            rlpHeader.size() == (RLP_HEADER_SIZE-1) || rlpHeader.size() == (RLP_HEADER_SIZE_WITH_MERGED_MINING-1);

        int uncleCount = 0;
        if (includeUncleCount) {
            byte[] ucBytes = rlpHeader.get(r++).getRLPData();
            uncleCount = parseBigInteger(ucBytes).intValueExact();
        }

        byte[] ummRoot = null;
        if (isUmm) {
            ummRoot = rlpHeader.get(r++).getRLPRawData();
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

        if (blockNumber == Genesis.NUMBER) {
            return new GenesisHeader(
                    parentHash,
                    unclesHash,
                    logsBloom,
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

        return new BlockHeader(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                blockNumber, glBytes, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, new byte[0],
                minimumGasPrice, uncleCount, sealed, useRskip92Encoding, includeForkDetectionData,
                ummRoot
        );
    }

    private boolean canBeDecoded(RLPList rlpHeader, long blockNumber) {
        int preUmmHeaderSizeAdjustment = activationConfig.isActive(ConsensusRule.RSKIPUMM, blockNumber) ? 0 : 1;

        return rlpHeader.size() == (RLP_HEADER_SIZE - preUmmHeaderSizeAdjustment) ||
            rlpHeader.size() == (RLP_HEADER_SIZE_WITH_MERGED_MINING - preUmmHeaderSizeAdjustment);
    }

    private static BigInteger parseBigInteger(byte[] bytes) {
        return bytes == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(bytes);
    }

    private static List<Transaction> parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();
        byte[] typePrefix = new byte[]{};
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            final byte[] rlpData = transactionRaw.getRLPData();
            if(rlpData.length == 1){
                typePrefix = new byte[]{rlpData[0]};
                continue;
            }

            Transaction tx = new ImmutableTransaction(Bytes.concat(typePrefix, rlpData));

            if (tx.isRemascTransaction(i, txTransactions.size())) {
                // It is the remasc transaction
                tx = new RemascTransaction(rlpData);
            }
            parsedTxs.add(tx);
            if(typePrefix.length > 0) {
                typePrefix = new byte[]{};
            }
        }

        return Collections.unmodifiableList(parsedTxs);
    }
}
