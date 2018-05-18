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

import co.rsk.rpc.ModuleDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.squareup.okhttp.*;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import okio.Buffer;
import org.ethereum.rpc.Web3;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Web3WebSocketServerTest {

    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExecutorService wsExecutor;

    @Before
    public void setup() {
        wsExecutor = Executors.newSingleThreadExecutor();
    }

    @Test
    public void smokeTest() throws Exception {
        Web3 web3Mock = mock(Web3.class);
        String mockResult = "output";
        when(web3Mock.web3_sha3(anyString())).thenReturn(mockResult);

        int randomPort = 9998;//new ServerSocket(0).getLocalPort();

        List<ModuleDescription> filteredModules = Collections.singletonList(new ModuleDescription("web3", "1.0", true, Collections.emptyList(), Collections.emptyList()));
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Mock, filteredModules);

        Web3WebSocketServer websocketServer = new Web3WebSocketServer(InetAddress.getLoopbackAddress(), randomPort, serverHandler);
        websocketServer.start();

        OkHttpClient wsClient = new OkHttpClient();
        Request wsRequest = new Request.Builder().url("ws://localhost:"+randomPort+"/websocket").build();
        WebSocketCall wsCall = WebSocketCall.create(wsClient, wsRequest);

        CountDownLatch wsAsyncResultLatch = new CountDownLatch(1);
        CountDownLatch wsAsyncCloseLatch = new CountDownLatch(1);
        AtomicReference<Exception> failureReference = new AtomicReference<>();
        wsCall.enqueue(new WebSocketListener() {

            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsExecutor.submit(() -> {
                    RequestBody body = RequestBody.create(WebSocket.TEXT, getJsonRpcDummyMessage());
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
                JsonNode jsonRpcResponse = OBJECT_MAPPER.readTree(message.bytes());
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

    @After
    public void tearDown() {
        wsExecutor.shutdown();
    }

    private byte[] getJsonRpcDummyMessage() {
        Map<String, JsonNode> jsonRpcRequestProperties = new HashMap<>();
        jsonRpcRequestProperties.put("jsonrpc", JSON_NODE_FACTORY.textNode("2.0"));
        jsonRpcRequestProperties.put("id", JSON_NODE_FACTORY.numberNode(13));
        jsonRpcRequestProperties.put("method", JSON_NODE_FACTORY.textNode("web3_sha3"));
        jsonRpcRequestProperties.put("params", JSON_NODE_FACTORY.arrayNode().add("value"));

        byte[] request = new byte[0];
        try {
            request = OBJECT_MAPPER.writeValueAsBytes(OBJECT_MAPPER.treeToValue(
                    JSON_NODE_FACTORY.objectNode().setAll(jsonRpcRequestProperties), Object.class));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }
        return request;

    }
}