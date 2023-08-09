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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

@JsonDeserialize(using = TxHashParam.Deserializer.class)
public class TxHashParam extends HashParam32 {
    private static final String HASH_TYPE = "transaction hash";
    public TxHashParam(String hash) {
        super(HASH_TYPE, hash);
    }

    public static class Deserializer extends JsonDeserializer<TxHashParam> {

        @Override
        public TxHashParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hash = jp.getText();
            return new TxHashParam(hash);

        }
    }
}

