package co.rsk.rpc.netty;

import co.rsk.config.TestSystemProperties;
import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.ModuleDescription;
import co.rsk.util.JacksonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import com.squareup.okhttp.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.ethereum.jsontestsuite.JSONReader;
import org.ethereum.rpc.Web3;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class Web3HttpServerTest {

    public static final String APPLICATION_JSON = "application/json";
    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void smokeTestUsingJsonContentType() throws Exception {
        smokeTest(APPLICATION_JSON);
    }

    @Test
    @Disabled("fix okhttp problem with charset/gzip")
    void smokeTestUsingJsonWithCharsetContentType() throws Exception {
        smokeTest("application/json; charset: utf-8");
    }

    @Test
    @Disabled("fix okhttp problem with charset/gzip")
    void smokeTestUsingJsonRpcWithCharsetContentType() throws Exception {
        smokeTest("application/json-rpc; charset: utf-8");
    }

    @Test
    void testMaxBatchRequest() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9000;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules, 1, new TestSystemProperties());
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String content = "[{\n" +
                "    \"method\": \"eth_getBlockByNumber\",\n" +
                "    \"params\": [\n" +
                "        \"latest\",\n" +
                "        true\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "},{\n" +
                "    \"method\": \"eth_getBlockByNumber\",\n" +
                "    \"params\": [\n" +
                "        \"latest\",\n" +
                "        true\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        Response response = sendJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        server.stop();

        assertThat(response.code(), is(HttpResponseStatus.BAD_REQUEST.code()));
        Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
        Assertions.assertEquals("Cannot dispatch batch requests. 1 is the max number of supported batch requests", jsonRpcResponse.get("error").get("message").asText());
    }

    @Test
    void testMaxBatchRequestWithNestedLevels() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9000;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules, 1, new TestSystemProperties());
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String content = "[[[{\n" +
                "    \"method\": \"eth_getBlockByNumber\",\n" +
                "    \"params\": [\n" +
                "        \"latest\",\n" +
                "        true\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "},{\n" +
                "    \"method\": \"eth_getBlockByNumber\",\n" +
                "    \"params\": [\n" +
                "        \"latest\",\n" +
                "        true\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]]]";

        Response response = sendJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        server.stop();

        assertThat(response.code(), is(HttpResponseStatus.BAD_REQUEST.code()));
        Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
        Assertions.assertEquals("Cannot dispatch batch requests. 1 is the max number of supported batch requests", jsonRpcResponse.get("error").get("message").asText());
    }

    @Test
    void testStackOverflowErrorInRequest() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "output";
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9900;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules, 1, new TestSystemProperties());
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String content = "[{\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "        \"latest\"" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        for (long i = 0; i < 99_999; i++) {
            content = String.format("[[[[[[[[[[%s]]]]]]]]]]", content);
        }

        Response response = sendJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        assertThat(response.code(), is(HttpResponseStatus.BAD_REQUEST.code()));
        Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
        Assertions.assertEquals("Invalid request", jsonRpcResponse.get("error").get("message").asText());
    }

    @Test
    void smokeTestUsingJsonRpcContentType() throws Exception {
        smokeTest("application/json-rpc");
    }

    @Test
    void smokeTestUsingInvalidContentType() {
        Assertions.assertThrows(IOException.class, () -> smokeTest("text/plain"));
    }

    @Test
    void smokeTestUsingValidHost() throws Exception {
        smokeTest(APPLICATION_JSON, "localhost");
    }

    @Test
    void smokeTestUsingInvalidHost() {
        Assertions.assertThrows(IOException.class, () -> smokeTest(APPLICATION_JSON, "evil.com"));
    }

    @Test
    void smokeTestUsingValidHostAndHostName() throws Exception {
        String domain = "www.google.com";
        List<String> rpcHost = new ArrayList<>();
        rpcHost.add(domain);
        smokeTest(APPLICATION_JSON, domain, InetAddress.getByName(domain), rpcHost);
    }

    @Test
    void smokeTestUsingWildcardHostAndHostName() throws Exception {
        String domain = "www.google.com";
        List<String> rpcHost = new ArrayList<>();
        rpcHost.add("*");
        smokeTest(APPLICATION_JSON, domain, InetAddress.getByName(domain), rpcHost);
    }

    @Test
    void smokeTestUsingInvalidHostAndHostName() throws Exception {
        InetAddress google = InetAddress.getByName("www.google.com");
        Assertions.assertThrows(IOException.class, () -> smokeTest(APPLICATION_JSON, "this is a wrong host", google, new ArrayList<>()));
    }

    @Test
    void smokeTestUsingValidHostIpAndHostName() throws Exception {
        InetAddress google = InetAddress.getByName("www.google.com");
        smokeTest(APPLICATION_JSON, "127.0.0.0", google, new ArrayList<>());
    }

    @Test
    void smokeTestProducesTimeout() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 1, new HashMap<>()));

        Function<Config, Config> decorator = rawConfig -> {
            Config config = rawConfig.withValue("rpc.timeoutunit", ConfigValueFactory.fromAnyRef(TimeUnit.MICROSECONDS.name()));
            List<? extends ConfigObject> list = config.getObjectList("rpc.modules");

            ConfigObject configElement = list.get(0);
            configElement = configElement.withValue("name", ConfigValueFactory.fromAnyRef("web3"));
            configElement = configElement.withValue("timeout", ConfigValueFactory.fromAnyRef(1));
            configElement = configElement.withValue("timeoutunit", ConfigValueFactory.fromAnyRef(TimeUnit.MICROSECONDS.name()));

            List<ConfigObject> modules = new ArrayList<>(list);
            modules.add(configElement);

            config = config.withValue("rpc.modules", ConfigValueFactory.fromAnyRef(modules));

            return config;
        };

        String mockResult = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"error\":{\"code\":-32603,\"message\":null,\"data\":\"java.util.concurrent.TimeoutException\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, decorator, mockResult);
    }

    @Test
    void smokeTestProducesTimeoutDueToMethodTimeout() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 1, new HashMap<>()));

        Function<Config, Config> decorator = rawConfig -> {
            Config config = rawConfig.withValue("rpc.timeoutunit", ConfigValueFactory.fromAnyRef(TimeUnit.MICROSECONDS.name()));
            List<? extends ConfigObject> list = config.getObjectList("rpc.modules");

            Map<String, Integer> methodTimeoutMap = new HashMap<>();
            methodTimeoutMap.put("sha3", 1);

            ConfigObject configElement = list.get(0);
            configElement = configElement.withValue("name", ConfigValueFactory.fromAnyRef("web3"));
            configElement = configElement.withValue("methodTimeout", ConfigValueFactory.fromAnyRef(methodTimeoutMap));

            List<ConfigObject> modules = new ArrayList<>(list);
            modules.add(configElement);

            config = config.withValue("rpc.modules", ConfigValueFactory.fromAnyRef(modules));

            return config;
        };

        String mockResult = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"error\":{\"code\":-32603,\"message\":null,\"data\":\"java.util.concurrent.TimeoutException\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, decorator, mockResult);
    }

    @Test
    void smokeTestProducesMethodNotFoundException() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 1, new HashMap<>()));

        String mockResult = "{\"error\":{\"code\":-32099,\"message\":\"Unexpected error\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, null, mockResult, "");
    }

    @Test
    void smokeTestProducesMethodInvalidException() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 1, new HashMap<>()));

        String mockResult = "{\"error\":{\"code\":-32099,\"message\":\"Unexpected error\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, null, mockResult, "web3sha3");
    }

    private void smokeTest(String contentType, String host) throws Exception {
        smokeTest(contentType, host, InetAddress.getLoopbackAddress(), new ArrayList<>());
    }

    private void smokeTest(String contentType, String host, List<ModuleDescription> filteredModules, Function<Config, Config> decorator, String mockResult, String method) throws Exception {
        smokeTest(contentType, host, InetAddress.getLoopbackAddress(), new ArrayList<>(), filteredModules, decorator, mockResult, method);
    }

    private void smokeTest(String contentType, String host, List<ModuleDescription> filteredModules, Function<Config, Config> decorator, String mockResult) throws Exception {
        smokeTest(contentType, host, InetAddress.getLoopbackAddress(), new ArrayList<>(), filteredModules, decorator, mockResult, "web3_sha3");
    }

    private void smokeTest(String contentType, String host, InetAddress rpcAddress, List<String> rpcHost) throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        String mockResult = "output";

        smokeTest(contentType, host, rpcAddress, rpcHost, filteredModules, null, mockResult, "web3_sha3");
    }

    private void smokeTest(String contentType, String host, InetAddress rpcAddress, List<String> rpcHost, List<ModuleDescription> filteredModules, Function<Config, Config> decorator, String mockResult, String method) throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        Mockito.when(web3Mock.web3_sha3(Mockito.anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9999;//new ServerSocket(0).getLocalPort();

        TestSystemProperties testSystemProperties = decorator == null ? new TestSystemProperties() : new TestSystemProperties(decorator);
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", rpcAddress, rpcHost);
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules, 5, testSystemProperties);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();
        try {
            Response response = sendJsonRpcMessage(randomPort, contentType, host, method);
            String responseBody = response.body().string();
            JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, responseBody);

            assertThat(response.code(), is(HttpResponseStatus.OK.code()));
            assertThat(response.header("Content-Length"), is(notNullValue()));
            assertThat(Integer.parseInt(response.header("Content-Length")), is(responseBody.getBytes().length));
            assertThat(response.header("Connection"), is("close"));

            if (mockResult.equals("output")) {
                assertThat(jsonRpcResponse.at("/result").asText(), is(mockResult));
            } else {
                Assertions.assertEquals(jsonRpcResponse.toString(), mockResult);
            }
        } finally {
            server.stop();
        }
    }

    private void smokeTest(String contentType) throws Exception {
        smokeTest(contentType, "127.0.0.1");
    }

    private Response sendJsonRpcMessage(int port, String contentType, String host) throws IOException {
        return sendJsonRpcMessage(port, contentType, host, null, "web3_sha3");
    }

    private Response sendJsonRpcMessage(int port, String contentType, String host, String content) throws IOException {
        return sendJsonRpcMessage(port, contentType, host, content, "web3_sha3");
    }

    private Response sendJsonRpcMessage(int port, String contentType, String host, String content, String method) throws IOException {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode(method));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        RequestBody requestBody = RequestBody.create(MediaType.parse(contentType),
                Optional.ofNullable(content)
                        .orElse(JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties).toString()));
        URL url = new URL("http", "localhost", port, "/");
        Request request = new Request.Builder().url(url)
                .addHeader("Host", host)
                .addHeader("Accept-Encoding", "identity")
                .post(requestBody).build();
        return getUnsafeOkHttpClient().newCall(request).execute();
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            return new OkHttpClient()
                    .setSslSocketFactory(sslSocketFactory)
                    .setHostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
