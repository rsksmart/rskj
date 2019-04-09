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
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class BlockFactory {
    public static BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
            byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
            byte[] bitcoinMergedMiningCoinbaseTransaction,
            byte[] minimumGasPrice, int uncleCount) {
        return new BlockHeader(
                parentHash, unclesHash, new RskAddress(coinbase),
                ByteUtils.clone(EMPTY_TRIE_HASH), null, ByteUtils.clone(EMPTY_TRIE_HASH),
                logsBloom, RLP.parseBlockDifficulty(difficulty), number,
                gasLimit, gasUsed, timestamp, extraData, Coin.ZERO,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                RLP.parseSignedCoinNonNullZero(minimumGasPrice), uncleCount, false
        );
    }

    public static BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp,
            byte[] extraData, byte[] minimumGasPrice, int uncleCount) {
        return newHeader(
                parentHash, unclesHash, coinbase, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                null, null, null,
                minimumGasPrice, uncleCount
        );
    }

    public static BlockHeader decodeHeader(byte[] encoded) {
        return decodeHeader(RLP.decodeList(encoded), true);
    }

    public static BlockHeader decodeHeader(RLPList rlpHeader, boolean sealed) {
        // TODO fix old tests that have other sizes
        if (rlpHeader.size() != 19 && rlpHeader.size() != 16) {
            throw new IllegalArgumentException(String.format(
                    "A block header must have 16 elements or 19 including merged-mining fields but it had %d",
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

        int r = 15;

        int uncleCount = 0;
        if ((rlpHeader.size() == 19) || (rlpHeader.size() == 16)) {
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

        return new BlockHeader(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                minimumGasPrice, uncleCount, sealed
        );
    }

    private static BigInteger parseBigInteger(byte[] bytes) {
        return bytes == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(bytes);
    }
}
