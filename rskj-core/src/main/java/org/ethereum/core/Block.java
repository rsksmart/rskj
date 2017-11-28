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

import co.rsk.panic.PanicProcessor;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

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

    private Trie txsState;

    /* Indicates if this block can or cannot be changed */
    private volatile boolean sealed;

    public Block(byte[] rawData) {
        this.rlpEncoded = rawData;
        this.sealed = true;
    }

    protected Block(byte[] rawData, boolean sealed) {
        this.rlpEncoded = rawData;
        this.sealed = sealed;
    }

    public Block(BlockHeader header) {
        this.header = header;
        this.parsed = true;
    }

    public Block(BlockHeader header, List<Transaction> transactionsList, List<BlockHeader> uncleList) {

        this(header.getParentHash(),
                header.getUnclesHash(),
                header.getCoinbase(),
                header.getLogsBloom(),
                header.getDifficulty(),
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
                header.getEventsRoot(),
                header.getTxTrieRoot(),
                header.getStateRoot(),
                transactionsList,
                uncleList,
                header.getMinimumGasPrice());
    }

    public Block(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] mixHash,
                 byte[] nonce, byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                 byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] receiptsRoot,
                 byte[] eventsRoot,
                 byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, List<BlockHeader> uncleList, byte[] minimumGasPrice) {

        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit,
                gasUsed, timestamp, extraData, mixHash, nonce, receiptsRoot, eventsRoot, transactionsRoot,
                stateRoot, transactionsList, uncleList, minimumGasPrice, 0L);

        this.header.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
        this.header.setBitcoinMergedMiningHeader(bitcoinMergedMiningHeader);
        this.header.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleProof);


        if (!Hex.toHexString(transactionsRoot).
                equals(Hex.toHexString(this.header.getTxTrieRoot()))){
            logger.error("Transaction root miss-calculate, block: {}", getNumber());

            panicProcessor.panic("txroot", String.format("Transaction root miss-calculate, block: %d %s", getNumber(), Hex.toHexString(getHash())));
        }

        this.header.setStateRoot(stateRoot);
        this.header.setReceiptsRoot(receiptsRoot);
        this.header.setEventsRoot(eventsRoot);

        this.flushRLP();
    }

    public Block(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] mixHash, byte[] nonce, byte[] receiptsRoot,
                 byte[] eventsRoot,
                 byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, List<BlockHeader> uncleList, byte[] minimumGasPrice, long paidFees) {

        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit,
                gasUsed, timestamp, extraData, mixHash, nonce, transactionsList, uncleList, minimumGasPrice);

        this.header.setPaidFees(paidFees);
        this.header.setTransactionsRoot(calcTxTrie(transactionsList));

        if (!Hex.toHexString(transactionsRoot).
                equals(Hex.toHexString(this.header.getTxTrieRoot()))) {
            logger.error("Transaction root miss-calculate, block: {}", getNumber());
            panicProcessor.panic("txroot", String.format("Transaction root miss-calculate, block: %s %s", getNumber(), Hex.toHexString(getHash())));
        }

        this.header.setStateRoot(stateRoot);
        this.header.setReceiptsRoot(receiptsRoot);
        this.header.setEventsRoot(eventsRoot);
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

    public void seal() {
        this.sealed = true;
        this.header.seal();
    }

    public boolean isSealed() {
        return this.sealed;
    }

    // Clone this block allowing modifications
    public Block cloneBlock() {
        Block clone = new Block(this.getEncoded(), false);

        return clone;
    }

    private void parseRLP() {
        ArrayList<RLPElement> params = RLP.decode2(rlpEncoded);
        RLPList block = (RLPList) params.get(0);

        // Parse Header
        RLPList header = (RLPList) block.get(0);
        this.header = new BlockHeader(header, this.sealed);

        // Parse Transactions
        // The element cannot be empty/null. It can however be an empty list.
        RLPList txTransactions = (RLPList) block.get(1);
        this.parseTxs(this.header.getTxTrieRoot(), txTransactions);

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
        if (this.sealed)
            throw new SealedBlockException("trying to alter transaction list");

        this.transactionsList = Collections.unmodifiableList(transactionsList);
        rlpEncoded = null;
    }

    public BlockHeader getHeader() {
        if (!parsed)
            parseRLP();
        return this.header;
    }

    public byte[] getHash() {
        if (!parsed)
            parseRLP();
        return this.header.getHash();
    }

    public byte[] getParentHash() {
        if (!parsed)
            parseRLP();
        return this.header.getParentHash();
    }

    public byte[] getUnclesHash() {
        if (!parsed)
            parseRLP();
        return this.header.getUnclesHash();
    }

    public byte[] getCoinbase() {
        if (!parsed)
            parseRLP();
        return this.header.getCoinbase();
    }

    public byte[] getStateRoot() {
        if (!parsed)
            parseRLP();
        return this.header.getStateRoot();
    }

    public byte[] getEventsRoot() {
        if (!parsed)
            parseRLP();
        return this.header.getEventsRoot();
    }

    public void setStateRoot(byte[] stateRoot) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed)
            throw new SealedBlockException("trying to alter state root");

        if (!parsed)
            parseRLP();
        this.header.setStateRoot(stateRoot);
    }

    public byte[] getTxTrieRoot() {
        if (!parsed)
            parseRLP();
        return this.header.getTxTrieRoot();
    }

    public byte[] getReceiptsRoot() {
        if (!parsed)
            parseRLP();
        return this.header.getReceiptsRoot();
    }


    public byte[] getLogBloom() {
        if (!parsed)
            parseRLP();
        return this.header.getLogsBloom();
    }

    public byte[] getDifficulty() {
        if (!parsed)
            parseRLP();
        return this.header.getDifficulty();
    }

    public BigInteger getDifficultyBI() {
        if (!parsed)
            parseRLP();
        return this.header.getDifficultyBI();
    }

    public long getFeesPaidToMiner() {
        if (!parsed)
            parseRLP();
        return this.header.getPaidFees();
    }

    public BigInteger getCumulativeDifficulty() {
        if (!parsed)
            parseRLP();
        BigInteger calcDifficulty = new BigInteger(1, this.header.getDifficulty());
        for (BlockHeader uncle : uncleList) {
            calcDifficulty = calcDifficulty.add(new BigInteger(1, uncle.getDifficulty()));
        }
        return calcDifficulty;
    }

    public long getTimestamp() {
        if (!parsed)
            parseRLP();
        return this.header.getTimestamp();
    }

    public long getNumber() {
        if (!parsed)
            parseRLP();
        return this.header.getNumber();
    }

    public byte[] getGasLimit() {
        if (!parsed)
            parseRLP();
        return this.header.getGasLimit();
    }

    public long getGasUsed() {
        if (!parsed)
            parseRLP();
        return this.header.getGasUsed();
    }

    public byte[] getExtraData() {
        if (!parsed)
            parseRLP();
        return this.header.getExtraData();
    }

  public void setExtraData(byte[] data) {
      /* A sealed block is immutable, cannot be changed */
      if (this.sealed)
          throw new SealedBlockException("trying to alter extra data");

        this.header.setExtraData(data);
        rlpEncoded = null;
    }

    public List<Transaction> getTransactionsList() {
        if (!parsed)
            parseRLP();

        return Collections.unmodifiableList(this.transactionsList);
    }

    public List<BlockHeader> getUncleList() {
        if (!parsed)
            parseRLP();

        return Collections.unmodifiableList(this.uncleList);
    }

    public byte[] getMinimumGasPrice() {
        if (!parsed)
            parseRLP();
        return this.header.getMinimumGasPrice();
    }

    private StringBuffer toStringBuff = new StringBuffer();
    // [parent_hash, uncles_hash, coinbase, state_root, tx_trie_root,
    // difficulty, number, minGasPrice, gasLimit, gasUsed, timestamp,
    // extradata, nonce]

    @Override
    public String toString() {
        if (!parsed)
            return "unparsed:"+super.toString();

        if (!parsed)
            parseRLP();

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash())).append("\n");
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
        toStringBuff.append("hash=").append(ByteUtil.toHexString(this.getHash()));
        toStringBuff.append(header.toFlatString());

        for (Transaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }

        toStringBuff.append("]");
        return toStringBuff.toString();
    }

    private void parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();

        this.txsState = new TrieImpl();
        int txsStateIndex = 0;
        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (isRemascTransaction(tx, i, txTransactions.size())) {
                // It is the remasc transaction
                tx = new RemascTransaction(transactionRaw.getRLPData());
            }
            parsedTxs.add(tx);
            this.txsState.put(RLP.encodeInt(txsStateIndex), transactionRaw.getRLPData());
            txsStateIndex++;
        }

        this.transactionsList = Collections.unmodifiableList(parsedTxs);
    }

    private boolean isRemascTransaction(Transaction tx, int txPosition, int txsSize) {

        return isLastTx(txPosition, txsSize) && checkRemascAddress(tx) && checkRemascTxZeroValues(tx);
    }

    private boolean isLastTx(int txPosition, int txsSize) {
        return txPosition == (txsSize - 1);
    }

    private boolean checkRemascAddress(Transaction tx) {
        return Arrays.areEqual(Hex.decode(PrecompiledContracts.REMASC_ADDR), tx.getReceiveAddress());
    }

    private boolean checkRemascTxZeroValues(Transaction tx) {
        if(null != tx.getData() || null != tx.getSignature()){
            return false;
        }

        return BigInteger.ZERO.equals(new BigInteger(1, tx.getValue())) &&
                BigInteger.ZERO.equals(new BigInteger(1, tx.getGasLimit())) &&
                BigInteger.ZERO.equals(new BigInteger(1, tx.getGasPrice()));

    }

    private boolean parseTxs(byte[] expectedRoot, RLPList txTransactions) {

        parseTxs(txTransactions);
        String calculatedRoot = Hex.toHexString(txsState.getHash());
        if (!calculatedRoot.equals(Hex.toHexString(expectedRoot))) {
            logger.error("Transactions trie root validation failed for block #{}", this.header.getNumber());
            panicProcessor.panic("txtrie", String.format("Transactions trie root validation failed for block %d %s", this.header.getNumber(), Hex.toHexString(this.header.getHash())));
            return false;
        }

        return true;
    }

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(Block block) {
        return Arrays.areEqual(this.getHash(), block.getParentHash());
    }

    public boolean isGenesis() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.isGenesis();
    }

    public boolean isEqual(Block block) {
        return Arrays.areEqual(this.getHash(), block.getHash());
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
        if (this.sealed)
            throw new SealedBlockException("trying to add uncle");

        uncleList.add(uncle);
        this.getHeader().setUnclesHash(SHA3Helper.sha3(getUnclesEncoded()));
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

        return Hex.toHexString(getHash()).substring(0, 6);
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
        return "#" + getNumber() + " (" + Hex.toHexString(getHash()).substring(0,6) + " <~ "
                + Hex.toHexString(getParentHash()).substring(0,6) + ") Txs:" + getTransactionsList().size() +
                ", Unc: " + getUncleList().size();
    }

    public byte[] getBitcoinMergedMiningHeader() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.getBitcoinMergedMiningHeader();
    }

    public void setBitcoinMergedMiningHeader(byte[] bitcoinMergedMiningHeader) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed)
            throw new SealedBlockException("trying to alter bitcoin merged mining header");

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
        if (this.sealed)
            throw new SealedBlockException("trying to alter bitcoin merged mining Merkle proof");

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
        if (this.sealed)
            throw new SealedBlockException("trying to alter bitcoin merged mining coinbase transaction");

        this.header.setBitcoinMergedMiningCoinbaseTransaction(bitcoinMergedMiningCoinbaseTransaction);
        rlpEncoded = null;
    }

    public static Trie getTxTrie(List<Transaction> transactions){
        Trie txsState = new TrieImpl();
        int itran = 0;

        if (transactions != null) {
            for (int i = 0; i < transactions.size(); i++) {
                Transaction transaction = transactions.get(i);

                txsState.put(RLP.encodeInt(itran), transaction.getEncoded());
                itran++;
            }
        }

        return txsState;
    }

    public BigInteger getMinGasPriceAsInteger() {
        return (this.getMinimumGasPrice() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getMinimumGasPrice());
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    private byte[] calcTxTrie(List<Transaction> transactions){
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed)
            throw new SealedBlockException("trying to alter transaction root");

        this.txsState = getTxTrie(transactions);

        return txsState.getHash();
    }

    public void flushRLP() {
        this.rlpEncoded = null;
        this.parsed = true;
    }
}
