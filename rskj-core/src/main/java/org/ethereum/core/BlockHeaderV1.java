/*Add a comment on lines R1 to R18Add diff commentMarkdown input:  edit mode selected.WritePreviewAdd a suggestionHeadingBoldItalicQuoteCodeLinkUnordered listNumbered listTask listMentionReferenceSaved repliesAdd FilesPaste, drop, or click to add filesCancelCommentStart a reviewReturn to code
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.exception.SealedBlockHeaderException;
import org.ethereum.util.RLP;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderV1 extends BlockHeader {
    private BlockHeaderExtensionV1 extension;

    public BlockHeaderV1(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
                         byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] extensionData, BlockDifficulty difficulty,
                         long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
                         Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                         Coin minimumGasPrice, int uncleCount, boolean sealed,
                         boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot, short[] txExecutionSublistsEdges, boolean compressed) {
        this(parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, compressed ? extensionData : null, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot,
                makeExtension(compressed, extensionData, txExecutionSublistsEdges), compressed);
    }

    public BlockHeaderV1(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
                         byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] extensionData, BlockDifficulty difficulty,
                         long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
                         Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                         Coin minimumGasPrice, int uncleCount, boolean sealed,
                         boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot,
                         BlockHeaderExtensionV1 extension, boolean compressed) {
        super(parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, compressed ? extensionData : null, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot);
        this.extension = extension;
        if (!compressed) {
            // update after calculating
            this.extensionData = createExtensionData(extension);
        }
    }

    @VisibleForTesting
    public static byte[] createExtensionData(BlockHeaderExtension extension) {
        return RLP.encodeList(
                RLP.encodeByte(extension.getVersion()),
                RLP.encodeElement(extension.getHash())
        );
    }

    private static BlockHeaderExtensionV1 makeExtension(boolean compressed,
                                                        byte[] extensionData,
                                                        short[] txExecutionSublistsEdges) {
        return compressed
                ? new BlockHeaderExtensionV1(null, null)
                : new BlockHeaderExtensionV1(extensionData, txExecutionSublistsEdges);
    }

    @Override
    public byte getVersion() {
        return 0x1;
    }

    @Override
    public BlockHeaderExtensionV1 getExtension() {
        return this.extension;
    }

    @Override
    public void setExtension(BlockHeaderExtension extension) {
        this.extension = (BlockHeaderExtensionV1) extension;
    }

    protected void updateExtensionData() {
        this.extensionData = createExtensionData(this.extension);
    }

    @Override
    public byte[] getLogsBloom() {
        return this.extension.getLogsBloom();
    }

    @Override
    public void setLogsBloom(byte[] logsBloom) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }
        this.hash = null;

        this.extension.setLogsBloom(logsBloom);
        this.updateExtensionData();
    }

    @Override
    public short[] getTxExecutionSublistsEdges() {
        return this.extension.getTxExecutionSublistsEdges();
    }

    @Override
    public void setTxExecutionSublistsEdges(short[] edges) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter edges");
        }
        this.hash = null;

        this.extension.setTxExecutionSublistsEdges(edges != null ? Arrays.copyOf(edges, edges.length) : null);
        this.updateExtensionData();
    }

    @Override
    public byte[] getBaseEvent() {
        return null;
    }

    @Override
    public void setBaseEvent(byte[] baseEvent) {
        if (baseEvent != null) {
            throw new UnsupportedOperationException("Block header v1 does not support base event");
        }
    }

    @Override
    public void addExtraFieldsToEncodedHeader(boolean usingCompressedEncoding, List<byte[]> fieldsToEncode) {
        if (!usingCompressedEncoding) {
            fieldsToEncode.add(RLP.encodeByte(this.getVersion()));
            this.addTxExecutionSublistsEdgesIfAny(fieldsToEncode);
        }
    }
}
