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
import java.util.Collections;
import java.util.List;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;

/** Created by ajlopez on 01/08/2016. */
public class BlockResult {
    public static final BlockResult INTERRUPTED_EXECUTION_BLOCK_RESULT =
            new BlockResult(Collections.emptyList(), Collections.emptyList(), 0, Coin.ZERO, null);

    private final List<Transaction> executedTransactions;
    private final List<TransactionReceipt> transactionReceipts;
    private final long gasUsed;
    private final Coin paidFees;

    // It is for optimizing switching between states. Instead of using the "stateRoot" field,
    // which requires regenerating the trie, using the finalState field does not.
    private final Trie finalState;

    public BlockResult(
            List<Transaction> executedTransactions,
            List<TransactionReceipt> transactionReceipts,
            long gasUsed,
            Coin paidFees,
            Trie finalState) {
        this.executedTransactions = executedTransactions;
        this.transactionReceipts = transactionReceipts;
        this.gasUsed = gasUsed;
        this.paidFees = paidFees;
        this.finalState = finalState;
    }

    public List<Transaction> getExecutedTransactions() {
        return executedTransactions;
    }

    public List<TransactionReceipt> getTransactionReceipts() {
        return this.transactionReceipts;
    }

    public long getGasUsed() {
        return this.gasUsed;
    }

    public Coin getPaidFees() {
        return this.paidFees;
    }

    public Trie getFinalState() {
        return this.finalState;
    }
}
