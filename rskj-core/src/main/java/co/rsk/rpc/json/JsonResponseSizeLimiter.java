package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitException;
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
    private static final int JSON_FILED_MISSING_SYMBOLS = 3;
    private static final long serialVersionUID = -4364188205571356867L;

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
        acc = 0;
        return getLength(root);
    }

    private int getLength(JsonNode jsonNode) throws JsonProcessingException {
        if (jsonNode.isArray()) {
            return handleArray((ArrayNode) jsonNode);
        }
        if (jsonNode.isValueNode()) {
            return handleValueNode(jsonNode);
        }
        return handleObject(jsonNode);
    }

    private int handleValueNode(JsonNode node) throws JsonProcessingException {
        acc += objectMapper.writeValueAsBytes(node).length;
        if (acc > max) {
            limitReached();
        }
        return acc;
    }

    private int handleArray(ArrayNode arrayNode) throws JsonProcessingException {
        Iterator<JsonNode> elements = arrayNode.elements();
        int eleNo = elements.hasNext() ? -1 : 0;
        while (elements.hasNext()) {
            acc = getLength(elements.next());
            eleNo++;
        }
        return acc + eleNo + BRACKETS_SIZE_IN_BYTES;
    }

    private int handleObject(JsonNode rootNode) throws JsonProcessingException {
        Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
        int fieldsNo = fields.hasNext() ? -1 : 0;
        while (fields.hasNext()) {
            fieldsNo++;
            acc = getFiledLength(fields.next());
            if (acc > max) {
                limitReached();
            }
        }

        acc = acc + BRACKETS_SIZE_IN_BYTES + (COMMA_SIZE * fieldsNo);
        if (acc > max) {
            limitReached();
        }
        return acc;
    }

    private int getFiledLength(Map.Entry<String, JsonNode> field) throws JsonProcessingException {
        JsonNode value = field.getValue();
        if (value.isObject() || value.isArray()) {
            int objectSize = getLength(value);
            return (field.getKey()).getBytes().length + JSON_FILED_MISSING_SYMBOLS + objectSize;
        }
        return acc + objectMapper.writeValueAsBytes(field).length - BRACKETS_SIZE_IN_BYTES;
    }

    private void limitReached() {
        throw new JsonRpcResponseLimitException(max);
    }

}
