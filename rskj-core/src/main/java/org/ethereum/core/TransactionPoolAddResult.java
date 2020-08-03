/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TransactionPoolAddResult {
    private final String errorMessage;
    private final List<Transaction> transactionsAdded;
    private final List<Transaction> queuedTransactionsAdded;
    private final List<Transaction> pendingTransactionsAdded;

    private TransactionPoolAddResult(String errorMessage, List<Transaction> transactionsAdded, List<Transaction> queuedTransactionsAdded, List<Transaction> pendingTransactionsAdded) {
        this.errorMessage = errorMessage;
        this.transactionsAdded = Collections.unmodifiableList(transactionsAdded);
        this.queuedTransactionsAdded = Collections.unmodifiableList(queuedTransactionsAdded);
        this.pendingTransactionsAdded = Collections.unmodifiableList(pendingTransactionsAdded);
    }

    public boolean transactionsWereAdded() {
        return pendingTransactionsWereAdded() || queuedTransactionsWereAdded();
    }

    private boolean queuedTransactionsWereAdded() {
        return queuedTransactionsAdded != null && !queuedTransactionsAdded.isEmpty();
    }

    public boolean pendingTransactionsWereAdded() {
        return pendingTransactionsAdded != null && !pendingTransactionsAdded.isEmpty();
    }

    public List<Transaction> getTransactionsAdded() {
        return transactionsAdded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static TransactionPoolAddResult okQueuedTransaction(Transaction tx) {
        return new TransactionPoolAddResult(null, Collections.emptyList(), Collections.singletonList(tx), Collections.emptyList());
    }

    public static TransactionPoolAddResult okPendingTransaction(Transaction tx) {
        return new TransactionPoolAddResult(null, Collections.emptyList(), Collections.emptyList(), Collections.singletonList(tx));
    }

    public static TransactionPoolAddResult withError(String errorMessage) {
        return new TransactionPoolAddResult(errorMessage, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static TransactionPoolAddResult okPendingTransactions(List<Transaction> pendingTransactionsAdded) {
        return new TransactionPoolAddResult(null, Collections.emptyList(), Collections.emptyList(), pendingTransactionsAdded);
    }
}
