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
package co.rsk.rpc.netty;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class RskWebSocketJsonParameterValidator {

	private static final String ID_BAD_PARAMETER_MSG = "JSON-RPC message id should be a String or a positive integer, but was '%s'.";

	public static final String ID = "id";

	public Result validate(final JsonNode node) {
		return requireValidId(node.get(ID));
	}

	/**
	 * id must be non null and of the type String or posisitive integer/long
	 * No other type is accepted nor empty string
	 */
	private Result requireValidId(JsonNode id) {

		Result result = new Result(true, null);

		boolean notNull = id != null;

		boolean validAsString = notNull && id.isTextual() && StringUtils.isNotEmpty(id.asText());

		boolean validAsInt = notNull && id.isIntegralNumber() && id.asLong() >= 0; // int OR long

		if (!validAsString && !validAsInt) {
			result.setValid(false);
			result.setMessage(String.format(ID_BAD_PARAMETER_MSG, id));
		}

		return result;
	}

	public static class Result {

		private boolean valid;
		private String message;

		public Result(boolean valid, String message) {
			this.valid = valid;
			this.message = message;
		}

		public boolean isValid() {
			return valid;
		}

		public void setValid(boolean valid) {
			this.valid = valid;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}
