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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

@JsonDeserialize(using = FilterRequestParam.Deserializer.class)
public class FilterRequestParam {

    @JsonProperty
    private final BlockIdentifierParam fromBlock;
    @JsonProperty
    private final BlockIdentifierParam toBlock;
    @JsonProperty
    private final HexAddressParam address;
    @JsonProperty
    private final TopicArrayParam[] topics;
    @JsonProperty
    private final BlockHashParam blockHash;

    public FilterRequestParam(BlockIdentifierParam fromBlock, BlockIdentifierParam toBlock, HexAddressParam address, TopicArrayParam[] topics, BlockHashParam blockHash) {
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

    public HexAddressParam getAddress() {
        return address;
    }

    public TopicArrayParam[] getTopics() {
        return topics;
    }

    public BlockHashParam getBlockHash() {
        return blockHash;
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
            HexAddressParam address = node.has("address") ? new HexAddressParam(node.get("address").asText()) : null;
            BlockHashParam blockHash = node.has("blockHash") ? new BlockHashParam(node.get("blockHash").asText()) : null;
            TopicArrayParam[] topics = node.has("topics") ? getTopicsArray(node.get("topics")) : null;

            return new FilterRequestParam(fromBlock, toBlock, address, topics, blockHash);
        }

        private TopicArrayParam[] getTopicsArray(JsonNode node) {
            if (node == null || node.isNull()) {
                return new TopicArrayParam[0];
            }
            if (node.isArray()) {
                TopicArrayParam[] topics = new TopicArrayParam[node.size()];
                for (int i = 0; i < node.size(); i++) {
                    JsonNode subNode = node.get(i);
                    if (subNode.isArray()) {
                        TopicParam[] topicParams = getTopics(subNode);
                        topics[i] = new TopicArrayParam(topicParams);
                    } else {
                        topics[i] = new TopicArrayParam(new TopicParam(subNode.asText()));
                    }
                }
                return topics;
            }
            TopicParam topicParam = new TopicParam(node.asText());
            return new TopicArrayParam[]{new TopicArrayParam(topicParam)};
        }

        private TopicParam[] getTopics(JsonNode jsonNode) {
            TopicParam[] topicParams = new TopicParam[jsonNode.size()];
            for (int j = 0; j < jsonNode.size(); j++) {
                topicParams[j] = new TopicParam(jsonNode.get(j).asText());
            }
            return topicParams;
        }
    }

}
