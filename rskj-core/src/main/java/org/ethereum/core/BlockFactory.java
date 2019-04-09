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

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class BlockFactory {
    public static BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp,
            byte[] extraData,
            byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
            byte[] bitcoinMergedMiningCoinbaseTransaction,
            byte[] minimumGasPrice,
            int uncleCount, boolean sealed) {
        return new BlockHeader(
                parentHash, unclesHash, coinbase, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof, bitcoinMergedMiningCoinbaseTransaction,
                minimumGasPrice, uncleCount, sealed
        );
    }

    public static BlockHeader newHeader(
            byte[] parentHash, byte[] unclesHash, byte[] coinbase,
            byte[] logsBloom, byte[] difficulty, long number,
            byte[] gasLimit, long gasUsed, long timestamp,
            byte[] extraData,
            byte[] minimumGasPrice,
            int uncleCount, boolean sealed) {
        return newHeader(
                parentHash, unclesHash, coinbase, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                null, null, null,
                minimumGasPrice, uncleCount, sealed
        );
    }

    public static BlockHeader newHeader(byte[] encoded, boolean sealed) {
        return newHeader(RLP.decodeList(encoded), sealed);
    }

    public static BlockHeader newHeader(RLPList rlpHeader, boolean sealed) {
        return new BlockHeader(rlpHeader, sealed);
    }
}
