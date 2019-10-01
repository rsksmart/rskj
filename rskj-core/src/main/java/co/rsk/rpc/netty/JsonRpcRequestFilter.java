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
import co.rsk.rpc.JsonRpcMethodFilter;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Filters methods based on configuration
 */
@Sharable
public class JsonRpcRequestFilter
        extends SimpleChannelInboundHandler<JsonRpcRequest<RskJsonRpcRequestParams>> {
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestFilter.class);

    private final JsonRpcSerializer<RskJsonRpcRequestParams> serializer;
    private final JsonRpcMethodFilter methodFilter;

    public JsonRpcRequestFilter(
            JsonRpcSerializer<RskJsonRpcRequestParams> serializer,
            JsonRpcMethodFilter methodFilter) {
        this.serializer = serializer;
        this.methodFilter = methodFilter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, JsonRpcRequest<RskJsonRpcRequestParams> request)
            throws IOException {
        if (methodFilter.checkMethod(request.getMethod())) {
            ctx.fireChannelRead(request);
        } else {
            logger.trace("Method {} is not enabled", request.getMethod());

            JsonRpcError error = JsonRpcErrors.methodNotEnabled();
            JsonRpcIdentifiableMessage response = error.responseFor(request.getId());
            int responseCode = error.httpStatusCode();

            ByteBuf responseContent = Unpooled.buffer();
            try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent)) {
                serializer.serializeMessage(os, response);
            }

            ctx.fireChannelRead(new Web3Result(responseContent, responseCode));
        }
    }
}
