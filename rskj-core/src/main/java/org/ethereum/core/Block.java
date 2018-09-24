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
import co.rsk.panic.PanicProcessor;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The block in Ethereum is the collection of relevant pieces of information
 * (known as the blockheader), H, together with information corresponding to
 * the comprised transactions, R, and a set of other blockheaders U that are known
 * to have a parent equalBytes to the present block’s parent’s parent
 * (such blocks are known as uncles).
 *
 * @author Roman Mandeleil
 * @author Nick Savers
 * @since 20.05.2014
 */
public class Block {

    private static final Logger logger = LoggerFactory.getLogger("block");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private BlockHeader header;

    // The methods below make sure we use immutable lists
    /* Transactions */
    private List<Transaction> transactionsList;

    /* Uncles */
    private List<BlockHeader> uncleList = new CopyOnWriteArrayList<>();

    /* Private */
    private byte[] rlpEncoded;
    private boolean parsed = false;

    /* Indicates if this block can or cannot be changed */
    private volatile boolean sealed;

    public Block(byte[] rawData) {
        this(rawData, true);
    }

    protected Block(byte[] rawData, boolean sealed) {
        this.rlpEncoded = rawData;
        this.sealed = sealed;
        parseRLP();
        // clear it so we always reencode the received data
        this.rlpEncoded = null;
    }

    public Block(BlockHeader header) {
        this.header = header;
        this.parsed = true;
    }

    public Block(BlockHeader header, List<Transaction> transactionsList, List<BlockHeader> uncleList) {

        this(
                header.getParentHash().getBytes(),
                header.getUnclesHash(),
                header.getCoinbase().getBytes(),
                header.getLogsBloom(),
                header.getDifficulty().getBytes(),
                header.getNumber(),
                header.getGasLimit(),
                header.getGasUsed(),
                header.getTimestamp(),
                header.getExtraData(),
                null,
                null,
                header.getBitcoinMergedMiningHeader(),
                header.getBitcoinMergedMiningMerkleProof(),
                header.getBitcoinMergedMiningCoinbaseTransaction(),
                header.getReceiptsRoot(),
                header.getTxTrieRoot(),
                header.getStateRoot(),
                transactionsList,
                uncleList,
                header.getMinimumGasPrice() == null ? null : header.getMinimumGasPrice().getBytes());
    }

    public Block(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] mixHash,
                 byte[] nonce, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                 byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] receiptsRoot,
                 byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, List<BlockHeader> uncleList, byte[] minimumGasPrice) {

        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit,
                gasUsed, timestamp, extraData, mixHash, nonce, receiptsRoot, transactionsRoot,
                stateRoot, transactionsList, uncleList, minimumGasPrice, Coin.ZERO);

        this.header.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
        this.header.setBitcoinMergedMiningHeader(bitcoinMergedMiningHeader);
        this.header.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleProof);

        this.flushRLP();
    }

    public Block(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] mixHash, byte[] nonce, byte[] receiptsRoot,
                 byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, List<BlockHeader> uncleList, byte[] minimumGasPrice, Coin paidFees) {

        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit,
                gasUsed, timestamp, extraData, mixHash, nonce, transactionsList, uncleList, minimumGasPrice);

        this.header.setPaidFees(paidFees);

        byte[] calculatedRoot = getTxTrieRoot(transactionsList, isHardFork9999(number));
        this.header.setTransactionsRoot(calculatedRoot);
        this.checkExpectedRoot(transactionsRoot, calculatedRoot);

        this.header.setStateRoot(stateRoot);
        this.header.setReceiptsRoot(receiptsRoot);

        this.flushRLP();
    }

    public Block(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, long number, byte[] gasLimit,
                 long gasUsed, long timestamp,
                 byte[] extraData, byte[] mixHash, byte[] nonce,
                 List<Transaction> transactionsList, List<BlockHeader> uncleList, byte[] minimumGasPrice) {

        if (transactionsList == null) {
            this.transactionsList = Collections.emptyList();
        }
        else {
            this.transactionsList = Collections.unmodifiableList(transactionsList);
        }

        this.uncleList = uncleList;
        if (this.uncleList == null) {
            this.uncleList = new CopyOnWriteArrayList<>();
        }

        this.header = new BlockHeader(parentHash, unclesHash, coinbase, logsBloom,
                difficulty, number, gasLimit, gasUsed,
                timestamp, extraData, minimumGasPrice, this.uncleList.size());

        this.parsed = true;
    }

    public static Block fromValidData(BlockHeader header, List<Transaction> transactionsList, List<BlockHeader> uncleList) {
        Block block = new Block(header);
        block.transactionsList = transactionsList;
        block.uncleList = uncleList;
        block.seal();
        return block;
    }

    public void seal() {
        this.sealed = true;
        this.header.seal();
    }

    public boolean isSealed() {
        return this.sealed;
    }

    // Clone this block allowing modifications
    public Block cloneBlock() {
        return new Block(this.getEncoded(), false);
    }

    private void parseRLP() {
        RLPList block = RLP.decodeList(rlpEncoded);
        if (block.size() != 3) {
            throw new IllegalArgumentException("A block must have 3 exactly items");
        }

        // Parse Header
        RLPList header = (RLPList) block.get(0);
        this.header = new BlockHeader(header, this.sealed);

        // Parse Transactions
        RLPList txTransactions = (RLPList) block.get(1);
        this.transactionsList = parseTxs(txTransactions);
        byte[] calculatedRoot = getTxTrieRoot(this.transactionsList,isHardFork9999(this.header.getNumber()));
        this.checkExpectedRoot(this.header.getTxTrieRoot(), calculatedRoot);

        // Parse Uncles
        RLPList uncleBlocks = (RLPList) block.get(2);
        for (RLPElement rawUncle : uncleBlocks) {

            RLPList uncleHeader = (RLPList) rawUncle;
            BlockHeader blockData = new BlockHeader(uncleHeader, this.sealed);
            this.uncleList.add(blockData);
        }
        this.parsed = true;
    }

    // TODO(mc) remove this method and create a new ExecutedBlock class or similar
    public void setTransactionsList(@Nonnull List<Transaction> transactionsList) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter transaction list");
        }

        this.transactionsList = Collections.unmodifiableList(transactionsList);
        rlpEncoded = null;
    }

    public BlockHeader getHeader() {
        if (!parsed) {
            parseRLP();
        }
        return this.header;
    }

    public Keccak256 getHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getHash();
    }

    public Keccak256 getParentHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getParentHash();
    }

    public byte[] getUnclesHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getUnclesHash();
    }

    public RskAddress getCoinbase() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getCoinbase();
    }

    public byte[] getStateRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getStateRoot();
    }

    public void setStateRoot(byte[] stateRoot) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter state root");
        }

        if (!parsed) {
            parseRLP();
        }
        this.header.setStateRoot(stateRoot);
    }

    public byte[] getTxTrieRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getTxTrieRoot();
    }

    public byte[] getReceiptsRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getReceiptsRoot();
    }


    public byte[] getLogBloom() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getLogsBloom();
    }

    public BlockDifficulty getDifficulty() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getDifficulty();
    }

    public Coin getFeesPaidToMiner() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getPaidFees();
    }

    public BlockDifficulty getCumulativeDifficulty() {
        if (!parsed) {
            parseRLP();
        }
        BlockDifficulty calcDifficulty = this.header.getDifficulty();
        for (BlockHeader uncle : uncleList) {
            calcDifficulty = calcDifficulty.add(uncle.getDifficulty());
        }
        return calcDifficulty;
    }

    public long getTimestamp() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getTimestamp();
    }

    public long getNumber() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getNumber();
    }

    public byte[] getGasLimit() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getGasLimit();
    }

    public long getGasUsed() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getGasUsed();
    }

    public byte[] getExtraData() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getExtraData();
    }

  public void setExtraData(byte[] data) {
      /* A sealed block is immutable, cannot be changed */
      if (this.sealed) {
          throw new SealedBlockException("trying to alter extra data");
      }

        this.header.setExtraData(data);
        rlpEncoded = null;
    }

    public List<Transaction> getTransactionsList() {
        if (!parsed) {
            parseRLP();
        }

        return Collections.unmodifiableList(this.transactionsList);
    }

    public List<BlockHeader> getUncleList() {
        if (!parsed) {
            parseRLP();
        }

        return Collections.unmodifiableList(this.uncleList);
    }

    public Coin getMinimumGasPrice() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getMinimumGasPrice();
    }

    private StringBuffer toStringBuff = new StringBuffer();
    // [parent_hash, uncles_hash, coinbase, state_root, tx_trie_root,
    // difficulty, number, minGasPrice, gasLimit, gasUsed, timestamp,
    // extradata, nonce]

    @Override
    public String toString() {
        if (!parsed) {
            parseRLP();
        }

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(this.getHash()).append("\n");
        toStringBuff.append(header.toString());

        if (!getUncleList().isEmpty()) {
            toStringBuff.append("Uncles [\n");
            for (BlockHeader uncle : getUncleList()) {
                toStringBuff.append(uncle.toString());
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Uncles []\n");
        }
        if (!getTransactionsList().isEmpty()) {
            toStringBuff.append("Txs [\n");
            for (Transaction tx : getTransactionsList()) {
                toStringBuff.append(tx);
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Txs []\n");
        }
        toStringBuff.append("]");

        return toStringBuff.toString();
    }

    public String toFlatString() {
        if (!parsed) {
            parseRLP();
        }

        toStringBuff.setLength(0);
        toStringBuff.append("BlockData [");
        toStringBuff.append("hash=").append(this.getHash());
        toStringBuff.append(header.toFlatString());

        for (Transaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }

        toStringBuff.append("]");
        return toStringBuff.toString();
    }

    private List<Transaction> parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (isRemascTransaction(tx, i, txTransactions.size())) {
                // It is the remasc transaction
                tx = new RemascTransaction(transactionRaw.getRLPData());
            }
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
    }

    public static boolean isRemascTransaction(Transaction tx, int txPosition, int txsSize) {

        return isLastTx(txPosition, txsSize) && checkRemascAddress(tx) && checkRemascTxZeroValues(tx);
    }

    private static boolean isLastTx(int txPosition, int txsSize) {
        return txPosition == (txsSize - 1);
    }

    private static boolean checkRemascAddress(Transaction tx) {
        return PrecompiledContracts.REMASC_ADDR.equals(tx.getReceiveAddress());
    }

    private static boolean checkRemascTxZeroValues(Transaction tx) {
        if(null != tx.getData() || null != tx.getSignature()){
            return false;
        }

        return Coin.ZERO.equals(tx.getValue()) &&
                BigInteger.ZERO.equals(new BigInteger(1, tx.getGasLimit())) &&
                Coin.ZERO.equals(tx.getGasPrice());

    }

    private void checkExpectedRoot(byte[] expectedRoot, byte[] calculatedRoot) {
        if (!Arrays.areEqual(expectedRoot, calculatedRoot)) {
            logger.error("Transactions trie root validation failed for block #{}", this.header.getNumber());
            panicProcessor.panic("txroot", String.format("Transactions trie root validation failed for block %d %s", this.header.getNumber(), this.header.getHash()));
        }
    }

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(Block block) {
        return this.getHash().equals(block.getParentHash());
    }

    public boolean isGenesis() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.isGenesis();
    }

    public boolean isEqual(Block block) {
        return this.getHash().equals(block.getHash());
    }

    public boolean fastEquals(Block block) {
        return block != null && this.getHash().equals(block.getHash());
    }

    private byte[] getTransactionsEncoded() {
        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (Transaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    private byte[] getUnclesEncoded() {

        byte[][] unclesEncoded = new byte[uncleList.size()][];
        int i = 0;
        for (BlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
    }

    public void addUncle(BlockHeader uncle) {
        if (this.sealed) {
            throw new SealedBlockException("trying to add uncle");
        }

        uncleList.add(uncle);
        this.getHeader().setUnclesHash(Keccak256Helper.keccak256(getUnclesEncoded()));
        rlpEncoded = null;
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] header = this.header.getEncoded();

            List<byte[]> block = getBodyElements();
            block.add(0, header);
            byte[][] elements = block.toArray(new byte[block.size()][]);

            this.rlpEncoded = RLP.encodeList(elements);
        }
        return rlpEncoded;
    }

    public byte[] getEncodedWithoutNonce() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getEncodedWithoutNonceMergedMiningFields();
    }

    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    private List<byte[]> getBodyElements() {
        if (!parsed) {
            parseRLP();
        }

        byte[] transactions = getTransactionsEncoded();
        byte[] uncles = getUnclesEncoded();

        List<byte[]> body = new ArrayList<>();
        body.add(transactions);
        body.add(uncles);

        return body;
    }

    public String getShortHash() {
        if (!parsed) {
            parseRLP();
        }

        return header.getShortHash();
    }

    public String getParentShortHash() {
        if (!parsed) {
            parseRLP();
        }

        return header.getParentShortHash();
    }

    public String getShortHashForMergedMining() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getShortHashForMergedMining();
    }

    public byte[] getHashForMergedMining() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getHashForMergedMining();
    }

    public String getShortDescr() {
        return "#" + getNumber() + " (" + getShortHash() + " <~ "
                + getParentShortHash() + ") Txs:" + getTransactionsList().size() +
                ", Unc: " + getUncleList().size();
    }

    public String getHashJsonString() {
        return TypeConverter.toJsonHex(getHash().getBytes());
    }

    public String getParentHashJsonString() {
        return getParentHash().toJsonString();
    }

    public byte[] getBitcoinMergedMiningHeader() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getBitcoinMergedMiningHeader();
    }

    public void setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining header");
        }

        if (!parsed) {
            parseRLP();
        }

        this.header.setBitcoinMergedMiningHeader(bitcoinMergedMiningHeader);
        rlpEncoded = null;
    }

    public byte[] getBitcoinMergedMiningMerkleProof() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getBitcoinMergedMiningMerkleProof();
    }

    public void setBitcoinMergedMiningMerkleProof(byte[] bitcoinMergedMiningMerkleProof) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining Merkle proof");
        }

        this.header.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleProof);
        rlpEncoded = null;
    }

    public byte[] getBitcoinMergedMiningCoinbaseTransaction() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getBitcoinMergedMiningCoinbaseTransaction();
    }

    public void setBitcoinMergedMiningCoinbaseTransaction(byte[] bitcoinMergedMiningCoinbaseTransaction) {
        if (this.sealed) {
            throw new SealedBlockException("trying to alter bitcoin merged mining coinbase transaction");
        }

        this.header.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
        rlpEncoded = null;
    }

    public static boolean isHardFork9999(long number) {
        return number >= 9999;
    }

    public static byte[] getTxTrieRoot(List<Transaction> transactions, boolean hardfork9999) {
        Trie trie;
        if (hardfork9999) {
            trie = getTxTrieNew(transactions);
        } else {
            trie = getTxTrieOld(transactions);
        }

        return trie.getHash().getBytes();
    }

    private static Trie getTxTrieOld(List<Transaction> transactions) {
        return getTxTrieFor(transactions, new TrieImpl());
    }

    private static Trie getTxTrieNew(List<Transaction> transactions) {
        return getTxTrieFor(transactions, new TrieImpl());
    }

    private static Trie getTxTrieFor(List<Transaction> transactions, Trie txsState) {
        if (transactions == null) {
            return txsState;
        }

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public void flushRLP() {
        this.rlpEncoded = null;
        this.parsed = true;
    }
}
