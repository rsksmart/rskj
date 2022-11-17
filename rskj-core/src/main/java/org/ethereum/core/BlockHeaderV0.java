/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import org.ethereum.util.RLP;

public class BlockHeaderV0 extends BlockHeader {
    // block header for blocks before rskip351
    @Override
    public byte getVersion() { return 0x0; }
    @Override
    public BlockHeaderExtension getExtension() { return null; }
    @Override
    public void setExtension(BlockHeaderExtension extension) {}

    private byte[] logsBloom;

    public BlockHeaderV0(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
        byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] logsBloom, BlockDifficulty difficulty,
        long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
        Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
        byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
        Coin minimumGasPrice, int uncleCount, boolean sealed,
        boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot, short[] txExecutionSublistsEdges) {
        super(parentHash,unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot, txExecutionSublistsEdges);
        this.logsBloom = logsBloom;
    }

    @Override
    public byte[] getLogsBloom() { return logsBloom; }

    public void setLogsBloom(byte[] logsBloom) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }
        this.hash = null;

        this.logsBloom = logsBloom;
    }

    @Override
    public byte[] getLogsBloomFieldEncoded() {
        return RLP.encodeElement(this.logsBloom);
    }
}
