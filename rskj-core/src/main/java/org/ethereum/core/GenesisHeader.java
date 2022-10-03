package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

public class GenesisHeader extends BlockHeader {

    private final byte[] difficulty;

    public GenesisHeader(byte[] parentHash,
                         byte[] unclesHash,
                         byte[] logsBloom,
                         byte[] difficulty,
                         long number,
                         byte[] gasLimit,
                         long gasUsed,
                         long timestamp,
                         byte[] extraData,
                         byte[] bitcoinMergedMiningHeader,
                         byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction,
                         byte[] minimumGasPrice,
                         boolean useRskip92Encoding,
                         byte[] coinbase,
                         byte[] stateRootHash) {
        super(
                0,
                parentHash,
                unclesHash,
                new RskAddress(coinbase),
                stateRootHash,
                ByteUtils.clone(HashUtil.EMPTY_TRIE_HASH),
                ByteUtils.clone(HashUtil.EMPTY_TRIE_HASH),
                logsBloom,
                RLP.parseBlockDifficulty(difficulty),
                number,
                ByteUtil.stripLeadingZeroes(gasLimit),
                gasUsed,
                timestamp,
                extraData,
                Coin.ZERO,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                new byte[0],
                RLP.parseSignedCoinNonNullZero(minimumGasPrice),
                0,
                false,
                useRskip92Encoding,
                false,
                null);
        this.difficulty = ByteUtils.clone(difficulty);
    }

    public GenesisHeader(byte[] parentHash,
                         byte[] unclesHash,
                         byte[] logsBloom,
                         byte[] difficulty,
                         long number,
                         byte[] gasLimit,
                         long gasUsed,
                         long timestamp,
                         byte[] extraData,
                         byte[] bitcoinMergedMiningHeader,
                         byte[] bitcoinMergedMiningMerkleProof,
                         byte[] bitcoinMergedMiningCoinbaseTransaction,
                         byte[] minimumGasPrice,
                         boolean useRskip92Encoding,
                         byte[] coinbase) {
        super(
                0,
                parentHash,
                unclesHash,
                new RskAddress(coinbase),
                ByteUtils.clone(HashUtil.EMPTY_TRIE_HASH),
                ByteUtils.clone(HashUtil.EMPTY_TRIE_HASH),
                ByteUtils.clone(HashUtil.EMPTY_TRIE_HASH),
                logsBloom,
                RLP.parseBlockDifficulty(difficulty),
                number,
                ByteUtil.stripLeadingZeroes(gasLimit),
                gasUsed,
                timestamp,
                extraData,
                Coin.ZERO,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                new byte[0],
                RLP.parseSignedCoinNonNullZero(minimumGasPrice),
                0,
                false,
                useRskip92Encoding,
                false,
                null);
        this.difficulty = ByteUtils.clone(difficulty);
    }

    @Override
    protected byte[] encodeBlockDifficulty(BlockDifficulty ignored) {
        return RLP.encodeElement(difficulty);
    }
}
