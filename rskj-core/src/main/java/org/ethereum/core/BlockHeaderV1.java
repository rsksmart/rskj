package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.util.RLP;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderV1 extends BlockHeader {
    @Override
    public byte getVersion() { return 0x1; }

    private BlockHeaderExtensionV1 extension;
    public BlockHeaderExtensionV1 getExtension() { return this.extension; }
    public void setExtension(BlockHeaderExtension extension) { this.extension = (BlockHeaderExtensionV1) extension; }

    public BlockHeaderV1(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
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
                useRskip92Encoding, includeForkDetectionData, ummRoot);

        this.extension = new BlockHeaderExtensionV1(logsBloom, txExecutionSublistsEdges);
    }

    @Override
    public byte[] getLogsBloom() {
        return this.extension.getLogsBloom();
    }

    public void setLogsBloom(byte[] logsBloom) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }
        this.hash = null;

        this.extension.setLogsBloom(logsBloom);
    }

    @Override
    public short[] getTxExecutionSublistsEdges() { return this.extension.getTxExecutionSublistsEdges(); }

    @Override
    public void setTxExecutionSublistsEdges(short[] edges) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }
        this.hash = null;

        this.extension.setTxExecutionSublistsEdges(edges != null ? Arrays.copyOf(edges, edges.length) : null);
    }

    @Override
    public byte[] getLogsBloomFieldEncoded() {
        byte[] ecnoded = new byte[Bloom.BLOOM_BYTES];
        ecnoded[0] = 0x1;
        byte[] hash = this.extension.getHash();
        System.arraycopy(hash, 0, ecnoded, 1, hash.length);
        return RLP.encodeElement(ecnoded);
    }

    @Override
    public void addExtraFieldsToEncoded(boolean useExtensionEncoding, List<byte[]> fieldsToEncode) {
        if (!useExtensionEncoding) {
            this.addTxExecutionSublistsEdgesIfAny(fieldsToEncode);
        }
    }


}
