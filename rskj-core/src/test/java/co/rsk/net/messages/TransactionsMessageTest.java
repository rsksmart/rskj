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

import co.rsk.config.TestSystemProperties;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
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
    void typedTransactionRoundTrip() {
        List<Transaction> txs = new ArrayList<>();

        Account sender1 = new AccountBuilder().name("sender1").build();
        Account sender2 = new AccountBuilder().name("sender2").build();
        Account sender3 = new AccountBuilder().name("sender3").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        txs.add(new TransactionBuilder()
                .sender(sender1).receiver(receiver)
                .value(BigInteger.valueOf(1000)).nonce(0)
                .build());

        txs.add(new TransactionBuilder()
                .sender(sender2).receiver(receiver)
                .value(BigInteger.valueOf(2000)).nonce(0)
                .transactionType((byte) 1)
                .build());

        txs.add(new TransactionBuilder()
                .sender(sender3).receiver(receiver)
                .value(BigInteger.valueOf(3000)).nonce(0)
                .transactionType((byte) 2)
                .rskSubtype((byte) 3)
                .build());

        TransactionsMessage original = new TransactionsMessage(txs);
        byte[] encoded = original.getEncoded();

        BlockFactory blockFactory = new BlockFactory(new TestSystemProperties().getActivationConfig());
        TransactionsMessage decoded = (TransactionsMessage) Message.create(blockFactory, encoded);

        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(3, decoded.getTransactions().size());

        Transaction decodedLegacy = decoded.getTransactions().get(0);
        Assertions.assertTrue(decodedLegacy.getTypePrefix().isLegacy());
        Assertions.assertArrayEquals(txs.get(0).getHash().getBytes(), decodedLegacy.getHash().getBytes());

        Transaction decodedType1 = decoded.getTransactions().get(1);
        Assertions.assertTrue(decodedType1.getTypePrefix().isTyped());
        Assertions.assertFalse(decodedType1.getTypePrefix().isRskNamespace());
        Assertions.assertArrayEquals(txs.get(1).getHash().getBytes(), decodedType1.getHash().getBytes());

        Transaction decodedRsk = decoded.getTransactions().get(2);
        Assertions.assertTrue(decodedRsk.getTypePrefix().isRskNamespace());
        Assertions.assertInstanceOf(TransactionTypePrefix.RskNamespace.class, decodedRsk.getTypePrefix());
        Assertions.assertEquals((byte) 3, ((TransactionTypePrefix.RskNamespace) decodedRsk.getTypePrefix()).subtype());
        Assertions.assertArrayEquals(txs.get(2).getHash().getBytes(), decodedRsk.getHash().getBytes());
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
