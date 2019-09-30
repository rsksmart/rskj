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
package co.rsk.rpc.modules;

import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeLogsParams;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeNewHeadsParams;
import co.rsk.rpc.modules.eth.subscribe.EthUnsubscribeParams;
import io.netty.channel.ChannelHandlerContext;

/**
 * Classes implementing this interface know how to handle Web3 requests on a specific Netty channel.
 * In the end, this interface will declare each and every method in the Web3 API, and its implementor(s) will need to
 * manually dispatch all of them.
 * This boilerplate is the price to pay to have a strongly-typed API with no reflection.
 */
public interface Web3Api {
    JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeNewHeadsParams params);

    JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeLogsParams params);

    JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthUnsubscribeParams params);
}
