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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
}
