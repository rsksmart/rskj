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
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;

import java.util.HashMap;
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
    private Events events;
    private byte[] stateRoot;
    private byte[] receiptsRoot;
    private byte[] eventsRoot;
    private long gasUsed;
    private long paidFees;
    private byte[] logsBloom;

    private BlockResult(boolean interruptedExecution) {
        // Just to create INTERRUPTED_EXECUTION_BLOCK_RESULT
        this.interruptedExecution = interruptedExecution;
    }

    public BlockResult(List<Transaction> executedTransactions,
                       List<TransactionReceipt> transactionReceipts,
                       List<EventInfoItem> events,
                       byte[] stateRoot, long gasUsed, long paidFees) {
        interruptedExecution = false;
        this.executedTransactions = executedTransactions;
        this.transactionReceipts = transactionReceipts;
        this.events = new Events();
        this.events.addAll(events);
        this.stateRoot = stateRoot;
        this.gasUsed = gasUsed;
        this.paidFees = paidFees;

        this.eventsRoot = calculateEventsTrie(events);
        this.receiptsRoot = calculateReceiptsTrie(transactionReceipts);

        this.logsBloom = calculateLogsBloom(transactionReceipts,events);
    }

    public List<Transaction> getExecutedTransactions() { return executedTransactions; }

    public List<TransactionReceipt> getTransactionReceipts() {
        return this.transactionReceipts;
    }

    public Events getEvents() {
        return this.events;
    }

    public byte[] getStateRoot() {
        return this.stateRoot;
    }

    public byte[] getEventsRoot() {
        return this.eventsRoot;
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
    public static byte[] calculateReceiptsTrie(List<TransactionReceipt> receipts) {
        //TODO Fix Trie hash for receipts - doesnt match cpp
        Trie receiptsTrie = new TrieImpl();

        if (receipts == null || receipts.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        for (int i = 0; i < receipts.size(); i++)
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());

        return receiptsTrie.getHash();
    }


    public static byte[] calculateEventsPerAccountTrie(EventsPerAccount cl) {

        if (cl == null )
            return HashUtil.EMPTY_TRIE_HASH;

        List<EventInfo> t = cl.getList();

        if (t == null || t.isEmpty())
            return HashUtil.EMPTY_TRIE_HASH;

        Trie aTrie = new TrieImpl();
        for (int i=0;i<t.size();i++) {
            byte[] key = RLP.encodeInt(i);
            aTrie = aTrie.put(key, t.get(i).getEncoded());
        }
        return aTrie.getHash();
    }

    public static Map<ByteArrayWrapper,EventsPerAccount> getEventsMapFromList(List<EventInfoItem> events) {

        Map<ByteArrayWrapper,EventsPerAccount> eventsPerAccountMap = new HashMap<>();

        // Associate all events for the same contract in a single try
        for (EventInfoItem eventInfoItem : events) {
            EventsPerAccount t = eventsPerAccountMap.get(eventInfoItem.address);
            if (t==null) {
                t = new EventsPerAccount();
                eventsPerAccountMap.put(new ByteArrayWrapper(eventInfoItem.address),t);
            }
            t.add(eventInfoItem.eventInfo);
        }
        return eventsPerAccountMap;
    }

    public static byte[] calculateEventsTrie(List<EventInfoItem> events) {
        Trie clTrie = new TrieImpl();

        if (events == null)
            return HashUtil.EMPTY_TRIE_HASH;

        Map<ByteArrayWrapper,EventsPerAccount> eventsPerAccountMap = getEventsMapFromList(events);

        for (ByteArrayWrapper addr : eventsPerAccountMap.keySet()) {
            clTrie = clTrie.put(RLP.encodeElement(addr.getData()), calculateEventsPerAccountTrie(eventsPerAccountMap.get(addr)));
        }
        return clTrie.getHash();
    }

    public static byte[] calculateLogsBloom(List<TransactionReceipt> receipts,List<EventInfoItem> events) {
        Bloom logBloom = new Bloom();

        for (TransactionReceipt receipt : receipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        for (EventInfoItem item : events) {
            logBloom.or(item.getBloomFilter());
        }
        return logBloom.getData();
    }

    public boolean getInterruptedExecution() {
        return interruptedExecution;
    }
}
