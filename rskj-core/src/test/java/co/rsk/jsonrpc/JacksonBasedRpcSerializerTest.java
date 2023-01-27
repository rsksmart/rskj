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

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

class JacksonBasedRpcSerializerTest {

	private JacksonBasedRpcSerializer serializer;

	@BeforeEach
	public void init() {

		serializer = new JacksonBasedRpcSerializer();

	}

    // This is a negative test to ensure polymorphic types are not enabled in an insecure manner
    // Jackson unsafe deserialization can lead to remote code execution
    //
    // For details, see:
    // - https://cowtowncoder.medium.com/jackson-2-10-safe-default-typing-2d018f0ce2ba
    // - https://adamcaudill.com/2017/10/04/exploiting-jackson-rce-cve-2017-7525/
    @Test
    void testJackson_deserialization_Unsafe() throws IOException {
        String injectedObject = "{\"method\":\"eth_subscribe\",\"@class\":\"co.rsk.rpc.ArbitraryObject\"}";
        String injectedObjectMessage = "Cannot construct instance of `co.rsk.rpc.modules.eth.subscribe.EthSubscribeRequest`";

        ValueInstantiationException e = Assertions
                .assertThrows(ValueInstantiationException.class, () -> convertJson(injectedObject));

        String exceptionMessage = e.getMessage();
        Assertions.assertTrue(exceptionMessage.contains(injectedObjectMessage));
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
