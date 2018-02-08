package co.rsk.rpc.netty;

import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.netty.JsonRpcWeb3FilterHandler;
import co.rsk.rpc.netty.JsonRpcWeb3ServerHandler;
import co.rsk.rpc.netty.Web3HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.ethereum.rpc.Web3;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import sun.net.www.protocol.http.HttpURLConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class Web3HttpServerTest {

    public static final String APPLICATION_JSON = "application/json";
    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Set<String> restrictedHeaderSetBackup;

    @BeforeClass
    public static void setup() throws Exception {
        Set<String> original = getRestrictedHeaderSet();
        restrictedHeaderSetBackup = new HashSet<>(original);
        original.clear();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Set<String> modified = getRestrictedHeaderSet();
        modified.addAll(restrictedHeaderSetBackup);
    }

    private static Set<String> getRestrictedHeaderSet() throws Exception {
        return (Set<String>) Whitebox.getAllStaticFields(HttpURLConnection.class)
                .stream()
                .filter(f -> f.getName().equals("restrictedHeaderSet"))
                .findFirst()
                .get()
                .get(HttpURLConnection.class);
    }

    @Test
    public void smokeTestUsingJsonContentType() throws Exception {
        smokeTest(APPLICATION_JSON);
    }

    @Test
    public void smokeTestUsingJsonWithCharsetContentType() throws Exception {
        smokeTest("application/json; charset: utf-8");
    }

    @Test
    public void smokeTestUsingJsonRpcWithCharsetContentType() throws Exception {
        smokeTest("application/json-rpc; charset: utf-8");
    }

    @Test
    public void smokeTestUsingJsonRpcContentType() throws Exception {
        smokeTest("application/json-rpc");
    }

    @Test(expected=IOException.class)
    public void smokeTestUsingInvalidContentType() throws Exception {
        smokeTest("text/plain");
    }

    @Test
    public void smokeTestUsingValidHost() throws Exception {
        smokeTest(APPLICATION_JSON, "localhost");
    }

    @Test(expected = IOException.class)
    public void smokeTestUsingInvalidHost() throws Exception {
        smokeTest(APPLICATION_JSON, "evil.com");
    }

    @Test
    public void smokeTestUsingValidHostAndHostName() throws Exception {
        String domain = "www.google.com";
        List<String> rpcHost = new ArrayList<>();
        rpcHost.add(domain);
        smokeTest(APPLICATION_JSON, domain, InetAddress.getByName(domain), rpcHost);
    }

    @Test(expected = IOException.class)
    public void smokeTestUsingInvalidHostAndHostName() throws Exception {
        InetAddress google = InetAddress.getByName("www.google.com");
        smokeTest(APPLICATION_JSON, google.getHostAddress(), google, new ArrayList<>());
    }


    private void smokeTest(String contentType, String host) throws Exception {
        smokeTest(contentType, host, InetAddress.getLoopbackAddress(), new ArrayList<>());
    }

    private void smokeTest(String contentType, String host, InetAddress rpcAddress, List<String> rpcHost) throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9999;//new ServerSocket(0).getLocalPort();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", rpcAddress, rpcHost);
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler);
        server.start();
        HttpURLConnection conn = null;
        try {
            conn = sendJsonRpcMessage(randomPort, contentType, host);
            JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(conn.getInputStream());

            assertThat(conn.getResponseCode(), is(HttpResponseStatus.OK.code()));
            assertThat(jsonRpcResponse.at("/result").asText(), is(mockResult));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            server.stop();
        }
    }

    private void smokeTest(String contentType) throws Exception {
        smokeTest(contentType, "127.0.0.1");
    }

    private HttpURLConnection sendJsonRpcMessage(int port, String contentType, String host) throws IOException {
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
        jsonRpcConnection.setRequestProperty("Host", host);
        OutputStream os = jsonRpcConnection.getOutputStream();
        os.write(request);
        return jsonRpcConnection;
    }
}
