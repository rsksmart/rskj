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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.jsonrpc.JsonRpcVersion;
import co.rsk.rpc.modules.RskJsonRpcMethod;
import co.rsk.rpc.modules.RskJsonRpcRequest;
import co.rsk.rpc.modules.RskJsonRpcRequestVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;

public class EthSubscribeRequest extends RskJsonRpcRequest {

    private final EthSubscribeParams params;

    @JsonCreator
    public EthSubscribeRequest(
            @JsonProperty("jsonrpc") JsonRpcVersion version,
            @JsonProperty("method") RskJsonRpcMethod method,
            @JsonProperty("id") Integer id,
            @JsonProperty("params") EthSubscribeParams params) {
        super(version, verifyMethod(method), Objects.requireNonNull(id));
        this.params = Objects.requireNonNull(params);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public EthSubscribeParams getParams() {
        return params;
    }

    @Override
    public JsonRpcResultOrError accept(RskJsonRpcRequestVisitor visitor, ChannelHandlerContext ctx) {
        return visitor.visit(this, ctx);
    }

    private static RskJsonRpcMethod verifyMethod(RskJsonRpcMethod method) {
        if (method != RskJsonRpcMethod.ETH_SUBSCRIBE) {
            throw new IllegalArgumentException(
                    "Wrong method mapped to eth_subscribe. Check JSON mapping configuration in JsonRpcRequest."
            );
        }

        return method;
    }
}
