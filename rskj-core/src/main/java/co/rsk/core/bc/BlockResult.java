/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core.bc;

import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 01/08/2016.
 */
public class BlockResult {
    public static final BlockResult INTERRUPTED_EXECUTION_BLOCK_RESULT = new BlockResult(true);

    private boolean interruptedExecution;
    private List<Transaction> executedTransactions;
    private List<TransactionReceipt> transactionReceipts;
    private PerContractLog perContractLog;
    private byte[] stateRoot;
    private byte[] receiptsRoot;
    private byte[] perContractLogRoot;
    private long gasUsed;
    private long paidFees;
    private byte[] logsBloom;

    private BlockResult(boolean interruptedExecution) {
        // Just to create INTERRUPTED_EXECUTION_BLOCK_RESULT
        this.interruptedExecution = interruptedExecution;
    }

    public BlockResult(List<Transaction> executedTransactions,
                       List<TransactionReceipt> transactionReceipts,
                       PerContractLog contractLogs,
                       byte[] stateRoot, long gasUsed, long paidFees) {
        interruptedExecution = false;
        this.executedTransactions = executedTransactions;
        this.transactionReceipts = transactionReceipts;
        this.perContractLog = contractLogs;
        this.stateRoot = stateRoot;
        this.gasUsed = gasUsed;
        this.paidFees = paidFees;

        this.perContractLogRoot = calculatePerContractLogTrie(contractLogs);
        this.receiptsRoot = calculateReceiptsTrie(transactionReceipts);

        this.logsBloom = calculateLogsBloom(transactionReceipts);
    }

    public List<Transaction> getExecutedTransactions() { return executedTransactions; }

    public List<TransactionReceipt> getTransactionReceipts() {
        return this.transactionReceipts;
    }

    public PerContractLog getPerContractLog() {
        return this.perContractLog;
    }

    public byte[] getStateRoot() {
        return this.stateRoot;
    }

    public byte[] getPerContractLogRoot() {
        return this.perContractLogRoot;
    }

    public byte[] getReceiptsRoot() {
        return this.receiptsRoot;
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }

    public long getGasUsed() {
        return this.gasUsed;
    }

    public long getPaidFees() {
        return this.paidFees;
    }

    // from original BlockchainImpl

    private static byte[] calculateReceiptsTrie(List<TransactionReceipt> receipts) {
        //TODO Fix Trie hash for receipts - doesnt match cpp
        Trie receiptsTrie = new TrieImpl();

        if (receipts == null || receipts.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (int i = 0; i < receipts.size(); i++)
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());

        return receiptsTrie.getHash();
    }


    private static byte[] calculateContractLogTrie(ContractLog cl) {

        if (cl == null )
            return HashUtil.EMPTY_TRIE_HASH;

        Map<byte[], LogInfo> t = cl.getMap();

        if (t == null || t.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        Trie aTrie = new TrieImpl();
        for (byte[] index : t.keySet()) {
            aTrie = aTrie.put(index, t.get(index).getEncoded());
        }
        return aTrie.getHash();
    }

    private static byte[] calculatePerContractLogTrie(PerContractLog pcl) {
        Trie clTrie = new TrieImpl();

        if (pcl == null)
            return HashUtil.EMPTY_TRIE_HASH;


        Map<byte[],ContractLog> contractLogs = pcl.getMap();

        if (contractLogs == null || contractLogs.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (byte[] addr : contractLogs.keySet()) {
            ContractLog t = contractLogs.get(addr);

            clTrie = clTrie.put(RLP.encodeElement(addr), calculateContractLogTrie(t));
        }
        return clTrie.getHash();
    }

    private static byte[] calculateLogsBloom(List<TransactionReceipt> receipts) {
        Bloom logBloom = new Bloom();

        for (TransactionReceipt receipt : receipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        return logBloom.getData();
    }

    public boolean getInterruptedExecution() {
        return interruptedExecution;
    }
}
