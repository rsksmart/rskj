/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@JsonDeserialize(using = AccountOverrideParam.Deserializer.class)
public record AccountOverrideParam(HexNumberParam balance, HexNumberParam nonce, HexDataParam code,
                                   Map<HexDataParam, HexDataParam> state, Map<HexDataParam, HexDataParam> stateDiff,
                                   HexAddressParam movePrecompileToAddress) {

    public static class Deserializer extends StdDeserializer<AccountOverrideParam> {

        @Serial
        private static final long serialVersionUID = 1L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public AccountOverrideParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);

            HexNumberParam balance = paramOrNull(node, "balance", HexNumberParam::new);
            HexNumberParam nonce = paramOrNull(node, "nonce", HexNumberParam::new);
            HexDataParam code = paramOrNull(node, "code", HexDataParam::new);
            Map<HexDataParam, HexDataParam> state = getDataMap("state", node);
            Map<HexDataParam, HexDataParam> stateDiff = getDataMap("stateDiff", node);
            HexAddressParam movePrecompileToAddress = paramOrNull(node, "movePrecompileToAddress", HexAddressParam::new);
            return new AccountOverrideParam(balance, nonce, code, state, stateDiff, movePrecompileToAddress);
        }

        public Map<HexDataParam, HexDataParam> getDataMap(String paramName, JsonNode node) {
            JsonNode jsonMap = node.get(paramName);
            if (jsonMap == null || jsonMap.isNull()) {
                return null;
            }
            Map<HexDataParam, HexDataParam> map = new HashMap<>();
            jsonMap.fields().forEachRemaining(entry -> {
                HexDataParam key = new HexDataParam(entry.getKey());
                HexDataParam value = new HexDataParam(entry.getValue().asText());
                map.put(key, value);
            });
            return map;
        }

        @Nullable
        private static <T> T paramOrNull(JsonNode node, String fieldName, Function<String, T> paramFactory) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode == null || fieldNode.isNull()) {
                return null;
            }

            return paramFactory.apply(fieldNode.asText());
        }
    }

}
