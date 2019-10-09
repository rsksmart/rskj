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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The request id DTO for JSON serialization purposes.
 * Supports positive long values and strings according to the specification.
 */
public class JsonRpcRequestId extends JsonRpcResult {
    private final Object id;

    @JsonCreator
    public JsonRpcRequestId(long id) {
        this.id = requireNonNegative(id);
    }

    @JsonCreator
    public JsonRpcRequestId(String id) {
        this.id = id;
    }

    @JsonValue
    @SuppressWarnings("unused")
    private Object serialize() {
        return id;
    }

    private static long requireNonNegative(long id) {
        if (id < 0) {
            throw new IllegalArgumentException(
                    String.format("JSON-RPC request id should be a positive number, but was %d.", id)
            );
        }

        return id;
    }
}
