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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

public class Web3HttpMethodFilterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpMethod httpMethod = request.getMethod();
        if (HttpMethod.POST.equals(httpMethod)) {
            // retain the request so it isn't released automatically by SimpleChannelInboundHandler
            ctx.fireChannelRead(request.retain());
        } else {
            HttpResponse response =
                    new DefaultHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
