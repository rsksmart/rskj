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
import co.rsk.crypto.Keccak256;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.Utils;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.System.arraycopy;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Block header is a value object containing
 * the basic information of a block
 */
public class BlockHeader {

    private static final int HASH_FOR_MERGED_MINING_PREFIX_LENGTH = 20;
    private static final int FORK_DETECTION_DATA_LENGTH = 12;
    private static final int UMM_LEAVES_LENGTH = 20;

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

    private byte[] miningForkDetectionData;

    private byte[] ummRoot;

    /**
     * The mgp for a tx to be included in the block.
     */
    private Coin minimumGasPrice;
    private int uncleCount;

    /* Indicates if this block header cannot be changed */
    private volatile boolean sealed;

    /* Indicates if the block was mined according to RSKIP-92 rules */
    private boolean useRskip92Encoding;

    /* Indicates if Block hash for merged mining should have the format described in RSKIP-110 */
    private boolean includeForkDetectionData;

    public BlockHeader(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
                       byte[] txTrieRoot, byte[] receiptTrieRoot, byte[] logsBloom, BlockDifficulty difficulty,
                       long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
                       Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                       byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                       Coin minimumGasPrice, int uncleCount, boolean sealed,
                       boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot) {
        this.parentHash = parentHash;
        this.unclesHash = unclesHash;
        this.coinbase = coinbase;
        this.stateRoot = stateRoot;
        this.txTrieRoot = txTrieRoot;
        this.receiptTrieRoot = receiptTrieRoot;
        this.logsBloom = logsBloom;
        this.difficulty = difficulty;
        this.number = number;
        this.gasLimit = gasLimit;
        this.gasUsed = gasUsed;
        this.timestamp = timestamp;
        this.extraData = extraData;
        this.minimumGasPrice = minimumGasPrice;
        this.uncleCount = uncleCount;
        this.paidFees = paidFees;
        this.bitcoinMergedMiningHeader = bitcoinMergedMiningHeader;
        this.bitcoinMergedMiningMerkleProof = bitcoinMergedMiningMerkleProof;
        this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;
        this.miningForkDetectionData =
                Arrays.copyOf(mergedMiningForkDetectionData, mergedMiningForkDetectionData.length);
        this.sealed = sealed;
        this.useRskip92Encoding = useRskip92Encoding;
        this.includeForkDetectionData = includeForkDetectionData;
        this.ummRoot = ummRoot != null ? Arrays.copyOf(ummRoot, ummRoot.length) : null;
    }

    @VisibleForTesting
    public boolean isSealed() {
        return this.sealed;
    }

    public void seal() {
        this.sealed = true;
    }

    public boolean isGenesis() {
        return this.getNumber() == Genesis.NUMBER;
    }

    public Keccak256 getParentHash() {
        return new Keccak256(parentHash);
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

    public void setStateRoot(byte[] stateRoot) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter state root");
        }

        this.stateRoot = stateRoot;
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot;
    }

    public void setReceiptsRoot(byte[] receiptTrieRoot) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter receipts root");
        }

        this.receiptTrieRoot = receiptTrieRoot;
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot;
    }

    public void setTransactionsRoot(byte[] stateRoot) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter transactions root");
        }

        this.txTrieRoot = stateRoot;
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

    public void setDifficulty(BlockDifficulty difficulty) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter difficulty");
        }

        this.difficulty = difficulty;
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

    public void setPaidFees(Coin paidFees) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter paid fees");
        }

        this.paidFees = paidFees;
    }

    public Coin getPaidFees() {
        return this.paidFees;
    }

    public void setGasUsed(long gasUsed) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter gas used");
        }

        this.gasUsed = gasUsed;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public void setLogsBloom(byte[] logsBloom) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter logs bloom");
        }

        this.logsBloom = logsBloom;
    }

    public Keccak256 getHash() {
        return new Keccak256(HashUtil.keccak256(getEncoded()));
    }

    public byte[] getFullEncoded() {
        // the encoded block header must include all fields, even the bitcoin PMT and coinbase which are not used for
        // calculating RSKIP92 block hashes
        return this.getEncoded(true, true);
    }

    public byte[] getEncoded() {
        // the encoded block header used for calculating block hashes including RSKIP92
        return this.getEncoded(true, !useRskip92Encoding);
    }

    @Nullable
    public Coin getMinimumGasPrice() {
        return this.minimumGasPrice;
    }

    public byte[] getEncoded(boolean withMergedMiningFields, boolean withMerkleProofAndCoinbase) {
        byte[] parentHash = RLP.encodeElement(this.parentHash);

        byte[] unclesHash = RLP.encodeElement(this.unclesHash);
        byte[] coinbase = RLP.encodeRskAddress(this.coinbase);

        byte[] stateRoot = RLP.encodeElement(this.stateRoot);

        if (txTrieRoot == null) {
            this.txTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] txTrieRoot = RLP.encodeElement(this.txTrieRoot);

        if (receiptTrieRoot == null) {
            this.receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        byte[] receiptTrieRoot = RLP.encodeElement(this.receiptTrieRoot);

        byte[] logsBloom = RLP.encodeElement(this.logsBloom);
        byte[] difficulty = encodeBlockDifficulty(this.difficulty);
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] gasLimit = RLP.encodeElement(this.gasLimit);
        byte[] gasUsed = RLP.encodeBigInteger(BigInteger.valueOf(this.gasUsed));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        byte[] extraData = RLP.encodeElement(this.extraData);
        byte[] paidFees = RLP.encodeCoin(this.paidFees);
        byte[] mgp = RLP.encodeSignedCoinNonNullZero(this.minimumGasPrice);
        List<byte[]> fieldToEncodeList = Lists.newArrayList(parentHash, unclesHash, coinbase,
                stateRoot, txTrieRoot, receiptTrieRoot, logsBloom, difficulty, number,
                gasLimit, gasUsed, timestamp, extraData, paidFees, mgp);

        byte[] uncleCount = RLP.encodeBigInteger(BigInteger.valueOf(this.uncleCount));
        fieldToEncodeList.add(uncleCount);

        if (this.ummRoot != null) {
            fieldToEncodeList.add(RLP.encodeElement(this.ummRoot));
        }

        if (withMergedMiningFields && hasMiningFields()) {
            byte[] bitcoinMergedMiningHeader = RLP.encodeElement(this.bitcoinMergedMiningHeader);
            fieldToEncodeList.add(bitcoinMergedMiningHeader);
            if (withMerkleProofAndCoinbase) {
                byte[] bitcoinMergedMiningMerkleProof = RLP.encodeElement(this.bitcoinMergedMiningMerkleProof);
                fieldToEncodeList.add(bitcoinMergedMiningMerkleProof);
                byte[] bitcoinMergedMiningCoinbaseTransaction = RLP.encodeElement(this.bitcoinMergedMiningCoinbaseTransaction);
                fieldToEncodeList.add(bitcoinMergedMiningCoinbaseTransaction);
            }
        }

        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }

    /**
     * This is here to override specific non-minimal instances such as the mainnet Genesis
     */
    protected byte[] encodeBlockDifficulty(BlockDifficulty difficulty) {
        return RLP.encodeBlockDifficulty(difficulty);
    }

    // Warning: This method does not use the object's attributes
    public static byte[] getUnclesEncodedEx(List<BlockHeader> uncleList) {
        byte[][] unclesEncoded = new byte[uncleList.size()][];
        int i = 0;
        for (BlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getFullEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
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

    public static byte[] getUnclesEncoded(List<BlockHeader> uncleList) {
        byte[][] unclesEncoded = new byte[uncleList.size()][];
        int i = 0;
        for (BlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getFullEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
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

    public void setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter bitcoin merged mining header");
        }

        this.bitcoinMergedMiningHeader = bitcoinMergedMiningHeader;
    }

    public byte[] getBitcoinMergedMiningMerkleProof() {
        return bitcoinMergedMiningMerkleProof;
    }

    public void setBitcoinMergedMiningMerkleProof(byte[] bitcoinMergedMiningMerkleProof) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter bitcoin merged mining merkle proof");
        }

        this.bitcoinMergedMiningMerkleProof = bitcoinMergedMiningMerkleProof;
    }

    public byte[] getBitcoinMergedMiningCoinbaseTransaction() {
        return bitcoinMergedMiningCoinbaseTransaction;
    }

    public void setBitcoinMergedMiningCoinbaseTransaction(byte[] bitcoinMergedMiningCoinbaseTransaction) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter bitcoin merged mining coinbase transaction");
        }

        this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;
    }

    public String getShortHashForMergedMining() {
        return HashUtil.shortHash(getHashForMergedMining());
    }

    public boolean isUMMBlock() {
        return this.ummRoot != null && this.ummRoot.length != 0;
    }

    public byte[] getHashForMergedMining() {
        byte[] encodedBlock = getEncoded(false, false);
        byte[] hashForMergedMining = HashUtil.keccak256(encodedBlock);

        if (isUMMBlock()) {
            byte[] leftHash = Arrays.copyOf(hashForMergedMining, UMM_LEAVES_LENGTH);
            hashForMergedMining = this.getHashRootForMergedMining(leftHash);
        }

        if (includeForkDetectionData) {
            byte[] mergedMiningForkDetectionData = hasMiningFields() ?
                    getMiningForkDetectionData() :
                    miningForkDetectionData;
            arraycopy(
                    mergedMiningForkDetectionData,
                    0,
                    hashForMergedMining,
                    HASH_FOR_MERGED_MINING_PREFIX_LENGTH,
                    FORK_DETECTION_DATA_LENGTH
            );
        }

        return hashForMergedMining;
    }

    private byte[] getHashRootForMergedMining(byte[] leftHash) {
        if (ummRoot.length != UMM_LEAVES_LENGTH){
            throw new IllegalStateException(
                    String.format("UMM Root length must be either 0 or 20. Found: %d", ummRoot.length)
            );
        }

        byte[] leftRight = Arrays.copyOf(leftHash, leftHash.length + ummRoot.length);
        arraycopy(ummRoot, 0, leftRight, leftHash.length, ummRoot.length);

        byte[] root256 = HashUtil.keccak256(leftRight);
        return root256;
    }

    public String getShortHash() {
        return HashUtil.shortHash(getHash().getBytes());
    }

    public String getParentShortHash() {
        return HashUtil.shortHash(getParentHash().getBytes());
    }

    public byte[] getMiningForkDetectionData() {
        if(includeForkDetectionData) {
            if (hasMiningFields() && miningForkDetectionData.length == 0) {
                byte[] encodedBlock = getEncoded(false, false);
                byte[] hashForMergedMining = HashUtil.keccak256(encodedBlock);

                byte[] hashForMergedMiningPrefix = Arrays.copyOfRange(
                        hashForMergedMining,
                        0,
                        HASH_FOR_MERGED_MINING_PREFIX_LENGTH
                );
                byte[] coinbaseTransaction = getBitcoinMergedMiningCoinbaseTransaction();

                List<Byte> hashForMergedMiningPrefixAsList = Arrays.asList(ArrayUtils.toObject(hashForMergedMiningPrefix));
                List<Byte> coinbaseAsList = Arrays.asList(ArrayUtils.toObject(coinbaseTransaction));

                int position = Collections.lastIndexOfSubList(coinbaseAsList, hashForMergedMiningPrefixAsList);
                if (position == -1) {
                    throw new IllegalStateException(
                            String.format("Mining fork detection data could not be found. Header: %s", getShortHash())
                    );
                }

                int from = position + HASH_FOR_MERGED_MINING_PREFIX_LENGTH;
                int to = from + FORK_DETECTION_DATA_LENGTH;
                miningForkDetectionData = Arrays.copyOfRange(coinbaseTransaction, from, to);
            }

            return Arrays.copyOf(miningForkDetectionData, miningForkDetectionData.length);
        }

        return new byte[0];
    }

    public boolean isParentOf(BlockHeader header) {
        return this.getHash().equals(header.getParentHash());
    }

    public byte[] getUmmRoot() {
        return ummRoot != null ? Arrays.copyOf(ummRoot, ummRoot.length) : null;
    }
}
