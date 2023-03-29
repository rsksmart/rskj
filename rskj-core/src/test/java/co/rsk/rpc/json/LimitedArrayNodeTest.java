package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class LimitedArrayNodeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testLimit_exceedingLimit() throws JsonProcessingException {
        String input = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";
        JsonNode jsonNode = mapper.convertValue(input, JsonNode.class);
        int sizeInBytes = mapper.writeValueAsBytes(input).length;
        LimitedArrayNode array = new LimitedArrayNode(JsonNodeFactory.instance, (sizeInBytes * 2) - 1);
        array.add(jsonNode);
        assertThrows(JsonRpcResponseLimitException.class, () -> {
            array.add(jsonNode);
        });
        assertEquals(1, array.size());
    }

    @Test
    void testLimit_nonExceedingLimit() throws JsonProcessingException {
        String input = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";
        JsonNode jsonNode = mapper.convertValue(input, JsonNode.class);
        int sizeInBytes = mapper.writeValueAsBytes(input).length;
        LimitedArrayNode array = new LimitedArrayNode(JsonNodeFactory.instance, (sizeInBytes * 2) + 1);
        array.add(jsonNode);
        array.add(jsonNode);
        assertEquals(2, array.size());
    }

    @Test
    void testEquals() {
        LimitedArrayNode array1 = new LimitedArrayNode(JsonNodeFactory.instance);
        array1.add(JsonNodeFactory.instance.numberNode(1));
        array1.add(JsonNodeFactory.instance.booleanNode(true));

        LimitedArrayNode array2 = new LimitedArrayNode(JsonNodeFactory.instance);
        array2.add(JsonNodeFactory.instance.numberNode(1));
        array2.add(JsonNodeFactory.instance.booleanNode(true));

        assertEquals(array1, array2);

        LimitedArrayNode array3 = new LimitedArrayNode(JsonNodeFactory.instance, 50);
        array3.add(JsonNodeFactory.instance.numberNode(1));
        array3.add(JsonNodeFactory.instance.booleanNode(true));

        assertNotEquals(array1, array3);
    }

    @Test
    void testHashCode() {
        LimitedArrayNode array1 = new LimitedArrayNode(JsonNodeFactory.instance);
        array1.add(JsonNodeFactory.instance.numberNode(1));
        array1.add(JsonNodeFactory.instance.booleanNode(true));

        LimitedArrayNode array2 = new LimitedArrayNode(JsonNodeFactory.instance);
        array2.add(JsonNodeFactory.instance.numberNode(1));
        array2.add(JsonNodeFactory.instance.booleanNode(true));

        assertEquals(array1.hashCode(), array2.hashCode());

        LimitedArrayNode array3 = new LimitedArrayNode(JsonNodeFactory.instance, 50);
        array3.add(JsonNodeFactory.instance.numberNode(1));
        array3.add(JsonNodeFactory.instance.booleanNode(true));

        assertNotEquals(array1.hashCode(), array3.hashCode());
    }
}
