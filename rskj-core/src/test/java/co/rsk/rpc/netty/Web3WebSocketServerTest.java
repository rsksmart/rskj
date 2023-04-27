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

import co.rsk.config.TestSystemProperties;
import co.rsk.jsonrpc.JsonRpcError;
import co.rsk.rpc.ModuleDescription;
import co.rsk.util.JacksonParserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.squareup.okhttp.*;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import okio.Buffer;
import org.ethereum.rpc.Web3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Web3WebSocketServerTest {

    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_WRITE_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MAX_FRAME_SIZE = 65536; // 64 kb
    private static final int DEFAULT_MAX_AGGREGATED_FRAME_SIZE = 5242880; // 5 mb

    private ExecutorService wsExecutor;

    @BeforeEach
    void setup() {
        wsExecutor = Executors.newSingleThreadExecutor();
    }

    @Test
    void smokeTest() throws Exception {
        smokeTest(getJsonRpcDummyMessage("value"), 9991);
    }

    @Test
    void smokeTestWithBigJson() throws Exception {
        smokeTest(getJsonRpcBigMessage(), 9992);
    }

    @Test
    void smokeTestWithBigJsonUsingAnotherServerPath() throws Exception {
        smokeTest(getJsonRpcBigMessage(), "/", 9993);
    }

    @AfterEach
    void tearDown() {
        wsExecutor.shutdown();
    }

    @Test
    void testMaxBatchRequest() throws Exception {
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

        byte[] msg = content.getBytes();
        String serverPath = "/";

        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = 9995;

        TestSystemProperties testSystemProperties = new TestSystemProperties();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        RskWebSocketJsonRpcHandler handler = new RskWebSocketJsonRpcHandler(null);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .maxBatchRequestsSize(1)
                .rpcModules(filteredModules)
                .rpcMaxResponseSize(testSystemProperties.getRpcMaxResponseSize())
                .rpcTimeout(testSystemProperties.getRpcTimeout())
                .build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        int serverWriteTimeoutSeconds = testSystemProperties.rpcWebSocketServerWriteTimeoutSeconds();
        int maxFrameSize = testSystemProperties.rpcWebSocketMaxFrameSize();
        int maxAggregatedFrameSize = testSystemProperties.rpcWebSocketMaxAggregatedFrameSize();

        assertEquals(DEFAULT_WRITE_TIMEOUT_SECONDS, serverWriteTimeoutSeconds);
        assertEquals(DEFAULT_MAX_FRAME_SIZE, maxFrameSize);
        assertEquals(DEFAULT_MAX_AGGREGATED_FRAME_SIZE, maxAggregatedFrameSize);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(
                InetAddress.getLoopbackAddress(),
                randomPort,
                handler,
                serverHandler,
                serverWriteTimeoutSeconds,
                maxFrameSize,
                maxAggregatedFrameSize
        );
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:" + randomPort + serverPath).build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, msg);
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.sendMessage(body);
                        this.webSocket.close(1000, null);
                    } catch (IOException e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(IOException e, Response response) {
                failureReference.set(e);
            }

            @Override
            public void onMessage(ResponseBody message) throws IOException {
                JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, message.bytes());

                Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
                Assertions.assertEquals("Cannot dispatch batch requests. 1 is the max number of supported batch requests", jsonRpcResponse.get("error").get("message").asText());

                message.close();
                wsAsyncResultLatch.countDown();
            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });

        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail("Result timed out");
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail("Close timed out");
        }

        websocketServer.stop();

        Exception failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }
    }

    @Test
    void testMaxResponseSize() throws Exception {
        String content = "[\n" +
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

        byte[] msg = content.getBytes();
        String serverPath = "/";

        Web3 web3Mock = mock(Web3.class);
        String result = mediumJson();
        when(web3Mock.web3_sha3(any())).thenReturn(result);
        int responseSize = result.getBytes().length * 2 - 1;
        int randomPort = 9998;

        TestSystemProperties testSystemProperties = new TestSystemProperties();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        RskWebSocketJsonRpcHandler handler = new RskWebSocketJsonRpcHandler(null);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder()
                .rpcModules(filteredModules)
                .rpcMaxResponseSize(responseSize)
                .rpcTimeout(testSystemProperties.getRpcTimeout())
                .build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        int serverWriteTimeoutSeconds = testSystemProperties.rpcWebSocketServerWriteTimeoutSeconds();
        int maxFrameSize = testSystemProperties.rpcWebSocketMaxFrameSize();
        int maxAggregatedFrameSize = testSystemProperties.rpcWebSocketMaxAggregatedFrameSize();

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(
                InetAddress.getLoopbackAddress(),
                randomPort,
                handler,
                serverHandler,
                serverWriteTimeoutSeconds,
                maxFrameSize,
                maxAggregatedFrameSize
        );
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:" + randomPort + serverPath).build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, msg);
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.sendMessage(body);
                        this.webSocket.close(1000, null);
                    } catch (IOException e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(IOException e, Response response) {
                failureReference.set(e);
            }

            @Override
            public void onMessage(ResponseBody message) throws IOException {
                JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, message.bytes());

                Assertions.assertEquals(JsonRpcError.RESPONSE_LIMIT_ERROR,jsonRpcResponse.get("error").get("code").asInt());
                message.close();
                wsAsyncResultLatch.countDown();
            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });

        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail("Result timed out");
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail("Close timed out");
        }

        websocketServer.stop();

        Exception failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }

        verify(web3Mock, times(2)).web3_sha3(any());
    }

    @Test
    void testMaxBatchRequestWithNestedLevels() throws Exception {
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

        byte[] msg = content.getBytes();
        String serverPath = "/";

        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = 9994;

        TestSystemProperties testSystemProperties = new TestSystemProperties();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        RskWebSocketJsonRpcHandler handler = new RskWebSocketJsonRpcHandler(null);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        int serverWriteTimeoutSeconds = testSystemProperties.rpcWebSocketServerWriteTimeoutSeconds();
        int maxFrameSize = testSystemProperties.rpcWebSocketMaxFrameSize();
        int maxAggregatedFrameSize = testSystemProperties.rpcWebSocketMaxAggregatedFrameSize();

        assertEquals(DEFAULT_WRITE_TIMEOUT_SECONDS, serverWriteTimeoutSeconds);
        assertEquals(DEFAULT_MAX_FRAME_SIZE, maxFrameSize);
        assertEquals(DEFAULT_MAX_AGGREGATED_FRAME_SIZE, maxAggregatedFrameSize);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(
                InetAddress.getLoopbackAddress(),
                randomPort,
                handler,
                serverHandler,
                serverWriteTimeoutSeconds,
                maxFrameSize,
                maxAggregatedFrameSize
        );
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:" + randomPort + serverPath).build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, msg);
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.sendMessage(body);
                        this.webSocket.close(1000, null);
                    } catch (IOException e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(IOException e, Response response) {
                failureReference.set(e);
            }

            @Override
            public void onMessage(ResponseBody message) throws IOException {
                JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, message.bytes());

                Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
                Assertions.assertEquals("Cannot dispatch batch requests. 1 is the max number of supported batch requests", jsonRpcResponse.get("error").get("message").asText());

                message.close();
                wsAsyncResultLatch.countDown();
            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });

        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail("Result timed out");
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail("Close timed out");
        }

        websocketServer.stop();

        Exception failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }
    }

    @Test
    void testStackOverflowErrorInRequest() throws Exception {
        List<String> messages = new ArrayList<>();
        String content = this.getJsonRpcDummyMessageStr("value");

        for (long i = 0; i < 99_999; i++) {
            content = String.format("[[[[[[[[[[%s]]]]]]]]]]", content);
        }

        byte[] msg = content.getBytes();
        String serverPath = "/";

        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = 9996;

        TestSystemProperties testSystemProperties = new TestSystemProperties();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        RskWebSocketJsonRpcHandler handler = new RskWebSocketJsonRpcHandler(null);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        int serverWriteTimeoutSeconds = testSystemProperties.rpcWebSocketServerWriteTimeoutSeconds();
        int maxFrameSize = 9_999_999;
        int maxAggregatedFrameSize = 9_999_999;

        assertEquals(DEFAULT_WRITE_TIMEOUT_SECONDS, serverWriteTimeoutSeconds);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(
                InetAddress.getLoopbackAddress(),
                randomPort,
                handler,
                serverHandler,
                serverWriteTimeoutSeconds,
                maxFrameSize,
                maxAggregatedFrameSize
        );
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:" + randomPort + serverPath).build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, msg);
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.sendMessage(body);
                        this.webSocket.close(1000, null);
                    } catch (IOException e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(IOException e, Response response) {
                failureReference.set(e);
            }

            @Override
            public void onMessage(ResponseBody message) throws IOException {
                JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, message.bytes());

                messages.add(jsonRpcResponse.toPrettyString());

                Assertions.assertEquals(jsonRpcResponse.get("error").get("code").asInt(), ErrorResolver.JsonError.INVALID_REQUEST.code);
                Assertions.assertEquals("Invalid request", jsonRpcResponse.get("error").get("message").asText());

                message.close();
                wsAsyncResultLatch.countDown();
            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });

        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail(String.format("Result timed out. %s", String.join("", messages)));
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail(String.format("Close timed out. %s", String.join("", messages)));
        }

        websocketServer.stop();

        Exception failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }
    }

    private void smokeTest(byte[] msg, Integer port) throws Exception {
        smokeTest(msg, "/websocket", port);
    }

    private void smokeTest(byte[] msg, String serverPath, Integer port) throws Exception {
        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = Optional.ofNullable(port).orElse(9998);

        TestSystemProperties testSystemProperties = new TestSystemProperties();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList(), 0, new HashMap<>()));
        RskWebSocketJsonRpcHandler handler = new RskWebSocketJsonRpcHandler(null);
        JsonRpcWeb3ServerProperties properties = JsonRpcWeb3ServerProperties.builder().maxBatchRequestsSize(1).rpcModules(filteredModules).build();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, properties);
        int serverWriteTimeoutSeconds = testSystemProperties.rpcWebSocketServerWriteTimeoutSeconds();
        int maxFrameSize = testSystemProperties.rpcWebSocketMaxFrameSize();
        int maxAggregatedFrameSize = testSystemProperties.rpcWebSocketMaxAggregatedFrameSize();

        assertEquals(DEFAULT_WRITE_TIMEOUT_SECONDS, serverWriteTimeoutSeconds);
        assertEquals(DEFAULT_MAX_FRAME_SIZE, maxFrameSize);
        assertEquals(DEFAULT_MAX_AGGREGATED_FRAME_SIZE, maxAggregatedFrameSize);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(
                InetAddress.getLoopbackAddress(),
                randomPort,
                handler,
                serverHandler,
                serverWriteTimeoutSeconds,
                maxFrameSize,
                maxAggregatedFrameSize
        );
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:" + randomPort + serverPath).build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, msg);
                    try {
                        this.webSocket = webSocket;
                        this.webSocket.sendMessage(body);
                        this.webSocket.close(1000, null);
                    } catch (IOException e) {
                        failureReference.set(e);
                    }
                });
            }

            @Override
            public void onFailure(IOException e, Response response) {
                failureReference.set(e);
            }

            @Override
            public void onMessage(ResponseBody message) throws IOException {
                JsonNode jsonRpcResponse = JacksonParserUtil.readTree(OBJECT_MAPPER, message.bytes());
                assertThat(jsonRpcResponse.at("/result").asText(), is(mockResult));
                message.close();
                wsAsyncResultLatch.countDown();
            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                wsAsyncCloseLatch.countDown();
            }
        });

        if (!wsAsyncResultLatch.await(10, TimeUnit.SECONDS)) {
            fail("Result timed out");
        }

        if (!wsAsyncCloseLatch.await(10, TimeUnit.SECONDS)) {
            fail("Close timed out");
        }

        websocketServer.stop();

        Exception failure = failureReference.get();
        if (failure != null) {
            failure.printStackTrace();
            fail(failure.getMessage());
        }
    }

    private Map<String, JsonNode> getJsonRpcDummyMessageMap(String value) {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add(value));

        return jsonRpcRequestProperties;
    }

    private String getJsonRpcDummyMessageStr(String value) {
        Map<String, JsonNode> jsonRpcRequestProperties = getJsonRpcDummyMessageMap(value);

        String request = "";
        try {
            Object object = JacksonParserUtil.treeToValue(OBJECT_MAPPER, JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties), Object.class);
            request = OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }
        return request;

    }

    private byte[] getJsonRpcDummyMessage(String value) {
        Map<String, JsonNode> jsonRpcRequestProperties = getJsonRpcDummyMessageMap(value);

        byte[] request = new byte[0];
        try {
            Object object = JacksonParserUtil.treeToValue(OBJECT_MAPPER, JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties), Object.class);
            request = OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }
        return request;

    }

    private byte[] getJsonRpcBigMessage() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 55; i++) {
            s.append("thisisabigmessagethatwillbesentchunked");
        }
        return getJsonRpcDummyMessage(s.toString());
    }

    private String mediumJson() {
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
