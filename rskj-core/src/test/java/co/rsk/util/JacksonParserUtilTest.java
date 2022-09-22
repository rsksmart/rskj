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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class JacksonParserUtilTest {


    @Test
    public void test_treeToValue() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}");
        Map jsonMap = JacksonParserUtil.treeToValue(new ObjectMapper(), jsonNode, Map.class);

        Assert.assertEquals("value", jsonMap.get("prop"));
    }

    @Test
    public void test_readTreeStringContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}");

        Assert.assertEquals("value", jsonNode.get("prop").asText());
    }

    @Test
    public void test_readTreeBytesContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, "{\"prop\": \"value\"}".getBytes());

        Assert.assertEquals("value", jsonNode.get("prop").asText());
    }

    @Test
    public void test_readTreeInputStreamContent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = JacksonParserUtil.readTree(mapper, new ByteArrayInputStream("{\"prop\": \"value\"}".getBytes()));

        Assert.assertEquals("value", jsonNode.get("prop").asText());
    }
}
