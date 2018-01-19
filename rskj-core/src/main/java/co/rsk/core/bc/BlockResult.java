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

import co.rsk.core.Coin;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import java.util.Collections;
import java.util.List;

/**
 * Created by ajlopez on 01/08/2016.
 */
public class BlockResult {
    public static final BlockResult INTERRUPTED_EXECUTION_BLOCK_RESULT = new InterruptedExecutionBlockResult();

    private final List<Transaction> executedTransactions;
    private final List<TransactionReceipt> transactionReceipts;
    private final byte[] stateRoot;
    private final byte[] receiptsRoot;
    private final long gasUsed;
    private final Coin paidFees;
    private final byte[] logsBloom;

    public BlockResult(List<Transaction> executedTransactions, List<TransactionReceipt> transactionReceipts,
                       byte[] stateRoot, long gasUsed, Coin paidFees) {
        this.executedTransactions = executedTransactions;
        this.transactionReceipts = transactionReceipts;
        this.stateRoot = stateRoot;
        this.gasUsed = gasUsed;
        this.paidFees = paidFees;

        this.receiptsRoot = calculateReceiptsTrie(transactionReceipts);
        this.logsBloom = calculateLogsBloom(transactionReceipts);
    }

    public List<Transaction> getExecutedTransactions() { return executedTransactions; }

    public List<TransactionReceipt> getTransactionReceipts() {
        return this.transactionReceipts;
    }

    public byte[] getStateRoot() {
        return this.stateRoot;
    }

    public byte[] getReceiptsRoot() {
        return this.receiptsRoot;
    }

    public byte[] getLogsBloom() { return this.logsBloom; }

    public long getGasUsed() {
        return this.gasUsed;
    }

    public Coin getPaidFees() {
        return this.paidFees;
    }

    // from original BlockchainImpl
    private static byte[] calculateReceiptsTrie(List<TransactionReceipt> receipts) {
        //TODO Fix Trie hash for receipts - doesnt match cpp
        Trie receiptsTrie = new TrieImpl();

        if (receipts.isEmpty()) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie.getHash();
    }

    private static byte[] calculateLogsBloom(List<TransactionReceipt> receipts) {
        Bloom logBloom = new Bloom();

        for (TransactionReceipt receipt : receipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        return logBloom.getData();
    }

    private static class InterruptedExecutionBlockResult extends BlockResult {
        public InterruptedExecutionBlockResult() {
            super(Collections.emptyList(), Collections.emptyList(), null, 0, Coin.ZERO);
        }
    }
}
