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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

/**
 * This is the base class for JSON-RPC requests.
 * Inheritors should define the methods it accepts and how to map to different specific implementations.
 */
public class JsonRpcRequest<T extends JsonRpcRequestParams> extends JsonRpcIdentifiableMessage {
    private final String method;
    private final T params;

    public JsonRpcRequest(
            @JsonProperty("jsonrpc") JsonRpcVersion version,
            @JsonProperty("id") Integer id,
            @JsonProperty("method") String method,
            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "method", visible = true)
            @JsonProperty("params") T params) {
        super(version, id);
        this.method = Objects.requireNonNull(method);
        this.params = Objects.requireNonNull(params);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getMethod() {
        return method;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public T getParams() {
        return params;
    }
}
