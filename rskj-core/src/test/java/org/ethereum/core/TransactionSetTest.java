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
import co.rsk.core.SenderResolverVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.ethereum.util.TransactionFactoryHelper.*;

/**
 * Created by ajlopez on 28/02/2018.
 */
public class TransactionSetTest {
    @Test
    public void getEmptyTransactionList() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());

        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void transactionIsNotInEmptySet() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction transaction = createSampleTransaction();

        Assert.assertFalse(txset.hasTransaction(transaction));
        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void hasTransaction() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction transaction1 = createSampleTransaction(10);
        Transaction transaction2 = createSampleTransaction(20);
        Transaction transaction3 = createSampleTransaction(30);

        txset.addTransaction(transaction1);
        txset.addTransaction(transaction2);

        Assert.assertTrue(txset.hasTransaction(transaction1));
        Assert.assertTrue(txset.hasTransaction(transaction2));
        Assert.assertFalse(txset.hasTransaction(transaction3));
    }

    @Test
    public void addAndRemoveTransactions() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());

        Transaction transaction1 = createSampleTransaction(1, 2, 100, 0);
        Transaction transaction2 = createSampleTransaction(2, 3, 200, 0);
        Transaction transaction3 = createSampleTransaction(3, 4, 300, 0);

        txset.addTransaction(transaction1);
        txset.addTransaction(transaction2);
        txset.addTransaction(transaction3);

        txset.removeTransactionByHash(transaction1.getHash(), new SenderResolverVisitor());
        txset.removeTransactionByHash(transaction2.getHash(), new SenderResolverVisitor());
        txset.removeTransactionByHash(transaction3.getHash(), new SenderResolverVisitor());

        Assert.assertFalse(txset.hasTransaction(transaction1));
        Assert.assertFalse(txset.hasTransaction(transaction2));
        Assert.assertFalse(txset.hasTransaction(transaction3));

        Assert.assertTrue(txset.getTransactions().isEmpty());
        Assert.assertTrue(txset.getTransactionsWithSender(transaction1.accept(new SenderResolverVisitor())).isEmpty());
        Assert.assertTrue(txset.getTransactionsWithSender(transaction2.accept(new SenderResolverVisitor())).isEmpty());
        Assert.assertTrue(txset.getTransactionsWithSender(transaction3.accept(new SenderResolverVisitor())).isEmpty());
    }

    @Test
    public void addTransactionAndGetListWithOneTransaction() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction tx = createSampleTransaction();

        txset.addTransaction(tx);

        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(tx.getHash(), result.get(0).getHash());
    }

    @Test
    public void addtTransactionTwiceAndGetListWithOneTransaction() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);
        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(transaction.getHash(), result.get(0).getHash());
    }

    @Test
    public void getEmptyTransactionListByUnknownSender() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());

        List<Transaction> result = txset.getTransactionsWithSender(new RskAddress(new byte[20]));

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void addTransactionAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactionsWithSender(transaction.accept(new SenderResolverVisitor()));

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(transaction.getHash(), result.get(0).getHash());
    }

    @Test
    public void addTransactionTwiceAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet(new SenderResolverVisitor());
        Transaction transaction = createSampleTransaction();

        txset.addTransaction(transaction);
        txset.addTransaction(transaction);

        List<Transaction> result = txset.getTransactionsWithSender(transaction.accept(new SenderResolverVisitor()));

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(transaction.getHash(), result.get(0).getHash());
    }
}
