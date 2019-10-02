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
package co.rsk.rpc.modules.eth;

import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import co.rsk.rpc.modules.Web3Api;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.netty.channel.ChannelHandlerContext;

@JsonFormat(shape=JsonFormat.Shape.ARRAY)
public class EthBlockNumberParams implements RskJsonRpcRequestParams {
    @Override
    public JsonRpcResultOrError resolve(ChannelHandlerContext ctx, Web3Api api) {
        return api.respond(ctx, this);
    }
}
