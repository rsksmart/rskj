package co.rsk.rpc.netty;

import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import co.rsk.rpc.exception.JsonRpcTimeoutError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonRpcCustomServerTest {
    public static final String FIRST_METHOD_REQUEST = "{\"jsonrpc\":\"2.0\",\"method\":\"test_first\",\"params\":[\"param\"],\"id\":1}";
    public static final String SECOND_METHOD_REQUEST = "{\"jsonrpc\":\"2.0\",\"method\":\"test_second\",\"params\":[\"param\",\"param2\"],\"id\":1}";
    private final List<ModuleDescription> modules = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcCustomServer jsonRpcCustomServer;

    @Test
    void testHandleJsonNodeRequest() throws Exception {
        JsonNode request = objectMapper.readTree(FIRST_METHOD_REQUEST);
        String response = "test_method_response";
        Web3Test handler = mock(Web3Test.class);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, modules);

        when(handler.test_first(anyString())).thenReturn(response);

        JsonResponse actualResponse = jsonRpcCustomServer.handleJsonNodeRequest(request);
        assertEquals(response, actualResponse.getResponse().get("result").asText());
    }

    @Test
    void testHandleJsonNodeRequest_WithResponseLimit() throws Exception {
        String response = "test_method_response";
        JsonNode request = objectMapper.readTree(FIRST_METHOD_REQUEST);
        Web3Test handler = mock(Web3Test.class);
        ResponseSizeLimitContext.createResponseSizeContext(response.getBytes(StandardCharsets.UTF_8).length / 2);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, modules);

        when(handler.test_first(anyString())).thenReturn(response);

        assertThrows(JsonRpcResponseLimitError.class, () -> jsonRpcCustomServer.handleJsonNodeRequest(request));
    }

    @Test
    void testHandleJsonNodeRequest_WithMethodModule() throws Exception {
        JsonNode request = objectMapper.readTree(SECOND_METHOD_REQUEST);
        Web3Test handler = mock(Web3Test.class);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, getModulesWithMethodTimeout(0,125));
        String response = "test_method_response";

        when(handler.test_second(anyString(), anyString())).thenReturn(response);
        when(handler.test_second(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(250);
            return response;
        });

        JsonResponse actualResponse = jsonRpcCustomServer.handleJsonNodeRequest(request);
        assertEquals(response, actualResponse.getResponse().get("result").asText());
    }

    @Test
    void testHandleJsonNodeRequest_WithMethodTimeout() throws Exception {
        JsonNode request = objectMapper.readTree(SECOND_METHOD_REQUEST);
        Web3Test handler = mock(Web3Test.class);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, getModulesWithMethodTimeout(125, 0));

        when(handler.test_second(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(250);
            return "second_method_response";
        });

        assertThrows(JsonRpcTimeoutError.class, () -> jsonRpcCustomServer.handleJsonNodeRequest(request));
    }

    @Test
    void testHandleJsonNodeRequest_methodTimeoutOverModule() throws Exception {
        JsonNode request = objectMapper.readTree(SECOND_METHOD_REQUEST);
        Web3Test handler = mock(Web3Test.class);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, getModulesWithMethodTimeout(500, 100));
        String response = "test_method_response";

        when(handler.test_second(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(250);
            return response;
        });

        JsonResponse actualResponse = jsonRpcCustomServer.handleJsonNodeRequest(request);
        assertEquals(response, actualResponse.getResponse().get("result").asText());    }


    @Test //The timeout is applied per method and not per request
    void testHandleJsonNodeRequest_WithMethodTimeout_BatchRequest_OK() throws Exception {
        long timeoutPerMethod = 550;
        long sleepTimePerRequest = 300;
        //The request has 2 methods, each one will sleep for 300ms, so the total time will be 600ms.
        // The timeout per method is 550ms, so the request will be executed successfully.
        String jsonRequest = " [\n" +
                "    {\n" +
                "      \"jsonrpc\": \"2.0\",\n" +
                "      \"method\": \"test_second\",\n" +
                "      \"params\": [\n" +
                "        \"param1\",\n" +
                "        \"param2\"\n" +
                "      ],\n" +
                "      \"id\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"jsonrpc\": \"2.0\",\n" +
                "      \"method\": \"test_second\",\n" +
                "      \"params\": [\n" +
                "        \"param1\",\n" +
                "        \"param2\"\n" +
                "      ],\n" +
                "      \"id\": 2\n" +
                "    }\n" +
                "  ]";

        String response = "Expected response";
        JsonNode request = objectMapper.readTree(jsonRequest);
        Web3Test handler = mock(Web3Test.class);

        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, getModulesWithMethodTimeout(timeoutPerMethod,0));

        when(handler.test_second(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(sleepTimePerRequest);
            return response;
        });
        JsonResponse requestResponse = jsonRpcCustomServer.handleJsonNodeRequest(request);
        verify(handler,times(2)).test_second(anyString(), anyString());
        requestResponse.getResponse().forEach(jsonNode -> assertEquals(response, jsonNode.get("result").asText()));
    }

    @Test
    void testHandleJsonNodeRequest_WithMethodTimeout_BatchRequest_FAIL() throws Exception {
        long timeoutPerMethod = 150;
        long sleepTimePerRequest = 200;
        String jsonRequest = " [\n" +
                "    {\n" +
                "      \"jsonrpc\": \"2.0\",\n" +
                "      \"method\": \"test_second\",\n" +
                "      \"params\": [\n" +
                "        \"param1\",\n" +
                "        \"param2\"\n" +
                "      ],\n" +
                "      \"id\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"jsonrpc\": \"2.0\",\n" +
                "      \"method\": \"test_second\",\n" +
                "      \"params\": [\n" +
                "        \"param1\",\n" +
                "        \"param2\"\n" +
                "      ],\n" +
                "      \"id\": 2\n" +
                "    }\n" +
                "  ]";

        String response = "Expected response";
        JsonNode request = objectMapper.readTree(jsonRequest);
        Web3Test handler = mock(Web3Test.class);

        jsonRpcCustomServer = new JsonRpcCustomServer(handler, Web3Test.class, getModulesWithMethodTimeout(timeoutPerMethod,0));

        when(handler.test_second(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(sleepTimePerRequest);
            return response;
        });
        assertThrows(JsonRpcTimeoutError.class, () -> jsonRpcCustomServer.handleJsonNodeRequest(request));
        //The second request should not be executed
        verify(handler,times(1)).test_second(anyString(), anyString());
    }


    public interface Web3Test {
        String test_first(String param1);
        String test_second(String param1, String param2);

    }

    private List<ModuleDescription> getModulesWithMethodTimeout(long methodTimeout, long moduleTimeout) {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("test_first");
        enabledMethods.add("test_second");
        Map<String, Long> methodTimeoutMap = Collections.singletonMap("second", methodTimeout);

        List<String> disabledMethods = Collections.emptyList();

        ModuleDescription enabledModule = new ModuleDescription("test", "1.0", true, enabledMethods, disabledMethods, moduleTimeout, methodTimeoutMap);

        List<ModuleDescription> modules = new ArrayList<>();
        modules.add(enabledModule);

        return modules;
    }


}