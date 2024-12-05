/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import co.rsk.rpc.modules.debug.TraceOptions;
import co.rsk.rpc.modules.debug.trace.TracerType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@JsonDeserialize(using = DebugTracerParam.Deserializer.class)
public class DebugTracerParam {
    private final TracerType tracerType;
    private final TraceOptions traceOptions;

    public DebugTracerParam() {
        this(null, null);
    }

    public DebugTracerParam(TracerType tracerType, TraceOptions traceOptions) {
        this.tracerType = tracerType;
        this.traceOptions = traceOptions;
    }

    public TracerType getTracerType() {
        return tracerType;
    }

    public TraceOptions getTraceOptions() {
        return traceOptions;
    }

    public static class Deserializer extends StdDeserializer<DebugTracerParam> {
        @Serial
        private static final long serialVersionUID = 4222943114560623356L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public DebugTracerParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node;
            try {
                node = jp.getCodec().readTree(jp);
            } catch (Exception e) {
                throw new IllegalArgumentException("Can not deserialize parameters", e);
            }
            TracerType tracerType = null;
            TraceOptions traceOptions = new TraceOptions();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if ("tracerConfig".equalsIgnoreCase(entry.getKey())) {
                    JsonNode tracerConfigNode = entry.getValue();
                    tracerConfigNode.fields().forEachRemaining(tracerConfigEntry
                            -> traceOptions.addOption(tracerConfigEntry.getKey(), tracerConfigEntry.getValue().asText()));
                } else if ("tracer".equalsIgnoreCase(entry.getKey())) {
                    tracerType = getTracerType(entry.getValue().asText());
                } else {
                    traceOptions.addOption(entry.getKey(), entry.getValue().asText());
                }
            }
            return new DebugTracerParam(tracerType, traceOptions);
        }

        //TODO check how this exeption is handled
        private TracerType getTracerType(String tracerType) {
            return Optional.ofNullable(TracerType.getTracerType(tracerType)).orElseThrow(()
                    -> new IllegalArgumentException("Invalid tracer type: " + tracerType));
        }
    }
}
