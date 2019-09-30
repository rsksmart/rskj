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
package co.rsk.jsonrpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Basic JSON-RPC serialization methods.
 */
public interface JsonRpcSerializer {
    /**
     * @param os the destination of the JsonRpcMessage serialized into a JSON string as bytes
     * @throws IOException when serialization fails
     */
    void serializeMessage(OutputStream os, JsonRpcMessage message) throws IOException;

    /**
     * @return a request deserialized from a JSON string in the source stream
     * @throws IOException when deserialization fails
     */
    JsonRpcRequest deserializeRequest(InputStream source) throws IOException;

    /**
     * @return request parameters deserialized from JsonRpcRequest content
     * @throws IOException when deserialization fails
     */
    <T extends JsonRpcRequestParams> T deserializeRequestParams(JsonRpcRequest request, Class<T> tClass)
            throws IOException;
}
