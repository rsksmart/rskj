/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.core;

import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

class TransactionListExecutorTest {

    @Test
    void addTransactionListToTransactions() {
        TransactionListExecutor executor = new TransactionListExecutor(
                        new ArrayList<>(),
                        null,
                        null,
                        null,
                        false,
                        0,
                        new HashSet<>(),
                        false,
                        false,
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>(),
                        false,
                        null,
                        0,
                        null,
                        false,
                        new HashSet<>(),
                        6800000L);

        List<Transaction> transactionsToAdd = new ArrayList<>();
        transactionsToAdd.add(Transaction.builder().build());
        transactionsToAdd.add(Transaction.builder().build());

        Assertions.assertEquals(0, executor.getTransactions().size());

        executor.addTransactions(transactionsToAdd);

        Assertions.assertEquals(2, executor.getTransactions().size());
    }

    @Test
    void addAdditionalGasLimit() {
        TransactionListExecutor executor = new TransactionListExecutor(
                new ArrayList<>(),
                null,
                null,
                null,
                false,
                0,
                new HashSet<>(),
                false,
                false,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                false,
                null,
                0,
                null,
                false,
                new HashSet<>(),
                6800000L);

        Assertions.assertEquals(6800000L, executor.getSublistGasLimit());

        executor.addGasLimit(1360000L);

        Assertions.assertEquals(8160000L, executor.getSublistGasLimit());
    }
}
