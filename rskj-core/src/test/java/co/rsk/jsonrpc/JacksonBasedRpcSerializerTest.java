package co.rsk.jsonrpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcRequest;


public class JacksonBasedRpcSerializerTest {
	
	private JacksonBasedRpcSerializer serializer;
	
	@Before
	public void init() {
		
		serializer = new JacksonBasedRpcSerializer();
		
	}
	
	@Test
	public void testIntegerId_then_convertSuccessfully() throws IOException {

		String messageInt = "{\"jsonrpc\": \"2.0\",\"method\": \"eth_subscribe\",\"params\": [\"newHeads\"],\"id\": 64}";
		RskJsonRpcRequest requestFromIntId = convertJson(messageInt); 
		
		Assert.assertEquals("64", requestFromIntId.getId());
	}

	@Test
	public void testStringId_then_convertSuccessfully() throws IOException {

		String messageStr = "{\"jsonrpc\": \"2.0\",\"method\": \"eth_subscribe\",\"params\": [\"newHeads\"],\"id\": \"string\"}";
		RskJsonRpcRequest requestFromStringId = convertJson(messageStr); 

		Assert.assertEquals("string", requestFromStringId.getId());
		
	}

	private RskJsonRpcRequest convertJson(String message) throws IOException {
		
		RskJsonRpcRequest request = null;
		
        try (InputStream source = new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))){
            request = serializer.deserializeRequest(source);
        }
        
		return request;
	}
	
}
