/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.Utils;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = BlockIdentifierParam.Deserializer.class)
public class BlockIdentifierParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String identifier;

    public BlockIdentifierParam(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier: empty or null.");
        }

        if(BlockTag.fromString(identifier) == null
            && !Utils.isDecimalString(identifier)
            && !Utils.isHexadecimalString(identifier)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier '" + identifier + "'");
        }

        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static class Deserializer extends StdDeserializer<BlockIdentifierParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public BlockIdentifierParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String identifier = jp.getText();
            return new BlockIdentifierParam(identifier);
        }
    }
}
