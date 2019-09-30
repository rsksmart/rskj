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

import co.rsk.jsonrpc.*;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.JsonRpcMethodFilter;
import io.netty.buffer.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * This handler decodes inbound messages and dispatches valid JSON-RPC requests.
 *
 * A better implementation would decouple decoding and filtering logic into different netty handlers but we can't do
 * that while we support jsonrpc4j as a fallback.
 */
public class JsonRpcRequestHandler extends SimpleChannelInboundHandler<ByteBufHolder> {
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestHandler.class);

    private final EthSubscriptionNotificationEmitter emitter;
    private final JsonRpcSerializer serializer;
    private final JsonRpcMethodFilter methodFilter;
    private final JsonRpcRequestHandlerManager handlers;
    private final Set<String> disabledMethods;

    public JsonRpcRequestHandler(
            EthSubscriptionNotificationEmitter emitter,
            JsonRpcSerializer serializer,
            JsonRpcMethodFilter methodFilter,
            JsonRpcRequestHandlerManager handlers,
            String... disabledMethods) {
        this.emitter = emitter;
        this.serializer = serializer;
        this.methodFilter = methodFilter;
        this.handlers = handlers;
        // this is here to make it easy for the HTTP pipeline to disable subscription methods
        this.disabledMethods = new HashSet<>();
        Collections.addAll(this.disabledMethods, disabledMethods);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBufHolder msg) {
        JsonRpcRequest request;
        try (ByteBufInputStream source = new ByteBufInputStream(msg.copy().content())) {
            request = serializer.deserializeRequest(source);
        } catch (IOException e) {
            logger.trace("Unable to deserialize basic JSON-RPC request information", e);
            ctx.close();
            return;
        }

        if (!methodFilter.checkMethod(request.getMethod()) || disabledMethods.contains(request.getMethod())) {
            logger.trace("Method {} is not enabled", request.getMethod());
            respond(ctx, request, JsonRpcErrors.methodNotEnabled());
            return;
        }

        Optional<JsonRpcRequestRawHandler> rawHandler = handlers.find(request.getMethod());
        if (!rawHandler.isPresent()) {
            logger.trace("Method is enabled and handler not found, forwarding to legacy jsonrpc4j implementation");
            ctx.fireChannelRead(msg.retain());
            return;
        }

        JsonRpcResultOrError resultOrError;
        try {
            resultOrError = rawHandler.get().handle(ctx, request);
        } catch (IOException e) {
            logger.trace("Unable to deserialize request parameters", e);
            respond(ctx, request, JsonRpcErrors.invalidParams());
            return;
        }

        respond(ctx, request, resultOrError);
    }

    private void respond(ChannelHandlerContext ctx, JsonRpcRequest request, JsonRpcResultOrError resultOrError) {
        JsonRpcIdentifiableMessage response = resultOrError.responseFor(request.getId());
        int responseCode = resultOrError.httpStatusCode();

        ByteBuf responseContent = Unpooled.buffer();
        try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent)) {
            serializer.serializeMessage(os, response);
        } catch (IOException e) {
            logger.error("Unable to serialize Web3 response", e);
            ctx.close();
            return;
        }

        ctx.fireChannelRead(new Web3Result(responseContent, responseCode));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        emitter.unsubscribe(ctx.channel());
        super.channelInactive(ctx);
    }

    public interface Factory {
        JsonRpcRequestHandler newInstance(String... disabledMethods);
    }
}
