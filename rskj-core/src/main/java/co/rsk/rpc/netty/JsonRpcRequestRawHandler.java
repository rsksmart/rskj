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
import co.rsk.jsonrpc.JsonRpcResultOrError;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;

/**
 * Low level interface for RPC method handlers.
 * This helps glue typed handlers and decouple them from the Jackson framework.
 * Users should implement the higher-level @{@link JsonRpcRequestTypedHandler} interface.
 */
public interface JsonRpcRequestRawHandler {
    JsonRpcResultOrError handle(ChannelHandlerContext ctx, JsonRpcRequest request) throws IOException;
}
