/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.rpc.modules.debug;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.util.*;

@JsonDeserialize(using = TraceOptions.Deserializer.class)
public class TraceOptions {

    private final Set<String> disabledFields;
    private final Set<String> unsupportedOptions;
    private boolean onlyTopCall;
    private boolean withLog;

    public TraceOptions() {
        this.disabledFields = new HashSet<>();
        this.unsupportedOptions = new HashSet<>();
    }

    public TraceOptions(Map<String, String> traceOptions) {
        this();
        if (traceOptions == null) {
            return;
        }
        traceOptions.forEach(this::addOption);
    }

    public final void addOption(String key, String value) {
        switch (key) {
            case "onlyTopCall" -> onlyTopCall = Boolean.parseBoolean(value);
            case "withLog" -> withLog = Boolean.parseBoolean(value);
            default -> addDisableOption(key, value);
        }
    }

    private void addDisableOption(String key, String value) {
        DisableOption disableOption = DisableOption.getDisableOption(key);
        if (disableOption != null) {
            if (Boolean.parseBoolean(value)) {
                disabledFields.add(disableOption.value);
            }
        } else {
            unsupportedOptions.add(key);
        }
    }

    public boolean isOnlyTopCall() {
        return onlyTopCall;
    }

    public boolean isWithLog() {
        return withLog;
    }

    public Set<String> getDisabledFields() {
        return Collections.unmodifiableSet(disabledFields);
    }

    public Set<String> getUnsupportedOptions() {
        return Collections.unmodifiableSet(unsupportedOptions);
    }

    public static class Deserializer extends StdDeserializer<TraceOptions> {
        @Serial
        private static final long serialVersionUID = 4222943114560623356L;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public TraceOptions deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            TraceOptions traceOptions = new TraceOptions();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if ("tracerConfig".equalsIgnoreCase(entry.getKey())) {
                    JsonNode tracerConfigNode = entry.getValue();
                    tracerConfigNode.fields().forEachRemaining(tracerConfigEntry
                            -> traceOptions.addOption(tracerConfigEntry.getKey(), tracerConfigEntry.getValue().asText()));
                }
                traceOptions.addOption(entry.getKey(), entry.getValue().asText());
            }
            return traceOptions;
        }
    }
}
