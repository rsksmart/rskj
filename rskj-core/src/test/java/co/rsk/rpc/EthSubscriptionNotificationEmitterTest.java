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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.rpc.modules.eth.subscribe.SubscriptionId;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Block;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class EthSubscriptionNotificationEmitterTest {
    private static final Block TEST_BLOCK = new BlockGenerator().createBlock(12, 0);

    private EthSubscriptionNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        emitter = new EthSubscriptionNotificationEmitter(ethereum, serializer);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum, times(1)).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void subscribeReturnsSubscriptionId() {
        Channel channel = mock(Channel.class);

        assertThat(emitter.subscribe(channel), notNullValue());
    }

    @Test
    public void ethereumOnBlockEventTriggersMessageToChannel() throws JsonProcessingException {
        Channel channel = mock(Channel.class);
        emitter.subscribe(channel);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized");

        listener.onBlock(TEST_BLOCK, null);
        verify(channel, times(1)).writeAndFlush(new TextWebSocketFrame("serialized"));
    }

    @Test
    public void unsubscribeSucceedsForExistingSubscriptionId() {
        Channel channel = mock(Channel.class);
        SubscriptionId subscriptionId = emitter.subscribe(channel);

        assertThat(emitter.unsubscribe(new SubscriptionId()), is(false));
        assertThat(emitter.unsubscribe(subscriptionId), is(true));
    }
}