package co.rsk.jsonrpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcRequest;

class JacksonBasedRpcSerializerTest {

	private JacksonBasedRpcSerializer serializer;

	@BeforeEach
	public void init() {

		serializer = new JacksonBasedRpcSerializer();

	}

	@Test
	void testIntegerId_then_convertSuccessfully() throws IOException {

		String messageInt = "{\"jsonrpc\": \"2.0\",\"method\": \"eth_subscribe\",\"params\": [\"newHeads\"],\"id\": 64}";
		RskJsonRpcRequest requestFromIntId = convertJson(messageInt);

		Assertions.assertEquals(new Integer(64), requestFromIntId.getId());
	}

	@Test
	void testStringId_then_convertSuccessfully() throws IOException {

		String messageStr = "{\"jsonrpc\": \"2.0\",\"method\": \"eth_subscribe\",\"params\": [\"newHeads\"],\"id\": \"string\"}";
		RskJsonRpcRequest requestFromStringId = convertJson(messageStr);

		Assertions.assertEquals("string", requestFromStringId.getId());

	}

	private RskJsonRpcRequest convertJson(String message) throws IOException {

		RskJsonRpcRequest request = null;

		try (InputStream source = new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))) {
			request = serializer.deserializeRequest(source);
		}

		return request;
	}

}
