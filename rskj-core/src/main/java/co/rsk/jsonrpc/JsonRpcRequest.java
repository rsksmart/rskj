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

import java.util.Objects;

/**
 * This is the base class for JSON-RPC requests.
 * Inheritors should define the methods it accepts and how to map to different specific implementations.
 */
public abstract class JsonRpcRequest<T extends Enum<T>> extends JsonRpcIdentifiableMessage {
    private final T method;

    public JsonRpcRequest(
            JsonRpcVersion version,
            T method,
            int id) {
        super(version, id);
        this.method = Objects.requireNonNull(method);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public T getMethod() {
        return method;
    }
}
