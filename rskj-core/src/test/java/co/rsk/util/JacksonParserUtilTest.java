/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.util;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class JacksonParserUtilTest {
    @Test
    public void test_treeToValue() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}");
        Map jsonMap = JacksonParserUtil.treeToValue(mapper, jsonNode, Map.class);

        Assertions.assertEquals("value", jsonMap.get("prop"));
    }

    @Test
    public void test_treeToValueEmptyContent() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JsonNodeFactory.instance.missingNode();
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.treeToValue(mapper, jsonNode, Map.class));
    }

    @Test
    public void test_treeToValueNullContent() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JsonNodeFactory.instance.nullNode();
        Assertions.assertThrows(NullPointerException.class, () -> JacksonParserUtil.treeToValue(mapper, jsonNode, Map.class));
    }

    @Test
    public void test_readTreeStringContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}");

        Assertions.assertEquals("value", jsonNode.get("prop").asText());
    }

    @Test
    public void test_readTreeEmptyStringContent() {
        ObjectMapper mapper = new ObjectMapper();
        Assertions.assertThrows(JsonMappingException.class, () ->  JacksonParserUtil.readTree(mapper, ""));
    }

    @Test
    public void test_readTreeNullStringContent() {
        ObjectMapper mapper = new ObjectMapper();
        String content =  null;
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.readTree(mapper, content));
    }

    @Test
    public void test_readTreeBytesContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}".getBytes());

        Assertions.assertEquals("value", jsonNode.get("prop").asText());
    }

    @Test
    public void test_readTreeEmptyBytesContent() {
        ObjectMapper mapper = new ObjectMapper();
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.readTree(mapper, "".getBytes()));
    }

    @Test
    public void test_readTreeNullBytesContent() {
        ObjectMapper mapper = new ObjectMapper();
        byte[] content =  null;
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.readTree(mapper, content));
    }

    @Test
    public void test_readTreeInputStreamContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, new ByteArrayInputStream("{\"prop\": \"value\"}".getBytes()));

        Assertions.assertEquals("value", jsonNode.get("prop").asText());
    }

    @Test
    public void test_readTreeEmptyInputStreamContent() {
        ObjectMapper mapper = new ObjectMapper();
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.readTree(mapper, new ByteArrayInputStream("".getBytes())));
    }

    @Test
    public void test_readTreeNullInputStreamContent() {
        ObjectMapper mapper = new ObjectMapper();
        ByteArrayInputStream byteArrayInputStream = null;
        Assertions.assertThrows(JsonMappingException.class, () -> JacksonParserUtil.readTree(mapper, byteArrayInputStream));
    }
}
