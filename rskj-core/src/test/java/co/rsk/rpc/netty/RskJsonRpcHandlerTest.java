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
package co.rsk.rpc.netty;

import co.rsk.jsonrpc.JsonRpcBooleanResult;
import co.rsk.jsonrpc.JsonRpcVersion;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcMethod;
import co.rsk.rpc.modules.eth.subscribe.*;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class RskJsonRpcHandlerTest {
    private static final SubscriptionId SAMPLE_SUBSCRIPTION_ID = new SubscriptionId("0x3075");
    private static final EthSubscribeRequest SAMPLE_SUBSCRIBE_REQUEST = new EthSubscribeRequest(
            JsonRpcVersion.V2_0,
            RskJsonRpcMethod.ETH_SUBSCRIBE,
            35,
            new EthSubscribeParams(EthSubscribeTypes.NEW_HEADS)

    );

    private RskJsonRpcHandler handler;
    private EthSubscriptionNotificationEmitter emitter;
    private JsonRpcSerializer serializer;

    @Before
    public void setUp() {
        emitter = mock(EthSubscriptionNotificationEmitter.class);
        serializer = mock(JsonRpcSerializer.class);
        handler = new RskJsonRpcHandler(emitter, serializer);
    }

    @Test
    public void visitUnsubscribe() {
        EthUnsubscribeRequest unsubscribe = new EthUnsubscribeRequest(
                JsonRpcVersion.V2_0,
                RskJsonRpcMethod.ETH_UNSUBSCRIBE,
                35,
                new EthUnsubscribeParams(SAMPLE_SUBSCRIPTION_ID)

        );

        when(emitter.unsubscribe(SAMPLE_SUBSCRIPTION_ID))
            .thenReturn(true);

        assertThat(
                handler.visit(unsubscribe, null),
                is(new JsonRpcBooleanResult(true))
        );
    }

    @Test
    public void visitSubscribe() {
        Channel channel = mock(Channel.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel())
                .thenReturn(channel);
        when(emitter.subscribe(channel))
            .thenReturn(SAMPLE_SUBSCRIPTION_ID);

        assertThat(
                handler.visit(SAMPLE_SUBSCRIBE_REQUEST, ctx),
                is(SAMPLE_SUBSCRIPTION_ID)
        );
    }

    @Test
    public void handlerDeserializesAndHandlesRequest() throws Exception {
        Channel channel = mock(Channel.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel())
                .thenReturn(channel);

        when(serializer.deserializeRequest(any()))
                .thenReturn(SAMPLE_SUBSCRIBE_REQUEST);
        when(emitter.subscribe(channel))
                .thenReturn(SAMPLE_SUBSCRIPTION_ID);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized");

        DefaultByteBufHolder msg = new DefaultByteBufHolder(Unpooled.copiedBuffer("raw".getBytes()));
        handler.channelRead(ctx, msg);

        verify(ctx, times(1)).writeAndFlush(new TextWebSocketFrame("serialized"));
        verify(ctx, never()).fireChannelRead(any());
    }

    @Test
    public void handlerPassesRequestToNextHandlerOnException() throws Exception {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        when(serializer.deserializeRequest(any()))
                .thenThrow(new IOException());

        DefaultByteBufHolder msg = new DefaultByteBufHolder(Unpooled.copiedBuffer("raw".getBytes()));
        handler.channelRead(ctx, msg);

        verify(ctx, never()).writeAndFlush(any());
        verify(ctx, times(1)).fireChannelRead(msg);
    }

    // TODO unsubscribe
}