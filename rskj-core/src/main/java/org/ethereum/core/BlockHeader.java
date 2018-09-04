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
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.SystemProperties;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.util.Utils;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Block header is a value object containing
 * the basic information of a block
 */
public class BlockHeader {


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

    /* The 81-byte bitcoin block header for merged mining */
    private byte[] bitcoinMergedMiningHeader;
    /* The bitcoin merkle proof of coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningMerkleProof;
    /* The bitcoin protobuf serialized coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningCoinbaseTransaction;
    /**
     * The mgp for a tx to be included in the block.
     */
    private Coin minimumGasPrice;
    private int uncleCount;

    /* Indicates if this block header cannot be changed */
    private volatile boolean sealed;

    public BlockHeader(byte[] encoded, boolean sealed) {
        this(RLP.decodeList(encoded), sealed);
    }

    public BlockHeader(RLPList rlpHeader, boolean sealed) {
        // TODO fix old tests that have other sizes
        if (rlpHeader.size() != 19 && rlpHeader.size() != 16) {
            throw new IllegalArgumentException(String.format(
                    "A block header must have 16 elements or 19 including merged-mining fields but it had %d",
                    rlpHeader.size()
            ));
        }

        this.parentHash = rlpHeader.get(0).getRLPData();
        this.unclesHash = rlpHeader.get(1).getRLPData();
        this.coinbase = RLP.parseRskAddress(rlpHeader.get(2).getRLPData());
        this.stateRoot = rlpHeader.get(3).getRLPData();
        if (this.stateRoot == null) {
            this.stateRoot = EMPTY_TRIE_HASH;
        }

        this.txTrieRoot = rlpHeader.get(4).getRLPData();
        if (this.txTrieRoot == null) {
            this.txTrieRoot = EMPTY_TRIE_HASH;
        }

        this.receiptTrieRoot = rlpHeader.get(5).getRLPData();
        if (this.receiptTrieRoot == null) {
            this.receiptTrieRoot = EMPTY_TRIE_HASH;
        }

        this.logsBloom = rlpHeader.get(6).getRLPData();
        this.difficulty = RLP.parseBlockDifficulty(rlpHeader.get(7).getRLPData());

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        byte[] glBytes = rlpHeader.get(9).getRLPData();
        byte[] guBytes = rlpHeader.get(10).getRLPData();
        byte[] tsBytes = rlpHeader.get(11).getRLPData();

        this.number = parseBigInteger(nrBytes).longValueExact();

        this.gasLimit = glBytes;
        this.gasUsed = parseBigInteger(guBytes).longValueExact();
        this.timestamp = parseBigInteger(tsBytes).longValueExact();

        this.extraData = rlpHeader.get(12).getRLPData();

        this.paidFees = RLP.parseCoin(rlpHeader.get(13).getRLPData());
        this.minimumGasPrice = RLP.parseSignedCoinNonNullZero(rlpHeader.get(14).getRLPData());

        int r = 15;

        if ((rlpHeader.size() == 19) || (rlpHeader.size() == 16)) {
            byte[] ucBytes = rlpHeader.get(r++).getRLPData();
            this.uncleCount = parseBigInteger(ucBytes).intValueExact();
        }

        if (rlpHeader.size() > r) {
            this.bitcoinMergedMiningHeader = rlpHeader.get(r++).getRLPData();
            this.bitcoinMergedMiningMerkleProof = rlpHeader.get(r++).getRLPRawData();
            this.bitcoinMergedMiningCoinbaseTransaction = rlpHeader.get(r++).getRLPData();

        }

        this.sealed = sealed;
    }

    public BlockHeader(byte[] parentHash, byte[] unclesHash, byte[] coinbase,
                       byte[] logsBloom, byte[] difficulty, long number,
                       byte[] gasLimit, long gasUsed, long timestamp,
                       byte[] extraData,
                       byte[] minimumGasPrice,
                       int uncleCount) {
        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit, gasUsed, timestamp, extraData,
                null, null, null, minimumGasPrice, uncleCount);
    }

    public BlockHeader(byte[] parentHash, byte[] unclesHash, byte[] coinbase,
                       byte[] logsBloom, byte[] difficulty, long number,
                       byte[] gasLimit, long gasUsed, long timestamp,
                       byte[] extraData,
                       byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                       byte[] bitcoinMergedMiningCoinbaseTransaction,
                       byte[] minimumGasPrice,
                       int uncleCount) {
        this.parentHash = parentHash;
        this.unclesHash = unclesHash;
        this.coinbase = new RskAddress(coinbase);
        this.logsBloom = logsBloom;
        this.difficulty = RLP.parseBlockDifficulty(difficulty);
        this.number = number;
        this.gasLimit = gasLimit;
        this.gasUsed = gasUsed;
        this.timestamp = timestamp;
        this.extraData = extraData;
        this.stateRoot = ByteUtils.clone(EMPTY_TRIE_HASH);
        this.minimumGasPrice = RLP.parseSignedCoinNonNullZero(minimumGasPrice);
        this.receiptTrieRoot = ByteUtils.clone(EMPTY_TRIE_HASH);
        this.uncleCount = uncleCount;
        this.paidFees = Coin.ZERO;
        this.bitcoinMergedMiningHeader = bitcoinMergedMiningHeader;
        this.bitcoinMergedMiningMerkleProof = bitcoinMergedMiningMerkleProof;
        this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;
    }

    @VisibleForTesting
    public boolean isSealed() {
        return this.sealed;
    }

    public void seal() {
        this.sealed = true;
    }

    public BlockHeader cloneHeader() {
        return new BlockHeader((RLPList) RLP.decode2(this.getEncoded()).get(0), false);
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

    public void setUnclesHash(byte[] unclesHash) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter uncles hash");
        }

        this.unclesHash = unclesHash;
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

    public void setTimestamp(long timestamp) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter timestamp");
        }

        this.timestamp = timestamp;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter number");
        }

        this.number = number;
    }

    public byte[] getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(byte[] gasLimit) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter gas limit");
        }

        this.gasLimit = gasLimit;
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

    public void setExtraData(byte[] extraData) {
        /* A sealed block header is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockHeaderException("trying to alter extra data");
        }

        this.extraData = extraData;
    }

    public Keccak256 getHash() {
        return new Keccak256(HashUtil.keccak256(getEncoded(
                true,
                !SystemProperties.DONOTUSE_blockchainConfig.getConfigForBlock(getNumber()).isRskip92()
        )));
    }

    public byte[] getEncoded() {
        // the encoded block header must include all fields, even the bitcoin PMT and coinbase which are not used for
        // calculating RSKIP92 block hashes
        return this.getEncoded(true, true);
    }

    public byte[] getEncodedWithoutNonceMergedMiningFields() {
        return this.getEncoded(false, false);
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
            unclesEncoded[i] = uncle.getEncoded();
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
            unclesEncoded[i] = uncle.getEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
    }

    public byte[] getPowBoundary() {
        return BigIntegers.asUnsignedByteArray(32, BigInteger.ONE.shiftLeft(256).divide(getDifficulty().asBigInteger()));
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

    // TODO added to comply with SerializableObject

    public Keccak256 getRawHash() {
        return getHash();
    }
    // TODO added to comply with SerializableObject

    public byte[] getEncodedRaw() {
        return getEncoded();
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

    public byte[] getHashForMergedMining() {
        return HashUtil.keccak256(getEncoded(false, false));
    }

    public String getShortHash() {
        return HashUtil.shortHash(getHash().getBytes());
    }

    public String getParentShortHash() {
        return HashUtil.shortHash(getParentHash().getBytes());
    }

    private static BigInteger parseBigInteger(byte[] bytes) {
        return bytes == null ? BigInteger.ZERO : BigIntegers.fromUnsignedByteArray(bytes);
    }
}
