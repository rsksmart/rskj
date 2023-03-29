package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonResponseSizeLimiterTest {


    private static final int MAX_LIMIT = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("jsonProvider")
    @DisplayName("Test JSON size limit")
    void testJsonSize_LessThanLimit(String json, int max) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        int expectedSize = objectMapper.writeValueAsBytes(node).length;
        int size = JsonResponseSizeLimiter.getSizeInBytesWithLimit(node, max);
        assertEquals(expectedSize, size);
    }

    private static Stream<Object[]> jsonProvider() {
        return Stream.of(
                new Object[]{"{\"id\":1,\"name\":\"John Doe\",\"age\":30}", Integer.MAX_VALUE},
                new Object[]{"{\"fruits\":[\"apple\",\"banana\",\"pear\"]}", Integer.MAX_VALUE},
                new Object[]{"{\"person\":{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}}", Integer.MAX_VALUE},
                new Object[]{"{\"id\":1,\"name\":\"John Doe\",\"age\":30,\"fruits\":[\"apple\",\"banana\",\"pear\"],\"address\":{\"street\":\"Main Street\",\"number\":123}}", Integer.MAX_VALUE},
                new Object[]{"[\"apple\",\"banana\",\"pear\"]", Integer.MAX_VALUE},
                new Object[]{"[{\"id\":1,\"name\":\"John Doe\",\"age\":30},{\"id\":2,\"name\":\"John Smith\",\"age\":29}]", Integer.MAX_VALUE}
        );
    }

    @Test
    void testGetSizeInBytesWithLimit_NullNode_ReturnsZero() {
        int result = JsonResponseSizeLimiter.getSizeInBytesWithLimit(null, MAX_LIMIT);
        assertEquals(0, result);
    }

    @Test
    void testGetSizeInBytesWithLimit_ComplexObjectNode_ExceedsLimit() throws JsonProcessingException {
        // Arrange
        String input = "{\"id\":1,\"name\":\"John Doe\",\"age\":30,\"fruits\":[\"apple\",\"banana\",\"pear\"],\"address\":{\"street\":\"Main Street\",\"number\":123}}";
        JsonNode jsonNode = objectMapper.readTree(input);
        int limit = 5;

        // Act and assert
        assertThrows(JsonRpcResponseLimitException.class, () -> JsonResponseSizeLimiter.getSizeInBytesWithLimit(jsonNode, limit));
    }
}
