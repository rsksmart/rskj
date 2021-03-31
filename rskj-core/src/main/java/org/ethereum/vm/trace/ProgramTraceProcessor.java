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

package org.ethereum.vm.trace;

import co.rsk.crypto.Keccak256;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides tracing and exporting to JSON
 */
public class ProgramTraceProcessor {
    private final Map<Keccak256, ProgramTrace> traces = new HashMap<>();

    public void processProgramTrace(ProgramTrace programTrace, Keccak256 txHash) {
        this.traces.put(txHash, programTrace);
    }

    public ProgramTrace getProgramTrace(Keccak256 txHash) {
        return traces.get(txHash);
    }

    public JsonNode getProgramTraceAsJsonNode(Keccak256 txHash) {
        ProgramTrace trace = traces.get(txHash);

        if (trace == null) {
            return null;
        }

        ObjectMapper mapper = Serializers.createMapper(true);
        mapper.setVisibility(fieldsOnlyVisibilityChecker(mapper));

        return mapper.valueToTree(trace);
    }

    private static VisibilityChecker<?> fieldsOnlyVisibilityChecker(ObjectMapper mapper) {
        return mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE);
    }
}
