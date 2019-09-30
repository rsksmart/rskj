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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.netty.JsonRpcRequestTypedHandler;
import io.netty.channel.ChannelHandlerContext;

public class EthSubscribe
        implements JsonRpcRequestTypedHandler<EthSubscribeParams>, EthSubscribeParams.Visitor {
    private final EthSubscriptionNotificationEmitter emitter;

    public EthSubscribe(EthSubscriptionNotificationEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public JsonRpcResultOrError handle(ChannelHandlerContext ctx, EthSubscribeParams params) {
        return params.resolve(ctx, this);
    }

    @Override
    public Class<EthSubscribeParams> paramsClass() {
        return EthSubscribeParams.class;
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeNewHeadsParams params) {
        return emitter.subscribeNewHeads(ctx.channel());
    }

    @Override
    public JsonRpcResultOrError respond(ChannelHandlerContext ctx, EthSubscribeLogsParams params) {
        return emitter.subscribeLogs(params, ctx.channel());
    }
}
