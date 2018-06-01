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
 * The standard JSON-RPC error object for responses.
 */
public class JsonRpcError implements JsonRpcResultOrError {
    private final int code;
    private final String message;

    public JsonRpcError(int code, String message) {
        this.code = code;
        this.message = Objects.requireNonNull(message);
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public JsonRpcIdentifiableMessage responseFor(int messageId) {
        return new JsonRpcErrorResponse(messageId, this);
    }
}
