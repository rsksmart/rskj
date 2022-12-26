/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.*;

/**
 * Created by ajlopez on 28/02/2018.
 */
class TransactionSetTest {

    private SignatureCache signatureCache;

    @BeforeEach
    public void setup() {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    }
    @Test
    void getEmptyTransactionList() {
        TransactionSet txset = new TransactionSet(signatureCache);

        List<Transaction> result = txset.getTransactions();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void transactionIsNotInEmptySet() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction transaction = createSampleTransaction();

        Assertions.assertFalse(txset.hasTransaction(transaction));
        List<Transaction> result = txset.getTransactions();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void hasTransaction() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction transaction1 = createSampleTransaction(10);
        Transaction transaction2 = createSampleTransaction(20);
        Transaction transaction3 = createSampleTransaction(30);

        txset.addTransaction(transaction1);
        txset.addTransaction(transaction2);

        Assertions.assertTrue(txset.hasTransaction(transaction1));
        Assertions.assertTrue(txset.hasTransaction(transaction2));
        Assertions.assertFalse(txset.hasTransaction(transaction3));
    }

    @Test
    void addAndRemoveTransactions() {
        TransactionSet txset = new TransactionSet(signatureCache);

        Transaction transaction1 = createSampleTransaction(1, 2, 100, 0);
        Transaction transaction2 = createSampleTransaction(2, 3, 200, 0);
        Transaction transaction3 = createSampleTransaction(3, 4, 300, 0);

        txset.addTransaction(transaction1);
        txset.addTransaction(transaction2);
        txset.addTransaction(transaction3);

        txset.removeTransactionByHash(transaction1.getHash());
        txset.removeTransactionByHash(transaction2.getHash());
        txset.removeTransactionByHash(transaction3.getHash());

        Assertions.assertFalse(txset.hasTransaction(transaction1));
        Assertions.assertFalse(txset.hasTransaction(transaction2));
        Assertions.assertFalse(txset.hasTransaction(transaction3));

        Assertions.assertTrue(txset.getTransactions().isEmpty());
        Assertions.assertTrue(txset.getTransactionsWithSender(transaction1.getSender()).isEmpty());
        Assertions.assertTrue(txset.getTransactionsWithSender(transaction2.getSender()).isEmpty());
        Assertions.assertTrue(txset.getTransactionsWithSender(transaction3.getSender()).isEmpty());
    }

    @Test
    void addTransactionAndGetListWithOneTransaction() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction tx = createSampleTransaction();

        txset.addTransaction(tx);

        List<Transaction> result = txset.getTransactions();

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(tx.getHash(), result.get(0).getHash());
    }

    @Test
    void addtTransactionTwiceAndGetListWithOneTransaction() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);
        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactions();

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(transaction.getHash(), result.get(0).getHash());
    }

    @Test
    void getEmptyTransactionListByUnknownSender() {
        TransactionSet txset = new TransactionSet(signatureCache);

        List<Transaction> result = txset.getTransactionsWithSender(new RskAddress(new byte[20]));

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void addTransactionAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactionsWithSender(transaction.getSender());

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(transaction.getHash(), result.get(0).getHash());
    }

    @Test
    void addTransactionTwiceAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet(signatureCache);
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);
        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactionsWithSender(transaction.getSender());

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(transaction.getHash(), result.get(0).getHash());
    }
}
