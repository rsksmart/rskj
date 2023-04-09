package co.rsk.rpc.netty;

import co.rsk.config.TestSystemProperties;
import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import co.rsk.rpc.exception.JsonRpcTimeoutError;
import co.rsk.util.JacksonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.squareup.okhttp.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.ethereum.rpc.Web3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9000;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
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

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
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
        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9000;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
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

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
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
        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9900;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
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

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", content);
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
            List<? extends ConfigObject> list = rawConfig.getObjectList("rpc.modules");

            ConfigObject configElement = list.get(0);
            configElement = configElement.withValue("name", ConfigValueFactory.fromAnyRef("web3"));
            configElement = configElement.withValue("timeout", ConfigValueFactory.fromAnyRef(1));

            List<ConfigObject> modules = new ArrayList<>(list);
            modules.add(configElement);

            return rawConfig.withValue("rpc.modules", ConfigValueFactory.fromAnyRef(modules));
        };

        String mockResult = "{\"error\":{\"code\":-32603,\"message\":\"Execution has expired.\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, decorator, mockResult);
    }

    @Test
    void smokeTestProducesTimeoutDueToMethodTimeout() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 1, new HashMap<>()));

        Function<Config, Config> decorator = rawConfig -> {
            List<? extends ConfigObject> list = rawConfig.getObjectList("rpc.modules");

            Map<String, Long> methodTimeoutMap = new HashMap<>();
            methodTimeoutMap.put("sha3", 1L);
            Map<String, Map<String, Long>> timeoutMap = new HashMap<>();
            timeoutMap.put("timeout", methodTimeoutMap);

            ConfigObject configElement = list.get(0);
            configElement = configElement.withValue("name", ConfigValueFactory.fromAnyRef("web3"));
            configElement = configElement.withValue("methods", ConfigValueFactory.fromAnyRef(timeoutMap));

            List<ConfigObject> modules = new ArrayList<>(list);
            modules.add(configElement);

            return rawConfig.withValue("rpc.modules", ConfigValueFactory.fromAnyRef(modules))
                    .withValue("rpc.timeout",  ConfigValueFactory.fromAnyRef(10_000_000_000L));
        };

        String mockResult = "{\"error\":{\"code\":-32603,\"message\":\"Execution has expired.\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, decorator, mockResult);
    }

    @Test
    void smokeTestProducesMethodNotFoundException() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));

        String mockResult = "{\"jsonrpc\":\"2.0\",\"id\":13,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, null, mockResult, "");
    }

    @Test
    void smokeTestProducesMethodInvalidException() throws Exception {
        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));

        String mockResult = "{\"jsonrpc\":\"2.0\",\"id\":13,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}";
        smokeTest(APPLICATION_JSON, "localhost", filteredModules, null, mockResult, "web3sha3");
    }

    @Test
    void testMaxResponseSize() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = getMediumJsonResponse();
        int responseSize = mockResult.getBytes().length * 2 - 1;


        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9110;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .rpcModules(filteredModules)
                .rpcMaxResponseSize(responseSize)
                .build();

        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String requestContent = "[\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  },\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  }\n" +
                "]";

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", requestContent);
        server.stop();


        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        Assertions.assertEquals(JsonRpcResponseLimitError.ERROR_CODE, jsonRpcResponse.get("error").get("code").asInt());
        verify(web3Mock, times(2)).web3_sha3(anyString());
    }

    @Test
    void testMaxResponseSize_stopBatch() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = getMediumJsonResponse();
        int responseSize = mockResult.getBytes().length - 1;


        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9110;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .rpcModules(filteredModules)
                .rpcMaxResponseSize(responseSize)
                .build();

        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String requestContent = "[\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  },\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  }\n" +
                "]";

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", requestContent);
        server.stop();


        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        Assertions.assertEquals(JsonRpcResponseLimitError.ERROR_CODE, jsonRpcResponse.get("error").get("code").asInt());
        verify(web3Mock, times(1)).web3_sha3(anyString());
    }

    @Test
    void testResponseTimeoutException() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "response output";
        final long expectedTimeoutPerRequest = 500;
        Mockito.when(web3Mock.web3_sha3(anyString())).thenAnswer(invocation -> {
            Thread.sleep(expectedTimeoutPerRequest);
            return mockResult;
        });
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9111;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .rpcModules(filteredModules)
                .rpcTimeout(600)
                .build();

        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String requestContent = "[\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  },\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  }\n" +
                "]";

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", requestContent);
        server.stop();


        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        Assertions.assertEquals(JsonRpcTimeoutError.ERROR_CODE, jsonRpcResponse.get("error").get("code").asInt());
        verify(web3Mock, times(2)).web3_sha3(anyString());
    }

    @Test
    void testResponseTimeoutException_stopsBatch() throws Exception {
        Web3 web3Mock = Mockito.mock(Web3.class);
        String mockResult = "response output";
        final long expectedTimeoutPerRequest = 500;
        Mockito.when(web3Mock.web3_sha3(anyString())).thenAnswer(invocation -> {
            Thread.sleep(expectedTimeoutPerRequest);
            return mockResult;
        });

        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9111;

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", InetAddress.getLoopbackAddress(), new ArrayList<>());
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .rpcModules(filteredModules)
                .rpcTimeout(expectedTimeoutPerRequest / 2)
                .build();

        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        Web3HttpServer server = new Web3HttpServer(InetAddress.getLoopbackAddress(), randomPort, 0, Boolean.TRUE, mockCorsConfiguration, filterHandler, serverHandler, 52428800);
        server.start();

        String requestContent = "[\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  },\n" +
                "  {\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"web3_sha3\",\n" +
                "    \"params\": [\n" +
                "      \"0x68656c6c6f20776f726c64\"\n" +
                "    ],\n" +
                "    \"id\": 64\n" +
                "  }\n" +
                "]";

        Response response = sendHugeJsonRpcMessage(randomPort, "application/json-rpc", "127.0.0.1", requestContent);
        server.stop();


        String responseBody = response.body().string();
        JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(responseBody);

        Assertions.assertEquals(JsonRpcTimeoutError.ERROR_CODE, jsonRpcResponse.get("error").get("code").asInt());
        verify(web3Mock, times(1)).web3_sha3(anyString());
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
        Mockito.when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);
        CorsConfiguration mockCorsConfiguration = Mockito.mock(CorsConfiguration.class);
        Mockito.when(mockCorsConfiguration.hasHeader()).thenReturn(true);
        Mockito.when(mockCorsConfiguration.getHeader()).thenReturn("*");

        int randomPort = 9999;//new ServerSocket(0).getLocalPort();

        TestSystemProperties testSystemProperties = decorator == null ? new TestSystemProperties() : new TestSystemProperties(decorator);
        JsonRpcWeb3FilterHandler filterHandler = new JsonRpcWeb3FilterHandler("*", rpcAddress, rpcHost);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(5).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
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
            } else if (!jsonRpcResponse.at("/result").asText().isEmpty()) {
                Assertions.assertEquals(jsonRpcResponse.at("/result").asText(), mockResult);
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

    private Response sendHugeJsonRpcMessage(int port, String contentType, String host, String content) throws IOException {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
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

    private Response sendJsonRpcMessage(int port, String contentType, String host, String method) throws IOException {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode(method));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        RequestBody requestBody = RequestBody.create(MediaType.parse(contentType), JSON_NODE_FACTORY.objectNode()
                .setAll(jsonRpcRequestProperties).toString());
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

    private String getMediumJsonResponse() {
        return " {\n" +
                "        \"jsonrpc\": \"2.0\",\n" +
                "        \"id\": 1,\n" +
                "        \"result\": {\n" +
                "            \"number\": \"0x3\",\n" +
                "            \"hash\": \"0x2fd76d5e649d0afd216aba87fa919e8850b1badbb34531b69e10ee00496ae7ca\",\n" +
                "            \"parentHash\": \"0x5a5ff4628a01ccc79909eaea7004bc90620eefd1374f61c5c41928f780ad47df\",\n" +
                "            \"sha3Uncles\": \"0xdd198e401a42bdb666d9c6c7307da23d739fe992ce68fea44e0a38ad0f7097e3\",\n" +
                "            \"logsBloom\": \"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\n" +
                "            \"transactionsRoot\": \"0x142bd02ce0eb3ced3a228dac11e9dccc6943d800e1ce0f80953b0d67b2a398f6\",\n" +
                "            \"stateRoot\": \"0xcb694600670b9ea518c8b0d2c7ea391727663e98729e1badcd8a064b4814e982\",\n" +
                "            \"receiptsRoot\": \"0x66cfdb731f620cd96e2c2cb0f7d3c3a2879c29b40014aa27efbbf3cf9cd3b0f6\",\n" +
                "            \"miner\": \"0x1fab9a0e24ffc209b01faa5a61ad4366982d0b7f\",\n" +
                "            \"difficulty\": \"0x20a3d\",\n" +
                "            \"totalDifficulty\": \"0x2a0a3d\",\n" +
                "            \"extraData\": \"0x\",\n" +
                "            \"size\": \"0x27e4\",\n" +
                "            \"gasLimit\": \"0x4c8485\",\n" +
                "            \"gasUsed\": \"0x0\",\n" +
                "            \"timestamp\": \"0x5d1f551e\",\n" +
                "            \"transactions\": [\n" +
                "                {\n" +
                "                    \"hash\": \"0x694c5110568eeddab989eb9601cca00ab94dfccf3597f72e1627ca987d05691b\",\n" +
                "                    \"nonce\": \"0x2\",\n" +
                "                    \"blockHash\": \"0x2fd76d5e649d0afd216aba87fa919e8850b1badbb34531b69e10ee00496ae7ca\",\n" +
                "                    \"blockNumber\": \"0x3\",\n" +
                "                    \"transactionIndex\": \"0x0\",\n" +
                "                    \"from\": \"0x0000000000000000000000000000000000000000\",\n" +
                "                    \"to\": \"0x0000000000000000000000000000000001000008\",\n" +
                "                    \"gas\": \"0x0\",\n" +
                "                    \"gasPrice\": \"0x0\",\n" +
                "                    \"value\": \"0x0\",\n" +
                "                    \"input\": \"0x\",\n" +
                "                    \"v\": \"0x0\",\n" +
                "                    \"r\": \"0x0\",\n" +
                "                    \"s\": \"0x0\",\n" +
                "                    \"type\": \"0x0\"\n" +
                "                }\n" +
                "            ],\n" +
                "            \"uncles\": [\n" +
                "                \"0xda4b2d7e79a9f19881bdd61304fdab8ba3443c329b06ffab1460ebdd0950c736\",\n" +
                "                \"0x5bb7eac6fe88a32f5853c1368b6a018fea312d99c49decf2b3d6a940966c75ab\",\n" +
                "                \"0x08f15c6097d98d47d6f2e73d5fed5d42ecdf2d2f16760622bc189d604965fc02\",\n" +
                "                \"0xe09fe2113298257469513d7ddd2b21b7af589bab51b9f965d464418d03232949\",\n" +
                "                \"0x6c8d022cac09b0ff9914fcf3030c50fa6c38599ef97653d513ca5a76b981b54a\",\n" +
                "                \"0x35a5b19b778919f281cea24a23f303b44d3490479a02649960ce3cd1af86feca\",\n" +
                "                \"0x2639eb6de08773bd90bebbff9ce0de736cf4f805c2547a85682a4ed3c8bfd292\",\n" +
                "                \"0x5257ae559f38ea63f481bf9fcb006d07e647a4d525935089c64d2a8d95a45e6a\",\n" +
                "                \"0xc0b1aa843ee1e4b23f4c11382c3fbe5f0882b0768242efd20e16782e2ce9d25a\",\n" +
                "                \"0xc8f8288c35b8c9efbe65306b7dd0eca37e5fbbbdff98387c914d13f32f030375\"\n" +
                "            ],\n" +
                "            \"minimumGasPrice\": \"0x0\",\n" +
                "            \"bitcoinMergedMiningHeader\": \"0x000000204b830f1affa0f22955f4cba89f2b7856a2dfdb65c9a248145a020000000000002f6954241717b75ac7bef04fcff9dffdcd2f10c35a22fa9bda7772a10162cb5029551f5d531d041ac1787d31\",\n" +
                "            \"bitcoinMergedMiningCoinbaseTransaction\": \"0x00000000000000801649d8b2989d0ce6109fbcdc38d4593275662b4d541fd08ce5f0740bd78736696088ac0000000000000000266a24aa21a9ed029d7e041decec4a9946a4ce1c067f74dc92709e90854432c9494a6428307f3700000000000000002a6a52534b424c4f434b3aa29b6de0f39c04c200d138e07758f2604783de7c1e0003abb60b3d28544bc1b600000000\",\n" +
                "            \"bitcoinMergedMiningMerkleProof\": \"0x6829b0d13e9af3d9d763dcda91d55adbe513f453863ee268c0617d4bc4b1b4e2fb1ef1250e9a5abd35daded453f13ba08290c2eec04237f9586ab1918300b7482e9857a3839c94225bdec2ed3a6e49c8d407734cf70720fc8e79830ff2974688f895cee2a9687c1fb96f66eed9717454cd343e85483a502849d2e873c22afb2d665efa40b90f1518e8be178e3c0a58b44e78c4c400d6789fce043eb142d590171e2096ee4485d5a3035a887040432a09074cd1f700369da15f65520fdd3a093c602abc4746e0f936b3e01191bc30dee02aa4448d74689c7e03fdae2413f165fdca9727f5741c864bb138760dbd5a1033d007a766161b3e851e602e2d61ce7858\",\n" +
                "            \"hashForMergedMining\": \"0xa29b6de0f39c04c200d138e07758f2604783de7c1e0003abb60b3d28544bc1b6\",\n" +
                "            \"paidFees\": \"0x0\",\n" +
                "            \"cumulativeDifficulty\": \"0x160a3d\"\n" +
                "        }\n" +
                "    }";
    }
}