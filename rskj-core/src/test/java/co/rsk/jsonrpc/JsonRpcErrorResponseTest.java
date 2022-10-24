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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonRpcErrorResponseTest {

	private ObjectMapper serializer = new ObjectMapper();

	@Test
	void serializeResponseWithError() throws IOException {

		String message = "{\"jsonrpc\":\"2.0\",\"id\":\"48\",\"error\":{\"code\":-32603,\"message\":\"Internal error.\"}}";

		assertThat(serializer.writeValueAsString(new JsonRpcErrorResponse("48", new JsonRpcInternalError())), is(message));
	}
}
