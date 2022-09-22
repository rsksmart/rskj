package co.rsk.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public final class JacksonParserUtil {
    private JacksonParserUtil() {
    }

    public static <T> T treeToValue(ObjectMapper mapper, TreeNode treeNode, Class<T> aClass) throws IllegalArgumentException, JsonProcessingException {
        T result = mapper.treeToValue(treeNode, aClass);

        if (result == null) {
            throw new NullPointerException("Input is null");
        }

        return result;
    }

    public static JsonNode readTree(ObjectMapper mapper, InputStream is) throws IOException {
        JsonNode result = mapper.readTree(is);

        if (result.isEmpty()) {
            throw JsonMappingException.from(mapper.getDeserializationContext(), "Input is empty");
        }

        return result;
    }

    public static JsonNode readTree(ObjectMapper mapper, String content) throws IOException {
        JsonNode result = mapper.readTree(content);

        if (result.isEmpty()) {
            throw JsonMappingException.from(mapper.getDeserializationContext(), "Input is empty");
        }

        return result;
    }

    public static JsonNode readTree(ObjectMapper mapper, byte[] content) throws IOException {
        JsonNode result = mapper.readTree(content);

        if (result.isEmpty()) {
            throw JsonMappingException.from(mapper.getDeserializationContext(), "Input is empty");
        }

        return result;
    }
}
