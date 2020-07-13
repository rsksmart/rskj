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
package co.rsk.net;

import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.rpc.modules.RskJsonRpcRequest;
import org.ethereum.TestUtils;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

public class TransactionGatewayTest {
    private ChannelManager channelManager;
    private TransactionPool transactionPool;
    private TransactionGateway gateway;
    private Transaction tx;

    @Before
    public void setUp() {
        this.channelManager = mock(ChannelManager.class);
        this.transactionPool = mock(TransactionPoolImpl.class);
        this.tx = mock(Transaction.class);
        when(this.tx.getHash()).thenReturn(TestUtils.randomHash());

        this.gateway = new TransactionGateway(channelManager, transactionPool);
    }

    @Test
    public void receiveTranasctionsFrom_newTransactions_shouldAddAndBroadcast() {
        List<Transaction> transactions = Collections.singletonList(tx);

        when(transactionPool.addTransactions(transactions)).thenReturn(transactions);
        when(transactionPool.transactionsWereAdded(transactions)).thenCallRealMethod();

        this.gateway.receiveTransactionsFrom(transactions, Collections.emptySet());

        verify(transactionPool, times(1)).addTransactions(transactions);
        verify(channelManager, times(1)).broadcastTransactions(transactions, Collections.emptySet());
    }

    @Test
    public void receiveTransactionsFrom_transactionsAlreadyAdded_shouldntAddAndShouldntBroadcast() {
        List<Transaction> transactions = Collections.singletonList(tx);
        List<Transaction> addTransactionsResult = Collections.emptyList();
        Set<NodeID> nodeIDS = Collections.emptySet();

        when(transactionPool.addTransactions(transactions)).thenReturn(addTransactionsResult);
        when(transactionPool.transactionsWereAdded(transactions)).thenCallRealMethod();

        this.gateway.receiveTransactionsFrom(transactions, nodeIDS);

        verify(transactionPool, times(1)).addTransactions(transactions);
        verify(channelManager, times(0)).broadcastTransactions(transactions, nodeIDS);
    }

    @Test
    public void receiveTransaction_newTransaction_shouldAddAndBroadcast() {
        List<Transaction> transactionsAdded = Collections.singletonList(tx);

        when(transactionPool.addTransaction(tx)).thenReturn(transactionsAdded);
        when(transactionPool.transactionsWereAdded(transactionsAdded)).thenCallRealMethod();

        this.gateway.receiveTransaction(tx);

        verify(transactionPool, times(1)).addTransaction(tx);
        verify(channelManager, times(1)).
                broadcastTransactions(transactionsAdded, Collections.emptySet());
    }

    @Test
    public void receiveTransaction_alreadyAddedTransaction_shouldntAddAndShouldntBroadcast() {
        List<Transaction> transactionsAdded = null;

        when(transactionPool.addTransaction(tx)).thenReturn(transactionsAdded);
        when(transactionPool.transactionsWereAdded(transactionsAdded)).thenCallRealMethod();

        try {
            this.gateway.receiveTransaction(tx);
        } catch (RskJsonRpcRequestException e) {
            Assert.assertEquals("Not added", e.getMessage());
        } finally {
            verify(transactionPool, times(1)).addTransaction(tx);
            verify(channelManager, times(0)).
                    broadcastTransactions(transactionsAdded, Collections.emptySet());
        }
    }
}
