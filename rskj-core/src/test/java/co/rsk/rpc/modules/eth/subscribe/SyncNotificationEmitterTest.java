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

import co.rsk.net.SyncProcessor;
import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncNotificationEmitterTest {
    private SyncNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @BeforeEach
    void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        Blockchain blockchain = mock(Blockchain.class);
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(2L);
        when(blockchain.getBestBlock()).thenReturn(block);
        SyncProcessor syncProcessor = mock(SyncProcessor.class);
        when(syncProcessor.getInitialBlockNumber()).thenReturn(2L);
        when(syncProcessor.getHighestBlockNumber()).thenReturn(100L);
        emitter = new SyncNotificationEmitter(ethereum, serializer, blockchain, syncProcessor);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    void onSyncStartedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1");

        listener.onLongSyncStarted();

        verify(channel).writeAndFlush(new TextWebSocketFrame("serialized1"));
    }

    @Test
    void onSyncStartedTriggersMessageToChannelMultipleSubscribers() throws JsonProcessingException {
        SubscriptionId subscriptionId1 = mock(SubscriptionId.class);
        Channel channel1 = mock(Channel.class);
        SubscriptionId subscriptionId2 = mock(SubscriptionId.class);
        Channel channel2 = mock(Channel.class);
        emitter.subscribe(subscriptionId1, channel1);
        emitter.subscribe(subscriptionId2, channel2);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1")
                .thenReturn("serialized1");

        listener.onLongSyncStarted();

        verify(channel1).writeAndFlush(new TextWebSocketFrame("serialized1"));
        verify(channel2).writeAndFlush(new TextWebSocketFrame("serialized1"));
    }

    @Test
    void onSyncEndedTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized1");

        listener.onLongSyncDone();

        verify(channel).writeAndFlush(new TextWebSocketFrame("serialized1"));
    }

    @Test
    void unsubscribeSucceedsForExistingSubscriptionId() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        MatcherAssert.assertThat(emitter.unsubscribe(new SubscriptionId()), is(false));
        MatcherAssert.assertThat(emitter.unsubscribe(subscriptionId), is(true));
    }

    @Test
    void unsubscribeChannelThenNothingIsEmitted() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        emitter.subscribe(subscriptionId, channel);

        emitter.unsubscribe(channel);

        listener.onLongSyncStarted();
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

        listener.onLongSyncDone();

        verifyNoMoreInteractions(channel);
    }

    @Test
    void validateNotificationSyncingStarted() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        EthSubscriptionNotification<?> ethSubscriptionNotification = emitter.getNotification(true, subscriptionId);

        assertTrue(ethSubscriptionNotification.getParams().getResult() instanceof SyncNotification);
        SyncNotification syncNotification = (SyncNotification) ethSubscriptionNotification.getParams().getResult();
        assertEquals(2L, syncNotification.getStatus().getStartingBlock());
        assertEquals(2L, syncNotification.getStatus().getCurrentBlock());
        assertEquals(100L, syncNotification.getStatus().getHighestBlock());
    }

    @Test
    void validateNotificationSyncingEnded() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        EthSubscriptionNotification<?> ethSubscriptionNotification = emitter.getNotification(false, subscriptionId);

        assertTrue(ethSubscriptionNotification.getParams().getResult() instanceof Boolean);
        assertFalse((Boolean)ethSubscriptionNotification.getParams().getResult());
    }
}
