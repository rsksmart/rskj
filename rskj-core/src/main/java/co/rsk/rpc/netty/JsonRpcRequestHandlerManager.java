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

import co.rsk.jsonrpc.JsonRpcRequest;
import co.rsk.jsonrpc.JsonRpcRequestParams;
import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.jsonrpc.JsonRpcSerializer;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribe;
import co.rsk.rpc.modules.eth.subscribe.EthUnsubscribe;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JsonRpcRequestHandlerManager {
    private final EthSubscriptionNotificationEmitter emitter;
    private final JsonRpcSerializer serializer;
    private final Map<String, JsonRpcRequestRawHandler> handlers;

    public JsonRpcRequestHandlerManager(EthSubscriptionNotificationEmitter emitter, JsonRpcSerializer serializer) {
        this.emitter = emitter;
        this.serializer = serializer;
        this.handlers = new HashMap<>();
        addDefaultHandlers();
    }

    public Optional<JsonRpcRequestRawHandler> find(String method) {
        return Optional.ofNullable(handlers.get(method));
    }

    private void addDefaultHandlers() {
        addOrReplace("eth_subscribe", new EthSubscribe(emitter));
        addOrReplace("eth_unsubscribe", new EthUnsubscribe(emitter));
    }

    public <T extends JsonRpcRequestParams> void addOrReplace(String method, JsonRpcRequestTypedHandler<T> handler) {
        handlers.put(method, new RawHandlerImpl<>(serializer, handler));
    }

    public static class RawHandlerImpl<T extends JsonRpcRequestParams> implements JsonRpcRequestRawHandler {
        private final JsonRpcSerializer serializer;
        private final JsonRpcRequestTypedHandler<T> handler;

        public RawHandlerImpl(JsonRpcSerializer serializer, JsonRpcRequestTypedHandler<T> handler) {
            this.serializer = serializer;
            this.handler = handler;
        }

        @Override
        public JsonRpcResultOrError handle(ChannelHandlerContext ctx, JsonRpcRequest request) throws IOException {
            T params = serializer.deserializeRequestParams(request, handler.paramsClass());
            return handler.handle(ctx, params);
        }
    }
}
