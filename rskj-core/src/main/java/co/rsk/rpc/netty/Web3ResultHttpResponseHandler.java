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

import com.googlecode.jsonrpc4j.DefaultHttpStatusCodeProvider;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.CUSTOM_SERVER_ERROR_LOWER;
import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.CUSTOM_SERVER_ERROR_UPPER;

public class Web3ResultHttpResponseHandler extends SimpleChannelInboundHandler<Web3Result> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Web3Result msg) {
        HttpResponseStatus responseStatus;
        int web3ResultCode = msg.getCode();
        if (CUSTOM_SERVER_ERROR_UPPER >= web3ResultCode && web3ResultCode >= CUSTOM_SERVER_ERROR_LOWER) {
            responseStatus = HttpResponseStatus.OK;
        } else {
            responseStatus = HttpResponseStatus.valueOf(DefaultHttpStatusCodeProvider.INSTANCE.getHttpStatusCode(web3ResultCode));
        }

        ctx.write(new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            responseStatus,
            msg.getContent()
        )).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
