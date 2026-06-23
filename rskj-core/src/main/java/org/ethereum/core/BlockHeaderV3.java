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

/**
 * Block header version 3: extends {@link BlockHeaderV2} with {@code forkBalanceProof} in the
 * block header extension ({@link BlockHeaderExtensionV3}).
 */
public class BlockHeaderV3 extends BlockHeaderV2 {

    public BlockHeaderV3(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot, byte[] txTrieRoot,
                          byte[] receiptTrieRoot, byte[] extensionData, BlockDifficulty difficulty, long number,
                          byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData, Coin paidFees,
                          byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                          byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                          Coin minimumGasPrice, int uncleCount, boolean sealed, boolean useRskip92Encoding,
                          boolean includeForkDetectionData, byte[] ummRoot, byte[] baseEvent, byte[] forkBalanceProof,
                          short[] txExecutionSublistsEdges, boolean compressed) {
        super(
                parentHash, unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, extensionData, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData, paidFees,
                bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot,
                makeExtension(compressed, extensionData, txExecutionSublistsEdges, baseEvent, forkBalanceProof), compressed);
    }

    private static BlockHeaderExtensionV3 makeExtension(
            boolean compressed,
            byte[] extensionData,
            short[] txExecutionSublistsEdges,
            byte[] baseEvent,
            byte[] forkBalanceProof) {
        return compressed
                ? new BlockHeaderExtensionV3(null, null, null, null)
                : new BlockHeaderExtensionV3(extensionData, txExecutionSublistsEdges, baseEvent, forkBalanceProof);
    }

    @VisibleForTesting
    public static byte[] createExtensionData(byte[] extensionHash) {
        return RLP.encodeList(
                RLP.encodeByte((byte) 0x3),
                RLP.encodeElement(extensionHash)
        );
    }

    @Override
    public byte getVersion() {
        return 0x3;
    }

    @Override
    public byte[] getForkBalanceProof() {
        return this.getExtension().getForkBalanceProof();
    }

    @Override
    public void setForkBalanceProof(byte[] forkBalanceProof) {
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter forkBalanceProof data of a sealed block");
        }
        if (forkBalanceProof != null && forkBalanceProof.length > BlockHeaderExtensionV3.FORK_BALANCE_PROOF_MAX_SIZE) {
            throw new FieldMaxSizeBlockHeaderException(
                    "forkBalanceProof length cannot exceed " + BlockHeaderExtensionV3.FORK_BALANCE_PROOF_MAX_SIZE
                            + " bytes");
        }
        this.hash = null;
        this.getExtension().setForkBalanceProof(forkBalanceProof);
        this.updateExtensionData();
    }

    @Override
    public BlockHeaderExtensionV3 getExtension() {
        return (BlockHeaderExtensionV3) super.getExtension();
    }

    @Override
    protected void updateExtensionData() {
        this.extensionData = BlockHeaderV3.createExtensionData(this.getExtension().getHash());
    }

    @Override
    public void addExtraFieldsToEncodedHeader(boolean usingCompressedEncoding, List<byte[]> fieldsToEncode) {
        super.addExtraFieldsToEncodedHeader(usingCompressedEncoding, fieldsToEncode);
        if (!usingCompressedEncoding) {
            addForkBalanceProof(fieldsToEncode);
        }
    }

    private void addForkBalanceProof(List<byte[]> fieldsToEncode) {
        byte[] forkBalanceProof = this.getForkBalanceProof();
        if (forkBalanceProof != null) {
            fieldsToEncode.add(RLP.encodeElement(forkBalanceProof));
        }
    }
}
