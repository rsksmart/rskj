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
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;

class TransactionsMessageTest {
    @Test
    void getMessageType() {
        TransactionsMessage message = new TransactionsMessage(null);
        Assertions.assertEquals(MessageType.TRANSACTIONS, message.getMessageType());
    }

    @Test
    void setAndGetTransactions() {
        List<Transaction> txs = TransactionUtils.getTransactions(10);
        TransactionsMessage message = new TransactionsMessage(txs);

        Assertions.assertNotNull(message.getTransactions());
        Assertions.assertEquals(10, message.getTransactions().size());
        Assertions.assertSame(txs, message.getTransactions());
    }

    @Test
    void accept() {
        List<Transaction> txs = new LinkedList<>();
        TransactionsMessage message = new TransactionsMessage(txs);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
