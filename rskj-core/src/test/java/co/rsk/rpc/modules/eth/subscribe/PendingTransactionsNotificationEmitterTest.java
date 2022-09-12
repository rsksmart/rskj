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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.net.utils.TransactionUtils;
import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

class PendingTransactionsNotificationEmitterTest {
    private PendingTransactionsNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @BeforeEach
    void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        emitter = new PendingTransactionsNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    void onPendingTransactionsReceivedEventTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1")
                .thenReturn("serialized2");

        listener.onPendingTransactionsReceived(TransactionUtils.getTransactions(2));

        verify(channel).write(new TextWebSocketFrame("serialized1"));
        verify(channel).write(new TextWebSocketFrame("serialized2"));
        verify(channel).flush();
    }

    @Test
    void sendMultipleTransactionsToMultipleSubscriptions() throws JsonProcessingException {
        SubscriptionId subscriptionId1 = mock(SubscriptionId.class);
        Channel channel1 = mock(Channel.class);
        SubscriptionId subscriptionId2 = mock(SubscriptionId.class);
        Channel channel2 = mock(Channel.class);
        emitter.subscribe(subscriptionId1, channel1);
        emitter.subscribe(subscriptionId2, channel2);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1")
                .thenReturn("serialized2")
                .thenReturn("serialized1")
                .thenReturn("serialized2");

        listener.onPendingTransactionsReceived(TransactionUtils.getTransactions(2));

        verify(channel1).write(new TextWebSocketFrame("serialized1"));
        verify(channel1).write(new TextWebSocketFrame("serialized2"));
        verify(channel1).flush();
        verify(channel2).write(new TextWebSocketFrame("serialized1"));
        verify(channel2).write(new TextWebSocketFrame("serialized2"));
        verify(channel2).flush();
    }

    @Test
    void notificationContainsHashedTransaction() {
        SubscriptionId subscriptionId =  new SubscriptionId(("0x7392"));
        Transaction transaction = TransactionUtils.createTransaction();
        EthSubscriptionNotification<String> notification = emitter.getNotification(subscriptionId, transaction);

        assertEquals(transaction.getHash().toJsonString(), notification.getParams().getResult());
        assertEquals(subscriptionId, notification.getParams().getSubscription());
    }

    @Test
    void unsubscribeSucceedsForExistingSubscriptionId() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        assertThat(emitter.unsubscribe(new SubscriptionId()), is(false));
        assertThat(emitter.unsubscribe(subscriptionId), is(true));
    }

    @Test
    void unsubscribeChannelThenNothingIsEmitted() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        emitter.unsubscribe(channel);

        listener.onPendingTransactionsReceived(TransactionUtils.getTransactions(2));
        verifyNoMoreInteractions(channel);
    }

    @Test
    void serializationFailsMessageNotSent() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        JsonProcessingException mockException = mock(JsonProcessingException.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenThrow(mockException);

        listener.onPendingTransactionsReceived(TransactionUtils.getTransactions(2));

        verify(channel, times(0)).write(any());
        verify(channel, times(1)).flush();
    }
}
