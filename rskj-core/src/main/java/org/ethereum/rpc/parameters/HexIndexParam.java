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

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexIndexParam.Deserializer.class)
public class HexIndexParam extends HexStringParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Integer index;

    public HexIndexParam(String indexString) {
        super(indexString);

        String preResult = HexUtils.removeHexPrefix(indexString);
        this.index = Integer.parseInt(preResult, 16);
    }

    public Integer getIndex() {
        return index;
    }

    public static class Deserializer extends StdDeserializer<HexIndexParam> {

        private static final long serialVersionUID = 1L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public HexIndexParam deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {
            String indexString = jp.getText();
            return new HexIndexParam(indexString);
        }
    }
}
