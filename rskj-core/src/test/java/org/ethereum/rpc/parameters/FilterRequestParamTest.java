/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.rpc.FilterRequest;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterRequestParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validInput() throws JsonProcessingException {
        String filterRequestInput = "{\n" +
                "            \"fromBlock\": \"0x1\"," +
                "            \"toBlock\" : \"0x2\"," +
                "            \"address\": \"0x7857288e171c6159c5576d1bd9ac40c0c48a771c\"," +
                "            \"topics\":[\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\"]," +
                "            \"blockHash\": \"0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        FilterRequestParam filterRequestParam = objectMapper.convertValue(jsonNode, FilterRequestParam.class);
        assertNotNull(filterRequestParam);
        assertEquals("0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3", filterRequestParam.getBlockHash().getHash().toJsonString());
        assertEquals("0x1", filterRequestParam.getFromBlock().getIdentifier());
        assertEquals("0x2", filterRequestParam.getToBlock().getIdentifier());
        assertEquals("0x7857288e171c6159c5576d1bd9ac40c0c48a771c", filterRequestParam.getAddress()[0].getAddress().toJsonString());
        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f706963", filterRequestParam.getTopics()[0][0].getHash().toJsonString());
    }

    @Test
    void filterRequestParsesArrayOfTopic() throws JsonProcessingException {
        String filterRequestInput = "{\n" +
                "            \"topics\":[\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\", " +
                "                        [\"0x0000000000000000000000000000000000000000000000000000000000001111\",\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\"]]" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        FilterRequestParam filterRequestParam = objectMapper.convertValue(jsonNode, FilterRequestParam.class);
        assertNotNull(filterRequestParam);
        TopicParam[][] topics = filterRequestParam.getTopics();
        assertEquals(2, topics.length);
        TopicParam[] topicParam = topics[1];
        assertEquals(2, topicParam.length);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000001111", topicParam[0].getHash().toJsonString());
    }

    @Test
    void invalidTopicFails() throws JsonProcessingException {
        String filterRequestInput = "{\n" +
                "            \"topics\":[\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\", " +
                "                        [\"0x0000000000000000000000000000000000000000000000000000000000001111\",\"0x0000w0000000000000000000000000006d696e696e675f6665655f746f706963\"]]" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, FilterRequestParam.class));
    }

    @Test
    void filterRequestParsesSingleTopic() throws JsonProcessingException {
        String filterRequestInput = "{\n" +
                "            \"topics\":\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\"" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        FilterRequestParam filterRequestParam = objectMapper.convertValue(jsonNode, FilterRequestParam.class);
        assertNotNull(filterRequestParam);
        TopicParam[][] topics = filterRequestParam.getTopics();
        assertEquals(1, topics.length);
        TopicParam[] topicParam = topics[0];
        assertEquals(1, topicParam.length);
        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f706963", topicParam[0].getHash().toJsonString());
    }

    @Test
    void invalidFromBlockFails() throws JsonProcessingException {
        String filterRequestInput = "{\"fromBlock\" : \"0x2w\"}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, FilterRequestParam.class));
    }

    @Test
    void invalidToBlockFails() throws JsonProcessingException {
        String filterRequestInput = "{\"toBlock\" : \"ab\"}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, FilterRequestParam.class));
    }

    @Test
    void toFilterRequestProducesValidObject() {
        FilterRequestParam filterRequestParam = new FilterRequestParam(
                new BlockIdentifierParam("0x1"),
                new BlockIdentifierParam("0x2"),
                new HexAddressParam[]{new HexAddressParam("0x7857288e171c6159c5576d1bd9ac40c0c48a771c")},
                new TopicParam[][]{
                        new TopicParam[]{
                                new TopicParam("0x000000000000000000000000000000006d696e696e675f6665655f746f706963"),
                                new TopicParam("0x000000000000000000000000000000006d696e696e675f6665655f746f711111")
                        },
                        new TopicParam[]{
                                new TopicParam("0x000000000000000000000000000000006d696e696e675f6665655f746f706963")
                        }
                },
                new BlockHashParam("0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3"));
        FilterRequest filterRequest = filterRequestParam.toFilterRequest();
        assertEquals("0x1", filterRequest.getFromBlock());
        assertEquals("0x2", filterRequest.getToBlock());
        assertEquals("0x7857288e171c6159c5576d1bd9ac40c0c48a771c", filterRequest.getAddress());
        assertEquals(2, filterRequest.getTopics().length);
        assertTrue(filterRequest.getTopics()[0] instanceof Collection<?>);
        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f711111", ((List<String>) filterRequest.getTopics()[0]).get(1));
        assertTrue(filterRequest.getTopics()[1] instanceof String);
        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f706963", filterRequest.getTopics()[1]);

    }

    @Test
    void canHandleNullTopics() throws JsonProcessingException {
        String filterRequestInput = "{\n" +
                "            \"topics\":[\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\", null, [\"0x000000000000000000000000000000006d696e696e675f6665655f746f706963\",null]]}";
        JsonNode jsonNode = objectMapper.readTree(filterRequestInput);
        FilterRequestParam filterRequestParam = objectMapper.convertValue(jsonNode, FilterRequestParam.class);
        FilterRequest fr = objectMapper.convertValue(jsonNode, FilterRequest.class);

        assertNotNull(filterRequestParam);
        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f706963", filterRequestParam.getTopics()[0][0].getHash().toJsonString());
        FilterRequest filterRequest = filterRequestParam.toFilterRequest();
        assertNotNull(filterRequest);
        assertEquals(3, filterRequest.getTopics().length);
        assertEquals(2, ((List<String>) filterRequest.getTopics()[2]).size());

    }
}