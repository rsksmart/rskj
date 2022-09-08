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

package org.ethereum.rpc;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class PendingTransactionFilterTest {
    @Test
    public void noEvents() {
        PendingTransactionFilter filter = new PendingTransactionFilter();

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    public void oneTransactionAndEvents() {
        PendingTransactionFilter filter = new PendingTransactionFilter();

        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .build();

        filter.newPendingTx(tx);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals("0x" + tx.getHash().toHexString(), result[0]);
    }

    @Test
    public void twoTransactionsAndEvents() {
        PendingTransactionFilter filter = new PendingTransactionFilter();

        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx1 = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .build();

        Transaction tx2 = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.ONE)
                .build();

        filter.newPendingTx(tx1);
        filter.newPendingTx(tx2);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
        Assertions.assertEquals("0x" + tx1.getHash().toHexString(), result[0]);
        Assertions.assertEquals("0x" + tx2.getHash().toHexString(), result[1]);
    }
}
