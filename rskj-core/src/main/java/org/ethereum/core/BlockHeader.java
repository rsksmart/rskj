package org.ethereum.core;

import co.rsk.config.RskMiningConstants;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.util.ListArrayUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.Utils;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.arraycopy;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.toHexStringOrEmpty;

/**
 * Block header is a value object containing
 * the basic information of a block
 */
public abstract class BlockHeader {
    /* RSKIP 351 */
    public abstract byte getVersion();
    public abstract BlockHeaderExtension getExtension();
    public abstract void setExtension(BlockHeaderExtension extension);
    // fields from block header extension
    public abstract byte[] getLogsBloom();
    public abstract void setLogsBloom(byte[] logsBloom);
    // encoding to use in logs bloom field on header response message
    public abstract byte[] getLogsBloomFieldEncoded();

    private static final int HASH_FOR_MERGED_MINING_PREFIX_LENGTH = 20;
    private static final int FORK_DETECTION_DATA_LENGTH = 12;
    private static final int UMM_LEAVES_LENGTH = 20;

    /* The SHA3 256-bit hash of the parent block, in its entirety */
    private final byte[] parentHash;
    /* The SHA3 256-bit hash of the uncles list portion of this block */
    private final byte[] unclesHash;
    /* The 160-bit address to which all fees collected from the
     * successful mining of this block be transferred; formally */
    private final RskAddress coinbase;
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
    /**
     * A scalar value corresponding to the difficulty level of this block.
     * This can be calculated from the previous block’s difficulty level
     * and the timestamp.
     */
    private BlockDifficulty difficulty;
    /* A scalar value equalBytes to the reasonable output of Unix's time()
     * at this block's inception */
    private final long timestamp;
    /* A scalar value equalBytes to the number of ancestor blocks.
     * The genesis block has a number of zero */
    private final long number;
    /* A scalar value equalBytes to the current limit of gas expenditure per block */
    private final byte[] gasLimit;
    /* A scalar value equalBytes to the total gas used in transactions in this block */
    private long gasUsed;
    /* A scalar value equalBytes to the total paid fees in transactions in this block */
    private Coin paidFees;

    /* An arbitrary byte array containing data relevant to this block.
     * With the exception of the genesis block, this must be 32 bytes or fewer */
    private final byte[] extraData;

    /* The 80-byte bitcoin block header for merged mining */
    private byte[] bitcoinMergedMiningHeader;
    /* The bitcoin merkle proof of coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningMerkleProof;
    /* The bitcoin protobuf serialized coinbase tx for merged mining */
    private byte[] bitcoinMergedMiningCoinbaseTransaction;

    private byte[] miningForkDetectionData;

    /* Edges of the transaction execution lists */
    private short[] txExecutionSublistsEdges;

    private final byte[] ummRoot;

    /**
     * The mgp for a tx to be included in the block.
     */
    private final Coin minimumGasPrice;
    private final int uncleCount;

    /* Indicates if this block header cannot be changed */
    protected volatile boolean sealed;

    /* Holds calculated block hash */
    protected Keccak256 hash;

    /* Indicates if the block was mined according to RSKIP-92 rules */
    private final boolean useRskip92Encoding;

    /* Indicates if Block hash for merged mining should have the format described in RSKIP-110 */
    private final boolean includeForkDetectionData;

    public BlockHeader(byte[] parentHash, byte[] unclesHash, RskAddress coinbase, byte[] stateRoot,
                       byte[] txTrieRoot, byte[] receiptTrieRoot, BlockDifficulty difficulty,
                       long number, byte[] gasLimit, long gasUsed, long timestamp, byte[] extraData,
                       Coin paidFees, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                       byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] mergedMiningForkDetectionData,
                       Coin minimumGasPrice, int uncleCount, boolean sealed,
                       boolean useRskip92Encoding, boolean includeForkDetectionData, byte[] ummRoot,
                       short[] txExecutionSublistsEdges) {
        this.parentHash = parentHash;
        this.unclesHash = unclesHash;
        this.coinbase = coinbase;
        this.stateRoot = stateRoot;
        this.txTrieRoot = txTrieRoot;
        this.receiptTrieRoot = receiptTrieRoot;
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
        this.txExecutionSublistsEdges = txExecutionSublistsEdges != null ? Arrays.copyOf(txExecutionSublistsEdges, txExecutionSublistsEdges.length) : null;
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
        this.hash = null;

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
        this.hash = null;

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
        this.hash = null;

        this.txTrieRoot = stateRoot;
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
        this.hash = null;

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
        this.hash = null;

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
        this.hash = null;

        this.gasUsed = gasUsed;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public Keccak256 getHash() {
        if (this.hash == null) {
            this.hash = new Keccak256(HashUtil.keccak256(getEncodedForHash()));
        }

        return this.hash;
    }

    public byte[] getFullEncoded() {
        // the encoded block header must include all fields, even the bitcoin PMT and coinbase which are not used for
        // calculating RSKIP92 block hashes
        return this.getEncoded(true, true, false);
    }

    public byte[] getEncoded() {
        // the encoded block header used for calculating block hashes including RSKIP92
        return this.getEncoded(true, !useRskip92Encoding, false);
    }

    public byte[] getEncodedForHeaderMessage() {
        return this.getEncoded(true, true, true);
    }

    public byte[] getEncodedForHash() {
        return this.getEncoded(true, !useRskip92Encoding, true);
    }

    @Nullable
    public Coin getMinimumGasPrice() {
        return this.minimumGasPrice;
    }

    public byte[] getEncoded(boolean withMergedMiningFields, boolean withMerkleProofAndCoinbase, boolean useExtensionEncoding) {
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

        byte[] logsBloom = useExtensionEncoding ? this.getLogsBloomFieldEncoded() : RLP.encodeElement(this.getLogsBloom());
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

        if (this.txExecutionSublistsEdges != null) {
            byte[] edgesBytes = new byte[this.txExecutionSublistsEdges.length * 2];
            ByteBuffer.wrap(edgesBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(this.txExecutionSublistsEdges);
            fieldToEncodeList.add(RLP.encodeElement(edgesBytes));
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
        toStringBuff.append("  parentHash=").append(toHexStringOrEmpty(parentHash)).append(suffix);
        toStringBuff.append("  unclesHash=").append(toHexStringOrEmpty(unclesHash)).append(suffix);
        toStringBuff.append("  coinbase=").append(coinbase).append(suffix);
        toStringBuff.append("  stateRoot=").append(toHexStringOrEmpty(stateRoot)).append(suffix);
        toStringBuff.append("  txTrieHash=").append(toHexStringOrEmpty(txTrieRoot)).append(suffix);
        toStringBuff.append("  receiptsTrieHash=").append(toHexStringOrEmpty(receiptTrieRoot)).append(suffix);
        toStringBuff.append("  difficulty=").append(difficulty).append(suffix);
        toStringBuff.append("  number=").append(number).append(suffix);
        toStringBuff.append("  gasLimit=").append(toHexStringOrEmpty(gasLimit)).append(suffix);
        toStringBuff.append("  gasUsed=").append(gasUsed).append(suffix);
        toStringBuff.append("  timestamp=").append(timestamp).append(" (").append(Utils.longToDateTime(timestamp)).append(")").append(suffix);
        toStringBuff.append("  extraData=").append(toHexStringOrEmpty(extraData)).append(suffix);
        toStringBuff.append("  minGasPrice=").append(minimumGasPrice).append(suffix);
        toStringBuff.append("  txExecutionSublistsEdges=").append(Arrays.toString(txExecutionSublistsEdges)).append(suffix);

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
        this.hash = null;

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
        this.hash = null;

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
        this.hash = null;

        this.bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningCoinbaseTransaction;
    }

    public String getPrintableHashForMergedMining() {
        return HashUtil.toPrintableHash(getHashForMergedMining());
    }

    public boolean isUMMBlock() {
        return this.ummRoot != null && this.ummRoot.length != 0;
    }

    public byte[] getHashForMergedMining() {
        byte[] hashForMergedMining = this.getBaseHashForMergedMining();

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

    public String getPrintableHash() {
        return HashUtil.toPrintableHash(getHash().getBytes());
    }

    public String getParentPrintableHash() {
        return HashUtil.toPrintableHash(getParentHash().getBytes());
    }

    public byte[] getMiningForkDetectionData() {
        if(includeForkDetectionData) {
            if (hasMiningFields() && miningForkDetectionData.length == 0) {
                byte[] hashForMergedMining = getBaseHashForMergedMining();

                byte[] coinbaseTransaction = getBitcoinMergedMiningCoinbaseTransaction();

                byte[] mergeMiningTagPrefix = Arrays.copyOf(RskMiningConstants.RSK_TAG, RskMiningConstants.RSK_TAG.length + HASH_FOR_MERGED_MINING_PREFIX_LENGTH);
                arraycopy(hashForMergedMining, 0, mergeMiningTagPrefix, RskMiningConstants.RSK_TAG.length, HASH_FOR_MERGED_MINING_PREFIX_LENGTH);

                int position = ListArrayUtil.lastIndexOfSubList(coinbaseTransaction, mergeMiningTagPrefix);
                if (position == -1) {
                    throw new IllegalStateException(
                            String.format("Mining fork detection data could not be found. Header: %s", getPrintableHash())
                    );
                }

                int from = position + RskMiningConstants.RSK_TAG.length + HASH_FOR_MERGED_MINING_PREFIX_LENGTH;
                int to = from + FORK_DETECTION_DATA_LENGTH;

                if (coinbaseTransaction.length < to) {
                    throw new IllegalStateException(
                            String.format(
                                    "Invalid fork detection data length. Expected: %d. Got: %d. Header: %s",
                                    FORK_DETECTION_DATA_LENGTH,
                                    coinbaseTransaction.length - from,
                                    getPrintableHash()
                            )
                    );
                }

                miningForkDetectionData = Arrays.copyOfRange(coinbaseTransaction, from, to);
            }

            return Arrays.copyOf(miningForkDetectionData, miningForkDetectionData.length);
        }

        return new byte[0];
    }

    /**
     * Compute the base hash for merged mining, taking into account whether the block is a umm block.
     * This base hash is later modified to include the forkdetectiondata in its last 12 bytes
     *
     * @return The computed hash for merged mining
     */
    private byte[] getBaseHashForMergedMining() {
        byte[] encodedBlock = getEncoded(false, false, false);
        byte[] hashForMergedMining = HashUtil.keccak256(encodedBlock);

        if (isUMMBlock()) {
            byte[] leftHash = Arrays.copyOfRange(hashForMergedMining, 0, UMM_LEAVES_LENGTH);
            hashForMergedMining = getHashRootForMergedMining(leftHash);
        }

        return hashForMergedMining;
    }

    public boolean isParentOf(BlockHeader header) {
        return this.getHash().equals(header.getParentHash());
    }

    public byte[] getUmmRoot() {
        return ummRoot != null ? Arrays.copyOf(ummRoot, ummRoot.length) : null;
    }

    public short[] getTxExecutionSublistsEdges() { return this.txExecutionSublistsEdges != null ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length) : null; }

    public void setTxExecutionSublistsEdges(short[] edges) {
        this.txExecutionSublistsEdges =  edges != null? Arrays.copyOf(edges, edges.length) : null;
    }
}
