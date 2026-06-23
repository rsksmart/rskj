/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = BlockIdentifierParam.Deserializer.class)
public class BlockIdentifierParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String REQUIRE_FORK_SAFE_KEY = "requireForkSafe";
    private static final String BLOCK_HASH_KEY = "blockHash";
    private static final String BLOCK_NUMBER_KEY = "blockNumber";
    private static final List<String> BLOCK_INPUT_KEYS = Arrays.asList(BLOCK_HASH_KEY, BLOCK_NUMBER_KEY);
    private static final List<String> IDENTIFIERS_TO_VALIDATE = Arrays.asList(
            BlockTag.EARLIEST.getTag(),
            BlockTag.LATEST.getTag(),
            BlockTag.PENDING.getTag(),
            BlockTag.SAFE.getTag(),
            BlockTag.FORK_SAFE.getTag());

    private final String identifier;
    private final boolean requireForkSafe;

    public BlockIdentifierParam(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier: empty or null.");
        }

        if (!IDENTIFIERS_TO_VALIDATE.contains(identifier)
                && !Utils.isDecimalString(identifier)
                && !Utils.isHexadecimalString(identifier)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier '" + identifier + "'");
        }

        this.identifier = identifier;
        this.requireForkSafe = false;
    }

    public BlockIdentifierParam(Map<String, String> inputs) {
        if (inputs == null || inputs.keySet().stream().noneMatch(BLOCK_INPUT_KEYS::contains)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block input");
        }
        validateMapItems(inputs);
        if (inputs.containsKey(BLOCK_NUMBER_KEY)) {
            this.identifier = inputs.get(BLOCK_NUMBER_KEY);
        } else {
            this.identifier = inputs.get(BLOCK_HASH_KEY);
        }
        this.requireForkSafe = parseRequireForkSafe(inputs.get(REQUIRE_FORK_SAFE_KEY));
    }

    private static boolean parseRequireForkSafe(String value) {
        if (value == null) {
            return false;
        }
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw RskJsonRpcRequestException.invalidParamError(
                    "Invalid input: requireForkSafe must be a String \"true\" or \"false\"");
        }
        return Boolean.parseBoolean(value);
    }

    private void validateMapItems(Map<String, String> inputs) {
        inputs.forEach((key, value) -> {
            switch (key) {
                case REQUIRE_FORK_SAFE_KEY:
                    parseRequireForkSafe(value);
                    break;
                case BLOCK_HASH_KEY:
                    new BlockHashParam(value);
                    break;
                case BLOCK_NUMBER_KEY:
                    new HexNumberParam(value);
                    break;
                default:
                    throw RskJsonRpcRequestException.invalidParamError("Invalid block input key: " + key);
            }
        });
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isRequireForkSafe() {
        return requireForkSafe;
    }

    /** Ethereum-compatible {@code safe} tag (canonical best block), not FAC fork-safe. */
    public boolean isSafeTag() {
        return BlockTag.SAFE.tagEquals(identifier);
    }

    /** RSK FAC fork-safe head relative to the chain tip. */
    public boolean isForkSafeTag() {
        return BlockTag.FORK_SAFE.tagEquals(identifier);
    }

    public static class Deserializer extends StdDeserializer<BlockIdentifierParam> {
        private static final long serialVersionUID = 1L;
        private final ObjectMapper mapper = new ObjectMapper();

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public BlockIdentifierParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            JsonNodeType nodeType = node.getNodeType();

            if (nodeType == JsonNodeType.STRING) {
                return new BlockIdentifierParam(node.asText());
            }
            if (nodeType == JsonNodeType.OBJECT) {
                @SuppressWarnings("unchecked")
                Map<String, String> inputs = mapper.convertValue(node, Map.class);
                return new BlockIdentifierParam(inputs);
            }
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier input");
        }
    }
}
