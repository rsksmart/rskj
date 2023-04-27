/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitError;
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
        assertThrows(JsonRpcResponseLimitError.class, () -> JsonResponseSizeLimiter.getSizeInBytesWithLimit(jsonNode, limit));
    }
}
