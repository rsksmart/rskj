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
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.crypto.ECKey.ECDSASignature;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    private static final int RLP_HEADER_SIZE = 16;
    private static final int RLP_HEADER_SIZE_WITH_MERGED_MINING = 19;

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
        if (block.size() != 3 && block.size() != 4) {
            throw new IllegalArgumentException("A block must have 3 or 4 exactly items");
        }

        RLPList rlpHeader = (RLPList) block.get(0);
        BlockHeader header = decodeHeader(rlpHeader, sealed);

        List<Transaction> transactionList = parseTxs((RLPList) block.get(1));

        RLPList uncleHeadersRlp = (RLPList) block.get(2);
        List<BlockHeader> uncleList = uncleHeadersRlp.stream()
                .map(uncleHeader -> decodeHeader((RLPList) uncleHeader, sealed))
                .collect(Collectors.toList());

        if (block.size() == 4) {
            RLPList signaturesList = (RLPList) block.get(3);
            for (RLPElement signat : signaturesList) {
                // Decode the signature
                RLPList sig = (RLPList)signat;

                BigInteger txIndex = new BigInteger(sig.get(0).getRLPRawData());
                RLPList signature = (RLPList)sig.get(1);
                byte[] r = signature.get(0).getRLPRawData();
                byte[] s = signature.get(1).getRLPRawData();
                byte[] v = signature.get(2).getRLPRawData();

                transactionList.get(txIndex.intValue()).setSignature(ECDSASignature.fromComponents(r, s, v[0]));
            }
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
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                mergedMiningForkDetectionData.length > 0;
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
                null, null, null, new byte[0],
                minimumGasPrice, uncleCount
        );
    }

    public BlockHeader decodeHeader(byte[] encoded) {
        return decodeHeader(RLP.decodeList(encoded), true);
    }

    private BlockHeader decodeHeader(RLPList rlpHeader, boolean sealed) {
        // TODO fix old tests that have other sizes
        if (rlpHeader.size() != RLP_HEADER_SIZE && rlpHeader.size() != RLP_HEADER_SIZE_WITH_MERGED_MINING) {
            throw new IllegalArgumentException(String.format(
                    "A block header must have 16 elements or 19 including merged-mining fields but it had %d",
                    rlpHeader.size()
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
        if (rlpHeader.size() == RLP_HEADER_SIZE || rlpHeader.size() == RLP_HEADER_SIZE_WITH_MERGED_MINING) {
            byte[] ucBytes = rlpHeader.get(r++).getRLPData();
            uncleCount = parseBigInteger(ucBytes).intValueExact();
        }

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        if (rlpHeader.size() > r) {
            bitcoinMergedMiningHeader = rlpHeader.get(r++).getRLPData();
            bitcoinMergedMiningMerkleProof = rlpHeader.get(r++).getRLPRawData();
            bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(r++).getRLPData();
        }

        boolean useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
        boolean includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                number >= MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION;

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
