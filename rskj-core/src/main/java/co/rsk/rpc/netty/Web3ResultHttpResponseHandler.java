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

import com.googlecode.jsonrpc4j.HttpStatusCodeProvider;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;

public class Web3ResultHttpResponseHandler extends SimpleChannelInboundHandler<Web3Result> {

    private final HttpStatusCodeProvider httpStatusCodeProvider;

    public Web3ResultHttpResponseHandler(HttpStatusCodeProvider httpStatusCodeProvider) {
        this.httpStatusCodeProvider = httpStatusCodeProvider;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Web3Result msg) {
        ByteBuf content = msg.getContent();

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(httpStatusCodeProvider.getHttpStatusCode(msg.getCode())),
                content
        );

        response.headers().add(CONTENT_TYPE, APPLICATION_JSON);
        response.headers().add(CONTENT_LENGTH, content.readableBytes());
        response.headers().add(CONNECTION, CLOSE);

        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
