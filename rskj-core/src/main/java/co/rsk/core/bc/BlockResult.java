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
import co.rsk.trie.OldTrieImpl;
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
    public static final BlockResult INTERRUPTED_EXECUTION_BLOCK_RESULT  = new InterruptedExecutionBlockResult();

    private final List<Transaction> executedTransactions;
    private final List<TransactionReceipt> transactionReceipts;
    private final byte[] stateRoot;
    private final byte[] receiptsRoot;
    private final long gasUsed;
    private final Coin paidFees;
    private final byte[] logsBloom;



    public BlockResult(List<Transaction> executedTransactions, List<TransactionReceipt> transactionReceipts,
                       byte[] stateRoot, long gasUsed, Coin paidFees,boolean hardfork9999) {
        this.executedTransactions = executedTransactions;
        this.transactionReceipts = transactionReceipts;
        this.stateRoot = stateRoot;
        this.gasUsed = gasUsed;
        this.paidFees = paidFees;

        if (hardfork9999)
            this.receiptsRoot = calculateReceiptsTrieRootNew(transactionReceipts);
        else
            this.receiptsRoot = calculateReceiptsTrieRootOld(transactionReceipts);

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

    public static byte[] calculateReceiptsTrieRoot(List<TransactionReceipt> receipts,boolean hardfork9999) {
        if (hardfork9999)
            return calculateReceiptsTrieRootNew(receipts);
        else
            return calculateReceiptsTrieRootOld(receipts);

    }

    public static byte[] calculateReceiptsTrieRootNew(List<TransactionReceipt> receipts) {
        Trie receiptsTrie = new TrieImpl();

        if (receipts.isEmpty()) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie.getHash().getBytes();
    }

    // from original BlockchainImpl
    public static byte[] calculateReceiptsTrieRootOld(List<TransactionReceipt> receipts) {
        OldTrieImpl receiptsTrie = new OldTrieImpl();

        if (receipts.isEmpty()) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie.getHash().getBytes();
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
            // it doesn't matter if it's pre or post HF9999
            super(Collections.emptyList(), Collections.emptyList(), null, 0, Coin.ZERO,false);
        }
    }
}
