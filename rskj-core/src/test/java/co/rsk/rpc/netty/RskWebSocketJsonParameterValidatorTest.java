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

import java.io.IOException;

import co.rsk.util.JacksonParserUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


class RskWebSocketJsonParameterValidatorTest {

	private ObjectMapper mapper = new ObjectMapper();

	private RskWebSocketJsonParameterValidator validator = new RskWebSocketJsonParameterValidator();

	@Test
	void testParameterValidator_expectValid() throws IOException {

		mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

		// right cases
		JsonNode request1 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':'teste','method':'eth_subscribe','params':['newHeads']}");
		JsonNode request2 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':'10','method':'eth_subscribe','params':['newHeads']}");
		JsonNode request3 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':10,'method':'eth_subscribe','params':['newHeads']}");

		Assertions.assertTrue(validator.validate(request1).isValid());
		Assertions.assertTrue(validator.validate(request2).isValid());
		Assertions.assertTrue(validator.validate(request3).isValid());

	}

	@Test
	public void testParameterValidator_expectNotValid() throws IOException {

		mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

		// wrong cases
		JsonNode request4 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':3.3,'method':'eth_subscribe','params':['newHeads']}");
		JsonNode request5 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':{},'method':'eth_subscribe','params':['newHeads']}");
		JsonNode request6 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':false,'method':'eth_subscribe','params':['newHeads']}");
		JsonNode request7 = JacksonParserUtil.readTree(mapper, "{'jsonrpc':'2.0','id':null,'method':'eth_subscribe','params':['newHeads']}");

		Assertions.assertFalse(validator.validate(request4).isValid());
		Assertions.assertFalse(validator.validate(request5).isValid());
		Assertions.assertFalse(validator.validate(request6).isValid());
		Assertions.assertFalse(validator.validate(request7).isValid());

	}

}
