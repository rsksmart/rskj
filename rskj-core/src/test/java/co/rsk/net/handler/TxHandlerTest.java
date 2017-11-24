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

package co.rsk.net.handler;

import co.rsk.TestHelpers.Tx;
import co.rsk.core.bc.EventInfoItem;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.TypeConverter;
import org.junit.Test;
import org.mockito.Mockito;
import org.testng.Assert;

import java.math.BigInteger;
import java.util.*;

public class TxHandlerTest {

    @Test
    public void cleanOldTxsTest() {
        long time = System.currentTimeMillis();
        final long threshold = 300001;
        Random random = new Random(0);

        Transaction tx1 = Tx.create(0, 0, 0, 0, 0, 0, random);
        Transaction tx2 = Tx.create(0, 0, 0, 1, 0, 0, random);

        Map<String, TxTimestamp> knownTxs = new HashMap<>();
        Map<String, TxsPerAccount> txsPerAccounts = new HashMap<>();

        knownTxs.put("1", new TxTimestamp(tx1, time));
        knownTxs.put("2", new TxTimestamp(tx1, time - threshold));

        TxsPerAccount tpa = new TxsPerAccount();
        tpa.getTransactions().add(tx1);
        tpa.getTransactions().add(tx2);
        txsPerAccounts.put(TypeConverter.toJsonHex(tx1.getSender()), tpa);

        TxHandlerImpl txHandler = new TxHandlerImpl();
        txHandler.setKnownTxs(knownTxs);
        txHandler.setTxsPerAccounts(txsPerAccounts);

        txHandler.cleanOldTxs();

        Assert.assertEquals(1, txHandler.getKnownTxs().keySet().size());
        Assert.assertEquals(1, txHandler.getTxsPerAccounts().entrySet().iterator().next().getValue().getTransactions().size());
    }

    @Test
    public void cleanOldTxsAndTxsPerAccountTest() {
        long time = System.currentTimeMillis();
        final long threshold = 300001;
        Random random = new Random(0);

        Transaction tx1 = Tx.create(0, 0, 0, 0, 0, 0, random);

        Map<String, TxTimestamp> knownTxs = new HashMap<>();
        Map<String, TxsPerAccount> txsPerAccounts = new HashMap<>();

        knownTxs.put("2", new TxTimestamp(tx1, time - threshold));

        TxsPerAccount tpa = new TxsPerAccount();
        tpa.getTransactions().add(tx1);
        txsPerAccounts.put(TypeConverter.toJsonHex(tx1.getSender()), tpa);

        TxHandlerImpl txHandler = new TxHandlerImpl();
        txHandler.setKnownTxs(knownTxs);
        txHandler.setTxsPerAccounts(txsPerAccounts);

        txHandler.cleanOldTxs();

        Assert.assertTrue(txHandler.getKnownTxs().isEmpty());
        Assert.assertTrue(txHandler.getTxsPerAccounts().isEmpty());
    }

    @Test
    public void listenerTest() {
        long time = System.currentTimeMillis();
        Random random = new Random(0);

        Transaction tx1 = Tx.create(0, 0, 0, 0, 0, 0, random);
        Transaction tx2 = Tx.create(0, 0, 0, 1, 0, 0, random);

        Map<String, TxTimestamp> knownTxs = new HashMap<>();
        Map<String, TxsPerAccount> txsPerAccounts = new HashMap<>();

        random = new Random(0);
        String hash1 = TypeConverter.toJsonHex(BigInteger.valueOf(random.nextLong()).toByteArray());
        String hash2 = TypeConverter.toJsonHex(BigInteger.valueOf(random.nextLong()).toByteArray());

        knownTxs.put(hash1, new TxTimestamp(tx1, time));
        knownTxs.put(hash2, new TxTimestamp(tx1, time));

        TxsPerAccount tpa = new TxsPerAccount();
        tpa.getTransactions().add(tx1);
        tpa.getTransactions().add(tx2);
        txsPerAccounts.put(TypeConverter.toJsonHex(BigInteger.valueOf(new Random(0).nextLong()).toByteArray()), tpa);

        TransactionReceipt receipt = Mockito.mock(TransactionReceipt.class);
        Mockito.when(receipt.getTransaction()).thenReturn(tx1);
        List<TransactionReceipt> receiptList = new LinkedList<>();
        receiptList.add(receipt);

        TxHandlerImpl txHandler = new TxHandlerImpl();
        txHandler.setTxsPerAccounts(txsPerAccounts);
        txHandler.setKnownTxs(knownTxs);

        List<EventInfoItem> eventList = new ArrayList<>();

        txHandler.onBlock(null, receiptList,eventList);

        Assert.assertEquals(1, txHandler.getKnownTxs().keySet().size());
        Assert.assertEquals(1, txHandler.getTxsPerAccounts().entrySet().iterator().next().getValue().getTransactions().size());
    }
}
