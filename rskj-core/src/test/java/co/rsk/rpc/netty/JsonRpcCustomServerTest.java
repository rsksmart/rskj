package co.rsk.rpc.netty;

import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonRpcCustomServerTest {

    private final List<ModuleDescription> modules = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcCustomServer jsonRpcCustomServer;

    @Test
    void testHandleJsonNodeRequest() throws Exception {
        String jsonRequest = getTestRequest();
        String response = getTestResponse();
        JsonNode request = objectMapper.readTree(jsonRequest);


        ServiceInterface handler = mock(ServiceInterface.class);
        jsonRpcCustomServer = new JsonRpcCustomServer(handler, ServiceInterface.class, modules);
        JsonNode expectedResponse = objectMapper.readTree(response);

        when(handler.testMethod(anyString())).thenReturn(expectedResponse.get("result"));

        JsonResponse actualResponse = jsonRpcCustomServer.handleJsonNodeRequest(request);
        assertEquals(expectedResponse, actualResponse.getResponse());
    }

    @Test
    void testHandleJsonNodeRequest_WithResponseLimit() throws Exception {
        String jsonRequest = getTestRequest();
        String response = getTestResponse();
        JsonNode request = objectMapper.readTree(jsonRequest);
        ServiceInterface handler = mock(ServiceInterface.class);
        ResponseSizeLimitContext.createResponseSizeContext(response.getBytes(StandardCharsets.UTF_8).length/2);

        jsonRpcCustomServer = new JsonRpcCustomServer(handler, ServiceInterface.class, modules);

        JsonNode expectedResponse = objectMapper.readTree(response);
        when(handler.testMethod(anyString())).thenReturn(expectedResponse.get("result"));
        assertThrows(JsonRpcResponseLimitError.class, () -> jsonRpcCustomServer.handleJsonNodeRequest(request));
    }

    private String getTestResponse() {
        return "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"id\": 1,\n" +
                "    \"result\": {\n" +
                "        \"timestamp\": \"0x5d1f551e\"\n" +
                "    }\n" +
                "}";
    }

    private String getTestRequest() {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"testMethod\",\"params\":[\"param\"],\"id\":1}";
    }

    public interface ServiceInterface {
        JsonNode testMethod(String param1);
    }

}