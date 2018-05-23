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

import org.ethereum.TestUtils;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.server.ChannelManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

public class TransactionGatewayTest {
    private ChannelManager channelManager;
    private CompositeEthereumListener emitter;
    private TransactionPool transactionPool;
    private TransactionGateway gateway;

    private EthereumListener listener;
    private Transaction tx;
    private NodeID node;

    @Before
    public void setUp() {
        this.channelManager = mock(ChannelManager.class);
        this.emitter = mock(CompositeEthereumListener.class);
        this.transactionPool = mock(TransactionPool.class);
        this.gateway = new TransactionGateway(channelManager, transactionPool, emitter);
        this.gateway.start();

        ArgumentCaptor<EthereumListener> argument = ArgumentCaptor.forClass(EthereumListener.class);
        verify(emitter, times(1)).addListener(argument.capture());
        this.listener = argument.getValue();

        this.tx = mock(Transaction.class);
        when(this.tx.getHash()).thenReturn(TestUtils.randomHash());
        this.node = mock(NodeID.class);
    }

    @After
    public void tearDown() {
        verify(emitter, times(0)).removeListener(listener);
        gateway.stop();
        verify(emitter, times(1)).removeListener(listener);
    }

    @Test
    public void relayTransactionsOnPending() {
        List<Transaction> txs = Collections.singletonList(tx);
        listener.onPendingTransactionsReceived(txs);

        verify(channelManager, times(1)).broadcastTransaction(tx, Collections.emptySet());
    }

    @Test
    public void relayingTwiceSkipsReceivingNodes() {
        List<Transaction> txs = Collections.singletonList(tx);
        Set<NodeID> receivingNodes = Collections.singleton(node);
        when(channelManager.broadcastTransaction(tx, Collections.emptySet())).thenReturn(receivingNodes);
        listener.onPendingTransactionsReceived(txs);

        listener.onPendingTransactionsReceived(txs);

        verify(channelManager, times(1)).broadcastTransaction(tx, receivingNodes);
    }

    @Test
    public void addsReceivedTransactionsToTransactionPool() {
        List<Transaction> txs = Collections.singletonList(tx);

        gateway.receiveTransactionsFrom(txs, node);

        verify(transactionPool, times(1)).addTransactions(txs);
    }

    @Test
    public void relayingTransactionSkipsSenderNode() {
        List<Transaction> txs = Collections.singletonList(tx);
        gateway.receiveTransactionsFrom(txs, node);

        listener.onPendingTransactionsReceived(txs);

        verify(channelManager, times(1)).broadcastTransaction(tx, Collections.singleton(node));
    }
}