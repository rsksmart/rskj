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
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals("0x7857288e171c6159c5576d1bd9ac40c0c48a771c", filterRequestParam.getAddress().getAddress().toJsonString());
//        assertEquals("0x000000000000000000000000000000006d696e696e675f6665655f746f706963", filterRequestParam.getTopics()[0].getHash().toJsonString());
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
        TopicArrayParam[] topics = filterRequestParam.getTopics();
        assertEquals(2, topics.length);
        TopicParam[] topicParam = topics[1].getTopics();
        assertEquals(2, topicParam.length);
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
        TopicArrayParam[] topics = filterRequestParam.getTopics();
        assertEquals(1, topics.length);
        TopicParam[] topicParam = topics[0].getTopics();
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



}