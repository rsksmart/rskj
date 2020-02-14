/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.Utils;
import javax.annotation.Nullable;
import java.math.BigInteger;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.toHexString;

public class BlockHeaderBuilder {

    private static final byte[] emptyUncleHashList = HashUtil.keccak256(RLP.encodeList(new byte[0]));

    /* The SHA3 256-bit hash of the parent block, in its entirety */
    private byte[] parentHash;
    /* The SHA3 256-bit hash of the uncles list portion of this block */
    private byte[] unclesHash;

    /* The 160-bit address to which all fees collected from the
     * successful mining of this block be transferred; formally */
    private RskAddress coinbase;

    /* The SHA3 256-bit hash of the root node of the state trie,
     * after all transactions are executed and finalisations applied */
    private byte[] stateRoot;
    /* The SHA3 256-bit hash of the root node of the trie structure
     * populated with each transaction in the transaction
     * list portion, the trie is populate by [key, val] --> [rlp(index), rlp(tx_recipe)]
     * of the block */
    private byte[] txTrieRoot;
    /* The SHA3 256-bit hash of the root node of the trie structure
     * populated with each transaction recipe in the transaction recipes
     * list portion, the trie is populate by [key, val] --> [rlp(index), rlp(tx_recipe)]
     * of the block */
    private byte[] receiptTrieRoot;
    /* The bloom filter for the logs of the block */
    private byte[] logsBloom;
    /**
     * A scalar value corresponding to the difficulty level of this block.
     * This can be calculated from the previous block’s difficulty level
     * and the timestamp.
     */
    private BlockDifficulty difficulty;
    /* A scalar value equalBytes to the reasonable output of Unix's time()
     * at this block's inception */
    private long timestamp;
    /* A scalar value equalBytes to the number of ancestor blocks.
     * The genesis block has a number of zero */
    private long number;
    /* A scalar value equalBytes to the current limit of gas expenditure per block */
    private byte[] gasLimit;
    /* A scalar value equalBytes to the total gas used in transactions in this block */
    private long gasUsed;
    /* A scalar value equalBytes to the total paid fees in transactions in this block */
    private Coin paidFees;

    /* An arbitrary byte array containing data relevant to this block.
     * With the exception of the genesis block, this must be 32 bytes or fewer */
    private byte[] extraData;

    /* The 80-byte bitcoin block header for merged mining */
    private byte[] bitcoinMergedMiningHeader;
    /* The bitcoin merkle proof of coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningMerkleProof;
    /* The bitcoin protobuf serialized coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningCoinbaseTransaction;

    private byte[] mergedMiningForkDetectionData;

    private byte[] mergeMiningRightHash; // this can be an empty byte[] vector
    /**
     * The mgp for a tx to be included in the block.
     */
    private Coin minimumGasPrice;
    private int uncleCount;

    /* Indicates if the block was mined according to RSKIP-92 rules */
    private boolean useRskip92Encoding;

    /* Indicates if Block hash for merged mining should have the format described in RSKIP-110 */
    private boolean includeForkDetectionData;
    private ActivationConfig activationConfig;

    public BlockHeaderBuilder(ActivationConfig activationConfig) {
        this.activationConfig =activationConfig;
    }

    public int getUncleCount() {
        return uncleCount;
    }

    public byte[] getUnclesHash() {
        return unclesHash;
    }

    public RskAddress getCoinbase() {
        return this.coinbase;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public BlockHeaderBuilder setStateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
        return this;
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot;
    }

    public BlockHeaderBuilder setReceiptsRoot(byte[] receiptTrieRoot) {
        this.receiptTrieRoot = receiptTrieRoot;
        return this;
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot;
    }

    public BlockHeaderBuilder setTransactionsRoot(byte[] stateRoot) {
        this.txTrieRoot = stateRoot;
        return this;
    }


    public byte[] getLogsBloom() {
        return logsBloom;
    }

    public BlockDifficulty getDifficulty() {
        // some blocks have zero encoded as null, but if we altered the internal field then re-encoding the value would
        // give a different value than the original.
        if (difficulty == null) {
            return BlockDifficulty.ZERO;
        }

        return difficulty;
    }

    public BlockHeaderBuilder setDifficulty(BlockDifficulty difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getNumber() {
        return number;
    }

    public byte[] getGasLimit() {
        return gasLimit;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public BlockHeaderBuilder setPaidFees(Coin paidFees) {
        this.paidFees = paidFees;
        return this;
    }

    public Coin getPaidFees() {
        return this.paidFees;
    }

    public BlockHeaderBuilder setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
        return this;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public BlockHeaderBuilder setLogsBloom(byte[] logsBloom) {
        this.logsBloom = logsBloom;
        return this;
    }

    @Nullable
    public Coin getMinimumGasPrice() {
        return this.minimumGasPrice;
    }


    public boolean hasMiningFields() {
        if (this.bitcoinMergedMiningCoinbaseTransaction != null && this.bitcoinMergedMiningCoinbaseTransaction.length > 0) {
            return true;
        }

        if (this.bitcoinMergedMiningHeader != null && this.bitcoinMergedMiningHeader.length > 0) {
            return true;
        }

        if (this.bitcoinMergedMiningMerkleProof != null && this.bitcoinMergedMiningMerkleProof.length > 0) {
            return true;
        }

        return false;
    }

    public String toString() {
        return toStringWithSuffix("\n");
    }

    private String toStringWithSuffix(final String suffix) {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.append("  parentHash=").append(toHexString(parentHash)).append(suffix);
        toStringBuff.append("  unclesHash=").append(toHexString(unclesHash)).append(suffix);
        toStringBuff.append("  coinbase=").append(coinbase).append(suffix);
        toStringBuff.append("  stateRoot=").append(toHexString(stateRoot)).append(suffix);
        toStringBuff.append("  txTrieHash=").append(toHexString(txTrieRoot)).append(suffix);
        toStringBuff.append("  receiptsTrieHash=").append(toHexString(receiptTrieRoot)).append(suffix);
        toStringBuff.append("  difficulty=").append(difficulty).append(suffix);
        toStringBuff.append("  number=").append(number).append(suffix);
        toStringBuff.append("  gasLimit=").append(toHexString(gasLimit)).append(suffix);
        toStringBuff.append("  gasUsed=").append(gasUsed).append(suffix);
        toStringBuff.append("  timestamp=").append(timestamp).append(" (").append(Utils.longToDateTime(timestamp)).append(")").append(suffix);
        toStringBuff.append("  extraData=").append(toHexString(extraData)).append(suffix);
        toStringBuff.append("  minGasPrice=").append(minimumGasPrice).append(suffix);

        return toStringBuff.toString();
    }

    public String toFlatString() {
        return toStringWithSuffix("");
    }

    public byte[] getBitcoinMergedMiningHeader() {
        return bitcoinMergedMiningHeader;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        this.bitcoinMergedMiningHeader = bitcoinMergedMiningHeader;
        return this;
    }

    public byte[] getBitcoinMergedMiningMerkleProof() {
        return bitcoinMergedMiningMerkleProof;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningMerkleProof(byte[] bitcoinMergedMiningMerkleProof) {
        this.bitcoinMergedMiningMerkleProof = bitcoinMergedMiningMerkleProof;
        return this;
    }

    public byte[] getBitcoinMergedMiningCoinbaseTransaction() {
        return bitcoinMergedMiningCoinbaseTransaction;
    }

    public BlockHeaderBuilder setBitcoinMergedMiningCoinbaseTransaction(byte[] bitcoinMergedMiningCoinbaseTransaction) {
        this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;
        return this;
    }


    public byte[] getMergeMiningRightHash() {
        return mergeMiningRightHash;
    }

    public byte[] getMergedMiningForkDetectionData() {
        return mergedMiningForkDetectionData;
    }

    public BlockHeaderBuilder setTxTrieRoot(byte[] txTrieRoot) {
        this.txTrieRoot = txTrieRoot;
        return this;
    }
    public BlockHeaderBuilder setEmptyTxTrieRoot() {
        this.txTrieRoot = EMPTY_TRIE_HASH;
        return this;
    }
    public byte[] getReceiptTrieRoot() {
        return receiptTrieRoot;
    }

    public BlockHeaderBuilder setReceiptTrieRoot(byte[] receiptTrieRoot) {
        this.receiptTrieRoot = receiptTrieRoot;
        return this;
    }

    public BlockHeaderBuilder setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public BlockHeaderBuilder setNumber(long number) {
        this.number = number;
        return this;
    }

    public BlockHeaderBuilder setGasLimit(byte[] gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public BlockHeaderBuilder setExtraData(byte[] extraData) {
        this.extraData = extraData;
        return this;
    }

    public BlockHeaderBuilder setMergedMiningForkDetectionData(byte[] mergedMiningForkDetectionData) {
        this.mergedMiningForkDetectionData = mergedMiningForkDetectionData;
        return this;
    }

    public BlockHeaderBuilder setMergeMiningRightHash(byte[] mergeMiningRightHash) {
        this.mergeMiningRightHash = mergeMiningRightHash;
        return this;
    }

    public BlockHeaderBuilder setMinimumGasPrice(Coin minimumGasPrice) {
        this.minimumGasPrice = minimumGasPrice;
        return this;
    }

    public BlockHeaderBuilder setUncleCount(int uncleCount) {
        this.uncleCount = uncleCount;
        return this;
    }


    public boolean isUseRskip92Encoding() {
        return useRskip92Encoding;
    }

    public BlockHeaderBuilder setUseRskip92Encoding(boolean useRskip92Encoding) {
        this.useRskip92Encoding = useRskip92Encoding;
        return this;
    }

    public boolean isIncludeForkDetectionData() {
        return includeForkDetectionData;
    }

    public BlockHeaderBuilder setIncludeForkDetectionData(boolean includeForkDetectionData) {
        this.includeForkDetectionData = includeForkDetectionData;
        return this;
    }

    public byte[] getParentHash() {
        return parentHash;
    }

    public BlockHeaderBuilder setParentHashFromKeccak256(Keccak256 parentHash) {
        this.parentHash = parentHash.getBytes();
        return this;
    }

    public BlockHeaderBuilder setParentHash(byte[] parentHash) {
        this.parentHash = parentHash;
        return this;
    }

    public BlockHeaderBuilder setEmptyUnclesHash() {
        this.unclesHash = emptyUncleHashList;
        return this;
    }

    public BlockHeaderBuilder setUnclesHash(byte[] unclesHash) {
        this.unclesHash = unclesHash;
        return this;
    }
    public BlockHeaderBuilder setCoinbase(RskAddress coinbase) {
        this.coinbase = coinbase;
        return this;
    }

    public BlockHeaderBuilder setGasLimitMaxValue() {
        this.gasLimit = ByteUtil.longToBytesNoLeadZeroes(Long.MAX_VALUE);
        return this;
    }

    public BlockHeaderBuilder setDifficultyFromBytes(@Nullable byte[]data) {
        // This is to make it compatible with RLP.parseBlockDifficulty() which was previously
        // user (but I think it was wrongly included in the RLP class, because these arguments
        // do not come from any RLP parsing).
        if (data!=null)
            difficulty= new BlockDifficulty(new BigInteger(data));
        else
            difficulty=null;
        return this;
    }

    public BlockHeaderBuilder setEmptyMergedMiningForkDetectionData() {
        mergedMiningForkDetectionData =  new byte[12];
        return this;
    }

    public BlockHeaderBuilder setEmptyExtraData() {
        extraData = new byte[]{};
        return this;
    }

    public BlockHeaderBuilder setEmptyLogsBloom() {
        logsBloom = new Bloom().getData();
        return this;
    }
    public BlockHeaderBuilder setEmptyStateRoot() {
        stateRoot = EMPTY_TRIE_HASH;
        return this;
    }
    public BlockHeaderBuilder setEmptyReceiptTrieRoot() {
        receiptTrieRoot = EMPTY_TRIE_HASH;
        return this;
    }

    private void normalize() {
        if (extraData==null)
            extraData= new byte[]{};
        if (bitcoinMergedMiningHeader==null)
            bitcoinMergedMiningHeader=new byte[]{};
        if (bitcoinMergedMiningMerkleProof==null)
            bitcoinMergedMiningMerkleProof = new byte[]{};
        if (bitcoinMergedMiningCoinbaseTransaction==null)
            bitcoinMergedMiningCoinbaseTransaction =new byte[]{};

        if (unclesHash==null)
            unclesHash = emptyUncleHashList;
        if (coinbase==null )
            coinbase=new RskAddress(new byte[20]); // zero address
        if (stateRoot==null)
            stateRoot= new byte[32];
        if (txTrieRoot==null)
            txTrieRoot =EMPTY_TRIE_HASH;
        if (receiptTrieRoot==null)
            receiptTrieRoot = EMPTY_TRIE_HASH; // new byte[32];
        if (logsBloom==null)
            logsBloom =new byte[32];
        if (paidFees==null)
            paidFees = Coin.ZERO;
        if (minimumGasPrice==null)
            minimumGasPrice = Coin.ZERO;
        if (mergedMiningForkDetectionData==null)
                mergedMiningForkDetectionData = new byte[]{};
    }
    public BlockHeader build(boolean createConsensusComplaintHeader,boolean createUMMComplaintHeader) {
        // Initial null values in some fields are replaced by empty
        // arrays
        normalize();
        if (createConsensusComplaintHeader) {
            useRskip92Encoding = activationConfig.isActive(ConsensusRule.RSKIP92, number);
            includeForkDetectionData = activationConfig.isActive(ConsensusRule.RSKIP110, number) &&
                    mergedMiningForkDetectionData.length > 0;
        }

        if (createUMMComplaintHeader) {
            if (activationConfig.isActive(ConsensusRule.RSKIPUMM, number)) {
                if (mergeMiningRightHash==null) {
                    mergeMiningRightHash = new byte[0];
                }
            }
        }


        return new BlockHeader(
                parentHash, unclesHash, coinbase,
                stateRoot, txTrieRoot, receiptTrieRoot,
                logsBloom, difficulty, number,
                gasLimit, gasUsed, timestamp, extraData, paidFees,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                mergedMiningForkDetectionData,
                minimumGasPrice, uncleCount,
                false, useRskip92Encoding,
                includeForkDetectionData,
                mergeMiningRightHash);
       }
}
