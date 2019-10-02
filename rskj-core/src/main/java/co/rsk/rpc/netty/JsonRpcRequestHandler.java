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

import co.rsk.jsonrpc.*;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import co.rsk.rpc.modules.Web3Api;
import co.rsk.rpc.modules.eth.EthBlockNumberParams;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeLogsParams;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeNewHeadsParams;
import co.rsk.rpc.modules.eth.subscribe.EthUnsubscribeParams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.io.IOException;

/**
 * This handler decodes inbound messages and dispatches valid JSON-RPC requests.
 *
 * Note that we split JSON-RPC handling in two because jsonrpc4j wasn't able to handle the PUB-SUB model.
 * Eventually, we might want to implement all methods in this style and remove jsonrpc4j.
 *
 * We make this object Sharable so it can be instanced once in the netty pipeline
 * and since all objects used by this object are thread safe, 
 */

@Sharable
public class JsonRpcRequestHandler
        extends SimpleChannelInboundHandler<JsonRpcRequest<RskJsonRpcRequestParams>>
        implements Web3Api {
    private final EthModule ethModule;
    private final EthSubscriptionNotificationEmitter emitter;
    private final JsonRpcSerializer<RskJsonRpcRequestParams> serializer;

    public JsonRpcRequestHandler(
            EthModule ethModule,
            EthSubscriptionNotificationEmitter emitter,
            JsonRpcSerializer<RskJsonRpcRequestParams> serializer) {
        this.ethModule = ethModule;
        this.emitter = emitter;
        this.serializer = serializer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, JsonRpcRequest<RskJsonRpcRequestParams> request)
            throws IOException {
        JsonRpcResultOrError resultOrError = request.getParams().resolve(ctx, this);
        JsonRpcIdentifiableMessage response = resultOrError.responseFor(request.getId());
        int responseCode = resultOrError.httpStatusCode();

        ByteBuf responseContent = Unpooled.buffer();
        try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent)) {
            serializer.serializeMessage(os, response);
        }

        ctx.fireChannelRead(new Web3Result(responseContent, responseCode));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        emitter.unsubscribe(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthBlockNumberParams params) {
        long blockNumber = ethModule.blockNumber();
        return new JsonRpcQuantityResult(blockNumber);
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthUnsubscribeParams params) {
        boolean unsubscribed = emitter.unsubscribe(params.getSubscriptionId());
        return new JsonRpcBooleanResult(unsubscribed);
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeLogsParams params) {
        return emitter.subscribe(params, ctx.channel());
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeNewHeadsParams params) {
        return emitter.subscribe(params, ctx.channel());
    }
}
