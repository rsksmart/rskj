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
        TransactionSet txset = new TransactionSet();

        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void addTransactionAndGetListWithOneTransaction() {
        TransactionSet txset = new TransactionSet();
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
        TransactionSet txset = new TransactionSet();
        Transaction tx = createSampleTransaction();

        txset.addTransaction(tx);
        txset.addTransaction(tx);

        List<Transaction> result = txset.getTransactions();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(tx.getHash(), result.get(0).getHash());
    }

    @Test
    public void getEmptyTransactionListByUnknownSender() {
        TransactionSet txset = new TransactionSet();

        List<Transaction> result = txset.getTransactionsWithSender(new RskAddress(new byte[20]));

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void addTransactionAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet();
        Transaction tx = createSampleTransaction();

        txset.addTransaction(tx);

        List<Transaction> result = txset.getTransactionsWithSender(tx.getSender());

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(tx.getHash(), result.get(0).getHash());
    }

    @Test
    public void addTransactionTwiceAndGetListBySenderWithOneTransaction() {
        TransactionSet txset = new TransactionSet();
        Transaction tx = createSampleTransaction();

        txset.addTransaction(tx);
        txset.addTransaction(tx);

        List<Transaction> result = txset.getTransactionsWithSender(tx.getSender());

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(tx.getHash(), result.get(0).getHash());
    }
}
