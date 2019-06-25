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

package co.rsk.net.messages;

import co.rsk.net.utils.TransactionUtils;
import java.util.List;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

/** Created by ajlopez on 7/22/2016. */
public class TransactionsMessageTest {
    @Test
    public void getMessageType() {
        TransactionsMessage message = new TransactionsMessage(null);
        Assert.assertEquals(MessageType.TRANSACTIONS, message.getMessageType());
    }

    @Test
    public void setAndGetTransactions() {
        List<Transaction> txs = TransactionUtils.getTransactions(10);
        TransactionsMessage message = new TransactionsMessage(txs);

        Assert.assertNotNull(message.getTransactions());
        Assert.assertEquals(10, message.getTransactions().size());
        Assert.assertSame(txs, message.getTransactions());
    }
}
