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

import java.io.IOException;

@JsonDeserialize(using = HexDurationParam.Deserializer.class)
public class HexDurationParam extends HexStringParam {
    private final Long duration;

    public HexDurationParam(String hexDurationStr) {
        super(hexDurationStr);

        if(hexDurationStr.isEmpty()) {
            this.duration = null;
        } else {
            this.duration = Long.parseLong(hexDurationStr.substring(2), 16);
        }
    }

    public Long getDuration() {
        return duration;
    }

    public static class Deserializer extends StdDeserializer<HexDurationParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexDurationParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexDurationStr = jp.getText();
            return new HexDurationParam(hexDurationStr);
        }
    }
}
