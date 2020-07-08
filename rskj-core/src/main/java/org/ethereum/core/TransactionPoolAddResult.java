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
    private final boolean transactionWasAdded;
    private final String errorMessage;
    private final List<Transaction> transactionsAdded;

    private TransactionPoolAddResult(boolean transactionWasAdded, String errorMessage, List<Transaction> transactionsAdded) {
        this.transactionWasAdded = transactionWasAdded;
        this.errorMessage = errorMessage;
        this.transactionsAdded = Collections.unmodifiableList(transactionsAdded);
    }

    public boolean transactionWasAdded() {
        return transactionWasAdded;
    }

    /**
     * This is mainly used to throw exceptions on the RPC avoiding the use of getters
     */
    public void ifTransactionWasNotAdded(Consumer<String> errorConsumer) {
        if (!transactionWasAdded) {
            errorConsumer.accept(errorMessage);
        }
    }

    public static TransactionPoolAddResult ok(Transaction transaction) {
        return new TransactionPoolAddResult(true, null, Collections.singletonList(transaction));
    }

    public static TransactionPoolAddResult withError(String errorMessage) {
        return new TransactionPoolAddResult(false, errorMessage, Collections.emptyList());
    }

    public List<Transaction> getTransactionsAdded() {
        return transactionsAdded;
    }
}
