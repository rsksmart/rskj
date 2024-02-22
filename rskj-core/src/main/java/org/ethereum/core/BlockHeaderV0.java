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

import java.util.Arrays;
import java.util.List;

public class BlockHeaderV0 extends BlockHeader {
    // block header for blocks before rskip351
    @Override
    public byte getVersion() { return 0x0; }
    @Override
    public BlockHeaderExtension getExtension() { return null; } // block header v0 has no extension
    @Override
    public void setExtension(BlockHeaderExtension extension) {
        // block header v0 has no extension
    }

    private short[] txExecutionSublistsEdges;

    public BlockHeaderV0(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
        byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] logsBloom, BlockDifficulty difficulty,
        long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
        Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
        byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
        Coin minimumGasPrice, int uncleCount, boolean sealed,
        boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot, short[] txExecutionSublistsEdges) {
        super(parentHash,unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, logsBloom, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot);
        this.txExecutionSublistsEdges = txExecutionSublistsEdges != null ? Arrays.copyOf(txExecutionSublistsEdges, txExecutionSublistsEdges.length) : null;
    }

    // logs bloom is stored in the extension data
    @Override
    public byte[] getLogsBloom() { return extensionData; }
    @Override
    public void setLogsBloom(byte[] logsBloom) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }
        this.hash = null;

        this.extensionData = logsBloom;
    }

    @Override
    public short[] getTxExecutionSublistsEdges() { return this.txExecutionSublistsEdges != null ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length) : null; }

    @Override
    public void setTxExecutionSublistsEdges(short[] edges) {
        this.txExecutionSublistsEdges =  edges != null? Arrays.copyOf(edges, edges.length) : null;
    }

    @Override
    public void addExtraFieldsToEncodedHeader(boolean usingCompressedEncoding, List<byte[]> fieldsToEncode) {
        // adding edges to
        // 1. keep RSKIP 351 and RSKIP 144 independent
        //    either can be activated at any height independently
        // 2. keep compressed encoding the same as uncompressed
        //    since this difference should not exist on v0
        this.addTxExecutionSublistsEdgesIfAny(fieldsToEncode);
    }
}
