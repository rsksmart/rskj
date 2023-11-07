package co.rsk.core;

import co.rsk.trie.Trie;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by SDL on 12/5/2017.
 */
public class FreeBlock {

    private FreeBlockHeader header;

    // The methods below make sure we use immutable lists
    /* Transactions */
    private List<Transaction> transactionsList;

    /* Uncles */
    private List<FreeBlockHeader> uncleList = new CopyOnWriteArrayList<>();

    /* Private */
    private byte[] rlpEncoded;
    private boolean parsed = false;

    public FreeBlock(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, byte[] number, byte[] gasLimit,
                     byte[] gasUsed, byte[] timestamp, byte[] extraData,
                 byte[] mixHash, byte[] nonce, byte[] receiptsRoot,
                 byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList,
                     List<FreeBlockHeader> uncleList, byte[] minimumGasPrice,
                     byte[] paidFees) {

        this(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit,
                gasUsed, timestamp, extraData, mixHash, nonce, transactionsList, uncleList, minimumGasPrice);

        this.header.setPaidFees(paidFees);

        byte[] calculatedRoot = getTxTrie(transactionsList).getHash().getBytes();
        this.header.setTransactionsRoot(calculatedRoot);

        this.header.setStateRoot(stateRoot);
        this.header.setReceiptsRoot(receiptsRoot);

        this.flushRLP();
    }

    public FreeBlock(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                 byte[] difficulty, byte[]  number, byte[] gasLimit,
                 byte[] gasUsed, byte[] timestamp,
                 byte[] extraData, byte[] mixHash, byte[] nonce,
                 List<Transaction> transactionsList, List<FreeBlockHeader> uncleList, byte[] minimumGasPrice) {

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

        this.header = new FreeBlockHeader(parentHash, unclesHash, coinbase, logsBloom,
                difficulty, number, gasLimit, gasUsed,
                timestamp, extraData, minimumGasPrice, BigInteger.valueOf(this.uncleList.size()).toByteArray());

        this.parsed = true;
    }


    private void parseRLP() {
        ArrayList<RLPElement> params = RLP.decodeListElements(rlpEncoded);
        RLPList block = (RLPList) params.get(0);

        // Parse Header
        RLPList header = (RLPList) block.get(0);
        this.header = new FreeBlockHeader(header);

        // Parse Transactions
        RLPList txTransactions = (RLPList) block.get(1);
        this.transactionsList = parseTxs(txTransactions);
        byte[] calculatedRoot = getTxTrie(this.transactionsList).getHash().getBytes();

        // Parse Uncles
        RLPList uncleBlocks = (RLPList) block.get(2);

        for (int k = 0; k < uncleBlocks.size(); k++) {
            RLPElement rawUncle = uncleBlocks.get(k);
            RLPList uncleHeader = (RLPList) rawUncle;
            FreeBlockHeader blockData = new FreeBlockHeader(uncleHeader);
            this.uncleList.add(blockData);
        }

        this.parsed = true;
    }

    public FreeBlockHeader getHeader() {
        if (!parsed) {
            parseRLP();
        }
        return this.header;
    }

    public byte[] getHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getHash();
    }

    public byte[] getParentHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getParentHash();
    }

    public byte[] getCoinbase() {
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

        if (!parsed) {
            parseRLP();
        }
        this.header.setStateRoot(stateRoot);
    }

    public byte[] getDifficulty() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getDifficulty();
    }

    public byte[] getTimestamp() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getTimestamp();
    }

    public byte[] getNumber() {
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

    public byte[] getGasUsed() {
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

        this.header.setExtraData(data);
        rlpEncoded = null;
    }

    private static List<Transaction> parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
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
        for (FreeBlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
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
    public static Trie getTxTrie(List<Transaction> transactions){
        if (transactions == null) {
            return new Trie();
        }

        Trie txsState = new Trie();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }

    public void flushRLP() {
        this.rlpEncoded = null;
        this.parsed = true;
    }
}
