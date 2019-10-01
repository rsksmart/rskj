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

import co.rsk.jsonrpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Tries to decode a request before it reaches jsonrpc4j.
 * When decoded, it's passed onto the next handler. Otherwise it falls back to jsonrpc4j.
 */
public class JsonRpcRequestDecoder extends MessageToMessageDecoder<ByteBufHolder> {
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestDecoder.class);

    private final JsonRpcSerializer<RskJsonRpcRequestParams> serializer;

    public JsonRpcRequestDecoder(JsonRpcSerializer<RskJsonRpcRequestParams> serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBufHolder msg, List<Object> out) {
        try {
            try (ByteBufInputStream source = new ByteBufInputStream(msg.copy().content())) {
                out.add(serializer.deserializeRequest(source));
            }
        } catch (IOException e) {
            logger.trace("Unable to deserialize request, forwarding to legacy jsonrpc4j implementation", e);
            ctx.fireChannelRead(msg.retain());
        }
    }
}
