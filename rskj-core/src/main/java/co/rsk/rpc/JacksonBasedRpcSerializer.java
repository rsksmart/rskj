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
package co.rsk.rpc;

import co.rsk.jsonrpc.JsonRpcMessage;
import co.rsk.jsonrpc.JsonRpcRequest;
import co.rsk.jsonrpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeParams;
import co.rsk.rpc.modules.eth.subscribe.EthUnsubscribeParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This implements basic JSON-RPC serialization using Jackson.
 */
public class JacksonBasedRpcSerializer implements JsonRpcSerializer<RskJsonRpcRequestParams> {
    //From https://fasterxml.github.io/jackson-databind/javadoc/2.5/com/fasterxml/jackson/databind/ObjectMapper.html
    // ObjectMapper is thread-safe as long as the config methods are not called after the serialiation begins.
    private final ObjectMapper mapper;

    public JacksonBasedRpcSerializer(NamedType... extraTypes) {
        mapper = new ObjectMapper();
        mapper.registerSubtypes(
                new NamedType(EthSubscribeParams.class, "eth_subscribe"),
                new NamedType(EthUnsubscribeParams.class, "eth_unsubscribe")
        );
        mapper.registerSubtypes(extraTypes);
    }

    @Override
    public String serializeMessage(JsonRpcMessage message) throws JsonProcessingException {
        return mapper.writeValueAsString(message);
    }

    @Override
    public void serializeMessage(OutputStream os, JsonRpcMessage message) throws IOException {
        mapper.writeValue(os, message);
    }

    @Override
    public JsonRpcRequest<RskJsonRpcRequestParams> deserializeRequest(InputStream source) throws IOException {
        return mapper.readValue(source, JsonRpcRequest.class);
    }
}
