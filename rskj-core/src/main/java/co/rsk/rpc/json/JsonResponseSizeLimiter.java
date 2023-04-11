package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.util.Iterator;
import java.util.Map;

public class JsonResponseSizeLimiter {
    private static final int BRACKETS_SIZE_IN_BYTES = 2;
    private static final int COMMA_SIZE = 1;
    private static final int JSON_FIELD_MISSING_SYMBOLS = 3;

    private int acc = 0;
    private final int max;
    private final JsonNode root;
    private final ObjectMapper objectMapper;

    private JsonResponseSizeLimiter(int max, JsonNode root) {
        this.max = max;
        this.root = root;
        this.objectMapper = new ObjectMapper();
    }

    public static int getSizeInBytesWithLimit(JsonNode node, int limit) {
        if (node == null) {
            return 0;
        }
        JsonResponseSizeLimiter limiter = new JsonResponseSizeLimiter(limit, node);
        try {
            return limiter.getJsonLength();
        } catch (JsonProcessingException jsonProcessingException) {
            throw RskJsonRpcRequestException.invalidParamError("Couldn't process Json Object:  " + jsonProcessingException.getMessage());
        }
    }

    private int getJsonLength() throws JsonProcessingException {
        addLength(root);
        return acc;
    }

    private void addLength(JsonNode jsonNode) throws JsonProcessingException {
        if (jsonNode.isArray()) {
            handleArray((ArrayNode) jsonNode);
        } else if (jsonNode.isValueNode()) {
            handleValueNode(jsonNode);
        } else {
            handleObject(jsonNode);
        }
    }

    private void handleValueNode(JsonNode node) throws JsonProcessingException {
        sumAndCheck(objectMapper.writeValueAsBytes(node).length);
    }

    private void handleArray(ArrayNode arrayNode) throws JsonProcessingException {
        Iterator<JsonNode> elements = arrayNode.elements();
        int eleNo = elements.hasNext() ? -1 : 0;
        while (elements.hasNext()) {
            addLength(elements.next());
            eleNo++;
        }
        sumAndCheck(eleNo + BRACKETS_SIZE_IN_BYTES);
    }

    private void handleObject(JsonNode rootNode) throws JsonProcessingException {
        Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
        int fieldSeparatorCount = fields.hasNext() ? -1 : 0;
        while (fields.hasNext()) {
            fieldSeparatorCount++;
            handleField(fields.next());
        }
        sumAndCheck(BRACKETS_SIZE_IN_BYTES + (COMMA_SIZE * fieldSeparatorCount));
    }

    private void handleField(Map.Entry<String, JsonNode> field) throws JsonProcessingException {
        JsonNode value = field.getValue();
        if (value.isObject() || value.isArray()) {
            addLength(value);
            sumAndCheck(field.getKey().getBytes().length + JSON_FIELD_MISSING_SYMBOLS);
        } else {
            sumAndCheck(objectMapper.writeValueAsBytes(field).length - BRACKETS_SIZE_IN_BYTES);
        }
    }

    private void sumAndCheck(int value) {
        acc += value;
        if (acc > max) {
            throw new JsonRpcResponseLimitError(max);
        }
    }

}
