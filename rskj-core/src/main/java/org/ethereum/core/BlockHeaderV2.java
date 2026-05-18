/*
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
import org.ethereum.core.exception.FieldMaxSizeBlockHeaderException;
import org.ethereum.core.exception.SealedBlockHeaderException;
import org.ethereum.util.RLP;

import java.util.List;

public class BlockHeaderV2 extends BlockHeaderV1 {

    public static final int BASE_EVENT_MAX_SIZE = 128;

    public BlockHeaderV2(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot, byte[] txTrieRoot,
                         byte[] receiptTrieRoot, byte[] extensionData, BlockDifficulty difficulty, long number,
                         byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData, Coin paidFees,
                         byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                         Coin minimumGasPrice, int uncleCount, boolean sealed, boolean useRskip92Encoding,
                         boolean includeForkDetectionData, byte[] ummRoot, byte[] baseEvent,
                         short[] txExecutionSublistsEdges, boolean compressed) {
        super(parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, compressed ? extensionData : null, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot,
                makeExtension(compressed, extensionData, txExecutionSublistsEdges, baseEvent), compressed);
    }

    private static BlockHeaderExtensionV2 makeExtension(boolean compressed,
                                                        byte[] extensionData,
                                                        short[] txExecutionSublistsEdges,
                                                        byte[] baseEvent) {
        return compressed
                ? new BlockHeaderExtensionV2(null, null, null)
                : new BlockHeaderExtensionV2(extensionData, txExecutionSublistsEdges, baseEvent);
    }

    @VisibleForTesting
    public static byte[] createExtensionData(byte[] extensionHash) {
        return RLP.encodeList(
                RLP.encodeByte((byte) 0x2),
                RLP.encodeElement(extensionHash)
        );
    }

    @Override
    public byte getVersion() {
        return 0x2;
    }

    @Override
    public byte[] getBaseEvent() {
        return this.getExtension().getBaseEvent();
    }

    @Override
    public void setBaseEvent(byte[] baseEvent) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter baseEvent data of a sealed block");
        }

        if (baseEvent.length > BASE_EVENT_MAX_SIZE) {
            throw new FieldMaxSizeBlockHeaderException(String.format("baseEvent length cannot exceed %d bytes", BASE_EVENT_MAX_SIZE));
        }
        this.hash = null;

        this.getExtension().setBaseEvent(baseEvent);
        this.updateExtensionData();
    }

    @Override
    public BlockHeaderExtensionV2 getExtension() {
        return (BlockHeaderExtensionV2) super.getExtension();
    }

    @Override
    public void setExtension(BlockHeaderExtension extension) {
        super.setExtension(extension);
    }

    @Override
    protected void updateExtensionData() {
        this.extensionData = BlockHeaderV2.createExtensionData(this.getExtension().getHash());
    }

    @Override
    public void addExtraFieldsToEncodedHeader(boolean usingCompressedEncoding, List<byte[]> fieldsToEncode) {
        super.addExtraFieldsToEncodedHeader(usingCompressedEncoding, fieldsToEncode);
        if (!usingCompressedEncoding) {
            addBaseEvent(fieldsToEncode);
        }
    }
}
