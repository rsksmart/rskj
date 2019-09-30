/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.jsonrpc.JsonRpcEmptyResult;
import co.rsk.jsonrpc.JsonRpcSerializer;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcMethodFilter;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Optional;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class JsonRpcRequestHandlerTest {
    private JsonRpcRequestHandler handler;
    private JsonRpcMethodFilter methodFilter;
    private JsonRpcRequestHandlerManager handlers;

    @Before
    public void setUp() {
        EthSubscriptionNotificationEmitter emitter = mock(EthSubscriptionNotificationEmitter.class);
        JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();
        methodFilter = mock(JsonRpcMethodFilter.class);
        handlers = mock(JsonRpcRequestHandlerManager.class);
        handler = new JsonRpcRequestHandler(emitter, serializer, methodFilter, handlers, "eth_unsubscribe");
    }

    @Test
    public void handlesRequest() throws IOException {
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        JsonRpcRequestRawHandler handler = handler();
        channel.writeInbound(knownMethod(handler));

        verify(handler).handle(any(), any());

        Object handlerResponse = channel.readInbound();
        assertThat(handlerResponse, instanceOf(Web3Result.class));
    }

    @Test
    public void passesOnRequestForUnhandledMethod() {
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        ByteBufHolder message = unhandledMethod();
        channel.writeInbound(message);

        Object handlerResponse = channel.readInbound();
        assertThat(handlerResponse, is(message));
    }

    @Test
    public void rejectsUnknownMethod() {
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(unknownMethod());

        Object handlerResponse = channel.readInbound();
        assertThat(handlerResponse, instanceOf(Web3Result.class));
    }

    @Test
    public void rejectsDisabledMethod() throws IOException {
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        JsonRpcRequestRawHandler handler = handler();
        channel.writeInbound(disabledMethod(handler));

        verify(handler, never()).handle(any(), any());

        Object handlerResponse = channel.readInbound();
        assertThat(handlerResponse, instanceOf(Web3Result.class));
    }

    private JsonRpcRequestRawHandler handler() throws IOException {
        JsonRpcRequestRawHandler handler = mock(JsonRpcRequestRawHandler.class);
        when(handler.handle(any(), any())).thenReturn(new JsonRpcEmptyResult());
        return handler;
    }

    private ByteBufHolder knownMethod(JsonRpcRequestRawHandler handler) {
        when(methodFilter.checkMethod("eth_subscribe")).thenReturn(true);
        when(handlers.find("eth_subscribe")).thenReturn(Optional.of(handler));
        return buffer("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"eth_subscribe\",\"params\":[\"newHeads\"]}");
    }

    private ByteBufHolder disabledMethod(JsonRpcRequestRawHandler handler) {
        when(methodFilter.checkMethod("eth_unsubscribe")).thenReturn(true);
        when(handlers.find("eth_subscribe")).thenReturn(Optional.of(handler));
        return buffer("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"eth_unsubscribe\",\"params\":[\"0x0204\"]}");
    }

    private ByteBufHolder unhandledMethod() {
        when(methodFilter.checkMethod("unhandled_message")).thenReturn(true);
        when(handlers.find("eth_subscribe")).thenReturn(Optional.empty());
        return buffer("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"unhandled_message\",\"params\":[]}");
    }

    private ByteBufHolder unknownMethod() {
        when(methodFilter.checkMethod("unknown_message")).thenReturn(false);
        return buffer("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"unknown_message\",\"params\":[]}");
    }

    private ByteBufHolder buffer(String json) {
        ByteBuf buf = ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(json), Charset.defaultCharset());
        return new DefaultByteBufHolder(buf);
    }
}