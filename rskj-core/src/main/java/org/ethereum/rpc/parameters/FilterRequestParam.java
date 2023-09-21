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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.FilterRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@JsonDeserialize(using = FilterRequestParam.Deserializer.class)
public class FilterRequestParam {

    private final BlockIdentifierParam fromBlock;
    private final BlockIdentifierParam toBlock;
    private final HexAddressParam[] address;
    private final TopicParam[][] topics;
    private final BlockHashParam blockHash;

    public FilterRequestParam(BlockIdentifierParam fromBlock, BlockIdentifierParam toBlock, HexAddressParam[] address, TopicParam[][] topics, BlockHashParam blockHash) {
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.address = address;
        this.topics = topics;
        this.blockHash = blockHash;
    }

    public BlockIdentifierParam getFromBlock() {
        return fromBlock;
    }

    public BlockIdentifierParam getToBlock() {
        return toBlock;
    }

    public HexAddressParam[] getAddress() {
        return address;
    }

    public TopicParam[][] getTopics() {
        return topics;
    }

    public BlockHashParam getBlockHash() {
        return blockHash;
    }

    public FilterRequest toFilterRequest() {
        String fb = this.fromBlock == null ? null : this.fromBlock.getIdentifier();
        String tb = this.toBlock == null ? null : this.toBlock.getIdentifier();
        Object ad = this.address == null ? null : this.parseAddressArray();
        String bh = this.blockHash == null ? null : this.blockHash.getHash().toJsonString();
        Object[] tp = this.topics == null ? null : this.parseTopicArrayToObjectArray();

        FilterRequest filterRequest = new FilterRequest();
        filterRequest.setAddress(ad);
        filterRequest.setBlockHash(bh);
        filterRequest.setFromBlock(fb);
        filterRequest.setToBlock(tb);
        filterRequest.setTopics(tp);

        return filterRequest;
    }

    private Object parseAddressArray() {
        if (this.address == null) {
            return null;
        }
        if (this.address.length == 1) {
            return this.address[0].getAddress().toJsonString();
        } else {
            List<String> arrayList = new ArrayList<>();
            for (int i = 0; i < this.address.length; i++) {
                arrayList.add(this.address[i].getAddress().toJsonString());
            }
            return arrayList;
        }
    }

    private Object[] parseTopicArrayToObjectArray() {
        if (this.topics == null) {
            return new Object[0];
        }
        Object[] result = new Object[this.topics.length];
        for (int i = 0; i < this.topics.length; i++) {
            TopicParam[] topicArray = this.topics[i];
            if (topicArray.length == 1) {
                result[i] = topicArray[0] != null ? topicArray[0].getHash().toJsonString() : null;
            } else {
                List<String> arrayList = new ArrayList<>();
                for (TopicParam topicParam : topicArray) {
                    arrayList.add(topicParam != null ? topicParam.getHash().toJsonString() : null);
                }
                result[i] = arrayList;
            }
        }
        return result;
    }

    public static class Deserializer extends StdDeserializer<FilterRequestParam> {
        private static final long serialVersionUID = -72304400913233552L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public FilterRequestParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            BlockIdentifierParam fromBlock = node.has("fromBlock") ? new BlockIdentifierParam(node.get("fromBlock").asText()) : null;
            BlockIdentifierParam toBlock = node.has("toBlock") ? new BlockIdentifierParam(node.get("toBlock").asText()) : null;
            HexAddressParam[] address = node.has("address") ? getAddressParam(node.get("address")) : null;
            BlockHashParam blockHash = node.has("blockHash") ? new BlockHashParam(node.get("blockHash").asText()) : null;
            TopicParam[][] topics = node.has("topics") ? getTopicArray(node.get("topics")) : null;

            return new FilterRequestParam(fromBlock, toBlock, address, topics, blockHash);
        }

        private HexAddressParam[] getAddressParam(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }

            if (node.isArray()) {
                HexAddressParam[] addresses = new HexAddressParam[node.size()];
                for (int i = 0; i < node.size(); i++) {
                    JsonNode subNode = node.get(i);
                    addresses[i] = new HexAddressParam(subNode.asText());
                }
                return addresses;
            }
            return new HexAddressParam[]{new HexAddressParam(node.asText())};
        }

        private TopicParam[][] getTopicArray(JsonNode node) {
            if (node == null || node.isNull()) {
                return new TopicParam[0][0];
            }
            if (node.isArray()) {
                TopicParam[][] topics = new TopicParam[node.size()][];
                for (int i = 0; i < node.size(); i++) {
                    JsonNode subNode = node.get(i);
                    if (subNode.isArray()) {
                        TopicParam[] topicParams = getTopics(subNode);
                        topics[i] = topicParams;
                    } else {
                        TopicParam subNodeTopic = subNode.asText().contentEquals("null") ? null : new TopicParam(subNode.asText());
                        topics[i] = new TopicParam[]{subNodeTopic};
                    }
                }
                return topics;
            }
            TopicParam topicParam = new TopicParam(node.asText());
            return new TopicParam[][]{new TopicParam[]{topicParam}};
        }

        private TopicParam[] getTopics(JsonNode jsonNode) {
            TopicParam[] topicParams = new TopicParam[jsonNode.size()];
            for (int j = 0; j < jsonNode.size(); j++) {
                JsonNode subNode = jsonNode.get(j);
                topicParams[j] = subNode.asText().contentEquals("null") ? null : new TopicParam(subNode.asText());
            }
            return topicParams;
        }

    }

}
