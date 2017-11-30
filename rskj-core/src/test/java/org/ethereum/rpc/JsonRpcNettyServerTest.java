package org.ethereum.rpc;

import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.ModuleDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonRpcNettyServerTest {

    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void smokeTestUsingJsonContentType() throws Exception {
        smokeTest("application/json");
    }

    @Test
    public void smokeTestUsingJsonRpcContentType() throws Exception {
        smokeTest("application/json-rpc");
    }

    @Test(expected=IOException.class)
    public void smokeTestUsingInvalidContentType() throws Exception {
        smokeTest("text/plain");
    }

    public void smokeTest(String contentType) throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9999;//new ServerSocket(0).getLocalPort();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*");
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules);
        JsonRpcNettyServer server = new JsonRpcNettyServer(randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler);
        server.start();

        HttpURLConnection conn = sendJsonRpcMessage(randomPort, contentType);
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(conn.getInputStream());

        assertThat(conn.getResponseCode(), is(HttpResponseStatus.OK.code()));
        assertThat(jsonRpcResponse.at("/result").asText(), is(mockResult));
        server.stop();
    }

    private HttpURLConnection sendJsonRpcMessage(int port, String contentType) throws IOException {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        byte[] request = OBJECT_MAPPER.writeValueAsBytes(OBJECT_MAPPER.treeToValue(
                JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties), Object.class));
        URL jsonRpcServer = new URL("http","localhost", port, "/");
        HttpURLConnection jsonRpcConnection = (HttpURLConnection) jsonRpcServer.openConnection();
        jsonRpcConnection.setDoOutput(true);
        jsonRpcConnection.setRequestMethod("POST");
        jsonRpcConnection.setRequestProperty("Content-Type", contentType);
        jsonRpcConnection.setRequestProperty("Content-Length", String.valueOf(request.length));
        OutputStream os = jsonRpcConnection.getOutputStream();
        os.write(request);
        return jsonRpcConnection;
    }
}
