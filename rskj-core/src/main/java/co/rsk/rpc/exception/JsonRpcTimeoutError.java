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

package co.rsk.rpc.exception;

import co.rsk.jsonrpc.JsonRpcError;

public class JsonRpcTimeoutError extends JsonRpcThrowableError {
    private static final long serialVersionUID = 950001095761882084L;

    public JsonRpcTimeoutError(String  msg) {
        super(msg);
    }

    @Override
    public JsonRpcError getErrorResponse() {
        return new JsonRpcError(JsonRpcError.RESPONSE_LIMIT_ERROR, getMessage());
    }

}
