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

import java.util.Objects;

/**
 * This the JSON-RPC error response DTO for JSON serialization purposes.
 */
public class JsonRpcErrorResponse extends JsonRpcIdentifiableMessage {
    private final JsonRpcError error;

    public JsonRpcErrorResponse(int id, JsonRpcError error) {
        super(JsonRpcVersion.V2_0, id);
        this.error = Objects.requireNonNull(error);
    }

    @SuppressWarnings("unused")
    public JsonRpcError getError() {
        return error;
    }
}
