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
import co.rsk.rpc.modules.RskJsonRpcRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * This implements basic JSON-RPC serialization using Jackson.
 */
public class JacksonBasedRpcSerializer implements JsonRpcSerializer {
    //From https://fasterxml.github.io/jackson-databind/javadoc/2.5/com/fasterxml/jackson/databind/ObjectMapper.html
    // ObjectMapper is thread-safe as long as the config methods are not called after the serialiation begins.
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String serializeMessage(JsonRpcMessage message) throws JsonProcessingException {
        return mapper.writeValueAsString(message);
    }

    @Override
    public RskJsonRpcRequest deserializeRequest(InputStream source) throws IOException {
        return mapper.readValue(source, RskJsonRpcRequest.class);
    }
}
