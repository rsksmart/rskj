package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.util.RLP;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderV1 extends BlockHeader {
    @Override
    public byte getVersion() { return 0x1; }

    private BlockHeaderExtensionV1 extension;
    @Override
    public BlockHeaderExtensionV1 getExtension() { return this.extension; }
    @Override
    public void setExtension(BlockHeaderExtension extension) { this.extension = (BlockHeaderExtensionV1) extension; }

    public BlockHeaderV1(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
                         byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] extensionData, BlockDifficulty difficulty,
                         long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
                         Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                         Coin minimumGasPrice, int uncleCount, boolean sealed,
                         boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot, short[] txExecutionSublistsEdges, boolean compressed) {
        super(parentHash,unclesHash, coinbase, stateRoot,
                txTrieRoot, receiptTrieRoot, compressed ? extensionData : null, difficulty,
                number, gasLimit, gasUsed, timestamp, extraData,
                paidFees, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount, sealed,
                useRskip92Encoding, includeForkDetectionData, ummRoot);
        this.extension = compressed
                ? new BlockHeaderExtensionV1(null, null)
                : new BlockHeaderExtensionV1(extensionData, txExecutionSublistsEdges);
        if(!compressed) {
            this.updateExtensionData(); // update after calculating
        }

    }

    @VisibleForTesting
    public static byte[] createExtensionData(byte[] extensionHash) {
        return RLP.encodeList(
                RLP.encodeByte((byte) 0x1),
                RLP.encodeElement(extensionHash)
        );
    }

    private void updateExtensionData() {
        this.extensionData = BlockHeaderV1.createExtensionData(this.extension.getHash());
    }

    @Override
    public byte[] getLogsBloom() { return this.extension.getLogsBloom(); }

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
    public short[] getTxExecutionSublistsEdges() { return this.extension.getTxExecutionSublistsEdges(); }

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
    public void addExtraFieldsToEncodedHeader(boolean usingCompressedEncoding, List<byte[]> fieldsToEncode) {
        if (!usingCompressedEncoding) {
            fieldsToEncode.add(RLP.encodeByte(this.getVersion()));
            this.addTxExecutionSublistsEdgesIfAny(fieldsToEncode);
        }
    }
}
