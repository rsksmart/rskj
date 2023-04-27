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

	// Error codes as defined in https://www.jsonrpc.org/specification#error_object
	public static final int METHOD_NOT_FOUND = -32601;
	public static final int INVALID_PARAMS = -32602;
	public static final int INTERNAL_ERROR = -32603;
	public static final int RESPONSE_LIMIT_ERROR = -32011;


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
	public JsonRpcIdentifiableMessage responseFor(Object messageId) {
		return new JsonRpcErrorResponse(messageId, this);
	}
}
