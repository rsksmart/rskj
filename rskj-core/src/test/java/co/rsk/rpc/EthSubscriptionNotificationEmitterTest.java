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
package co.rsk.rpc;

import co.rsk.rpc.modules.eth.subscribe.*;
import io.netty.channel.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

class EthSubscriptionNotificationEmitterTest {
    private BlockHeaderNotificationEmitter newHeads;
    private LogsNotificationEmitter logs;
    private PendingTransactionsNotificationEmitter pendingTransactions;
    private SyncNotificationEmitter sync;
    private EthSubscriptionNotificationEmitter emitter;

    @BeforeEach
    void setUp() {
        newHeads = mock(BlockHeaderNotificationEmitter.class);
        logs = mock(LogsNotificationEmitter.class);
        pendingTransactions = mock(PendingTransactionsNotificationEmitter.class);
        sync = mock(SyncNotificationEmitter.class);
        emitter = new EthSubscriptionNotificationEmitter(newHeads, logs, pendingTransactions, sync);
    }

    @Test
    void subscribeToNewHeads() {
        Channel channel = mock(Channel.class);
        EthSubscribeNewHeadsParams params = mock(EthSubscribeNewHeadsParams.class);

        SubscriptionId subscriptionId = emitter.visit(params, channel);

        assertThat(subscriptionId, notNullValue());
        verify(newHeads).subscribe(subscriptionId, channel);
        verify(pendingTransactions, never()).subscribe(any(), any());
        verify(logs, never()).subscribe(any(), any(), any());
        verify(sync, never()).subscribe(any(), any());
    }

    @Test
    void subscribeToLogs() {
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);

        SubscriptionId subscriptionId = emitter.visit(params, channel);

        assertThat(subscriptionId, notNullValue());
        verify(logs).subscribe(subscriptionId, channel, params);
        verify(pendingTransactions, never()).subscribe(any(), any());
        verify(sync, never()).subscribe(any(), any());
        verify(newHeads, never()).subscribe(any(), any());
    }

    @Test
    void subscribeToPendingTransactions() {
        Channel channel = mock(Channel.class);
        EthSubscribePendingTransactionsParams params = mock(EthSubscribePendingTransactionsParams.class);

        SubscriptionId subscriptionId = emitter.visit(params, channel);

        assertThat(subscriptionId, notNullValue());
        verify(pendingTransactions).subscribe(subscriptionId, channel);
        verify(sync, never()).subscribe(any(), any());
        verify(logs, never()).subscribe(any(), any(), any());
        verify(newHeads, never()).subscribe(any(), any());
    }

    @Test
    void subscribeToSync() {
        Channel channel = mock(Channel.class);
        EthSubscribeSyncParams params = mock(EthSubscribeSyncParams.class);

        SubscriptionId subscriptionId = emitter.visit(params, channel);

        assertThat(subscriptionId, notNullValue());
        verify(sync).subscribe(subscriptionId, channel);
        verify(pendingTransactions, never()).subscribe(any(), any());
        verify(logs, never()).subscribe(any(), any(), any());
        verify(newHeads, never()).subscribe(any(), any());
    }

    @Test
    void unsubscribeUnsuccessfully() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);

        boolean unsubscribed = emitter.unsubscribe(subscriptionId);

        assertThat(unsubscribed, is(false));
        verify(newHeads).unsubscribe(subscriptionId);
        verify(logs).unsubscribe(subscriptionId);
        verify(pendingTransactions).unsubscribe(subscriptionId);
        verify(sync).unsubscribe(subscriptionId);
    }

    @Test
    void unsubscribeSuccessfullyFromNewHeads() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        when(newHeads.unsubscribe(subscriptionId)).thenReturn(true);

        boolean unsubscribed = emitter.unsubscribe(subscriptionId);

        assertThat(unsubscribed, is(true));
        verify(newHeads).unsubscribe(subscriptionId);
        verify(logs).unsubscribe(subscriptionId);
        verify(pendingTransactions).unsubscribe(subscriptionId);
        verify(sync).unsubscribe(subscriptionId);
    }

    @Test
    void unsubscribeSuccessfullyFromLogs() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        when(logs.unsubscribe(subscriptionId)).thenReturn(true);

        boolean unsubscribed = emitter.unsubscribe(subscriptionId);

        assertThat(unsubscribed, is(true));
        verify(newHeads).unsubscribe(subscriptionId);
        verify(logs).unsubscribe(subscriptionId);
        verify(pendingTransactions).unsubscribe(subscriptionId);
        verify(sync).unsubscribe(subscriptionId);
    }

    @Test
    void unsubscribeSuccessfullyFromPendingTransactions() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        when(pendingTransactions.unsubscribe(subscriptionId)).thenReturn(true);

        boolean unsubscribed = emitter.unsubscribe(subscriptionId);

        assertThat(unsubscribed, is(true));
        verify(newHeads).unsubscribe(subscriptionId);
        verify(logs).unsubscribe(subscriptionId);
        verify(pendingTransactions).unsubscribe(subscriptionId);
        verify(sync).unsubscribe(subscriptionId);
    }

    @Test
    void unsubscribeSuccessfullyFromSync() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        when(sync.unsubscribe(subscriptionId)).thenReturn(true);

        boolean unsubscribed = emitter.unsubscribe(subscriptionId);

        assertThat(unsubscribed, is(true));
        verify(newHeads).unsubscribe(subscriptionId);
        verify(logs).unsubscribe(subscriptionId);
        verify(pendingTransactions).unsubscribe(subscriptionId);
        verify(sync).unsubscribe(subscriptionId);
    }

    @Test
    void unsubscribeChannel() {
        Channel channel = mock(Channel.class);

        emitter.unsubscribe(channel);

        verify(newHeads).unsubscribe(channel);
        verify(logs).unsubscribe(channel);
        verify(pendingTransactions).unsubscribe(channel);
        verify(sync).unsubscribe(channel);
    }
}
