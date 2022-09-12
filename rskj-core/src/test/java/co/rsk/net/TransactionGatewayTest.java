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
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.net.server.ChannelManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class TransactionGatewayTest {
    private ChannelManager channelManager;
    private TransactionPool transactionPool;
    private TransactionGateway gateway;
    private Transaction tx;

    @BeforeEach
    void setUp() {
        this.channelManager = mock(ChannelManager.class);
        this.transactionPool = mock(TransactionPool.class);
        this.tx = mock(Transaction.class);
        when(this.tx.getHash()).thenReturn(TestUtils.randomHash());

        this.gateway = new TransactionGateway(channelManager, transactionPool);
    }

    @Test
    void receiveTranasctionsFrom_newTransactions_shouldAddAndBroadcast() {
        List<Transaction> transactions = Collections.singletonList(tx);
        List<Transaction> transactionPoolAddResult = transactions;

        receiveTransactionsFromAndVerifyCalls(
                transactions,
                Collections.emptySet(),
                transactionPoolAddResult,
                1,
                1
        );
    }

    @Test
    void receiveTransactionsFrom_transactionsAlreadyAdded_shouldntAddAndShouldntBroadcast() {
        List<Transaction> transactions = Collections.singletonList(tx);
        List<Transaction> transactionPoolAddResult = Collections.emptyList();

        receiveTransactionsFromAndVerifyCalls(
                transactions,
                Collections.emptySet(),
                transactionPoolAddResult,
                1,
                0
        );
    }

    @Test
    void receiveTransaction_newTransaction_shouldAddAndBroadcast() {
        TransactionPoolAddResult transactionPoolAddResult = TransactionPoolAddResult
                .okPendingTransactions(Collections.singletonList(tx));

        receiveTransactionAndVerifyCalls(transactionPoolAddResult, 1);
    }

    @Test
    void receiveTransaction_alreadyAddedTransaction_shouldntAddAndShouldntBroadcast() {
        TransactionPoolAddResult transactionPoolAddResult = TransactionPoolAddResult.withError("Not added");
        receiveTransactionAndVerifyCalls(transactionPoolAddResult, 0);
    }

    private void receiveTransactionAndVerifyCalls(TransactionPoolAddResult transactionPoolAddResult,
                                                  int broadcastTransactionsCount) {
        when(transactionPool.addTransaction(tx)).thenReturn(transactionPoolAddResult);

        this.gateway.receiveTransaction(tx);

        verify(transactionPool, times(1)).addTransaction(tx);
        verify(channelManager, times(broadcastTransactionsCount)).
                broadcastTransactions(transactionPoolAddResult.getPendingTransactionsAdded(), Collections.emptySet());
    }

    private void receiveTransactionsFromAndVerifyCalls(List<Transaction> txs,
                                                       Set<NodeID> nodeIDS,
                                                       List<Transaction> transactionPoolAddResult,
                                                       int addTransactionsInvocationsCount,
                                                       int broadcastTransactionsInvocationsCount) {
        when(transactionPool.addTransactions(txs)).thenReturn(transactionPoolAddResult);

        this.gateway.receiveTransactionsFrom(txs, nodeIDS);

        verify(transactionPool, times(addTransactionsInvocationsCount)).addTransactions(txs);
        verify(channelManager, times(broadcastTransactionsInvocationsCount)).broadcastTransactions(txs, nodeIDS);
    }
}
