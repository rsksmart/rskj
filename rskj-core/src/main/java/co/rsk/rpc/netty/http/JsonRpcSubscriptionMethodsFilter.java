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
package co.rsk.rpc.netty.http;

import co.rsk.jsonrpc.JsonRpcRequest;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Prevents executing subscription methods (such as eth_subscribe and eth_unsubscribe) on the HTTP pipeline
 */
public class JsonRpcSubscriptionMethodsFilter
        extends SimpleChannelInboundHandler<JsonRpcRequest<RskJsonRpcRequestParams>> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, JsonRpcRequest<RskJsonRpcRequestParams> request) {
        if (request.getMethod().equals("eth_subscribe") || request.getMethod().equals("eth_unsubscribe")) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireChannelRead(request);
        }
    }
}
