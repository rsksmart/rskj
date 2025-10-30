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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

class DebugTracerParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testConstructor_withNullValues_shouldInitializeFieldsToNull() {
        // Given
        DebugTracerParam param = new DebugTracerParam(null, null);

        // Then
        Assertions.assertNull(param.getTracerType());
        Assertions.assertNull(param.getTraceOptions());
    }

    @Test
    void testConstructor_withoutValues_shouldInitializeFieldsToNull() {
        // Given
        DebugTracerParam param = new DebugTracerParam();

        // Then
        Assertions.assertNull(param.getTracerType());
        Assertions.assertNull(param.getTraceOptions());
    }

    @Test
    void testConstructor_withNonNullValues_shouldInitializeFieldsCorrectly() {
        // Given
        TraceOptions traceOptions = new TraceOptions(Map.of("disableMemory", "true"));
        TracerType tracerType = TracerType.CALL_TRACER;

        // When
        DebugTracerParam param = new DebugTracerParam(tracerType, traceOptions);

        // Then
        Assertions.assertEquals(tracerType, param.getTracerType());
        Assertions.assertEquals(traceOptions, param.getTraceOptions());
    }

    @Test
    void oldStyleTracerCall_shouldReturnCorrectInstance() throws IOException {
        // Given
        String json = """
                    {
                        "disableMemory": "true",
                        "disableStorage": "false"
                    }
                """;

        // When
        DebugTracerParam param = objectMapper.readValue(json, DebugTracerParam.class);

        // Then
        Assertions.assertNotNull(param);
        Assertions.assertNull(param.getTracerType());
        Assertions.assertNotNull(param.getTraceOptions());
        Assertions.assertTrue(param.getTraceOptions().getDisabledFields().contains("memory"));
        Assertions.assertFalse(param.getTraceOptions().getDisabledFields().contains("storage"));
    }

    @Test
    void callTracerWithConfig_shouldReturnCorrectInstance() throws IOException {
        // Given
        String json = """
                   {
                        "tracer": "callTracer",
                        "tracerConfig": {
                            "onlyTopCall": true,
                            "withLog": true
                        }
                   }
                """;

        // When
        DebugTracerParam param = objectMapper.readValue(json, DebugTracerParam.class);

        // Then
        Assertions.assertNotNull(param);
        Assertions.assertEquals(TracerType.CALL_TRACER, param.getTracerType());
        Assertions.assertNotNull(param.getTraceOptions());
        Assertions.assertTrue(param.getTraceOptions().isWithLog());
        Assertions.assertTrue(param.getTraceOptions().isOnlyTopCall());
    }


    @Test
    void testDeserialize_withUnknownTracerType_shouldThrowException() {
        // Given
        String json = """
                    {
                        "tracer": "unknownTracer"
                    }
                """;

        // Then
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // When
            objectMapper.readValue(json, DebugTracerParam.class);
        });
    }

    @Test
    void testDeserialize_withInvalidTraceOptions_shouldStillDeserialize() throws IOException {
        // Given
        String json = """
                    {
                        "tracer": "callTracer",
                        "unsupportedOption": "true"
                    }
                """;

        // When
        DebugTracerParam param = objectMapper.readValue(json, DebugTracerParam.class);

        // Then
        Assertions.assertNotNull(param);
        Assertions.assertEquals(TracerType.CALL_TRACER, param.getTracerType());
        Assertions.assertNotNull(param.getTraceOptions());
        Assertions.assertTrue(param.getTraceOptions().getUnsupportedOptions().contains("unsupportedOption"));
    }

    @Test
    void testDeserialize_withEmptyJson_shouldReturnDefaultValues() throws IOException {
        // Given
        String json = "{}";

        // When
        DebugTracerParam param = objectMapper.readValue(json, DebugTracerParam.class);

        // Then
        Assertions.assertNotNull(param);
        Assertions.assertNull(param.getTracerType());
        Assertions.assertNotNull(param.getTraceOptions());
        Assertions.assertTrue(param.getTraceOptions().getDisabledFields().isEmpty());
        Assertions.assertTrue(param.getTraceOptions().getUnsupportedOptions().isEmpty());
    }

    @Test
    void testDeserialize_withInvalidJson_shouldThrowException() {
        // Given
        String invalidJson = "{ invalid json }";

        // Then
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // When
            objectMapper.readValue(invalidJson, DebugTracerParam.class);
        });
    }

    @Test
    void testDeserializer_getTracerType_shouldReturnCorrectTracerType() throws IOException {
        // Given
        String json = """
                    {
                        "tracer": "callTracer"
                    }
                """;

        // When
        DebugTracerParam param = objectMapper.readValue(json, DebugTracerParam.class);

        // Then
        Assertions.assertEquals(TracerType.CALL_TRACER, param.getTracerType());
    }

    @Test
    void testDeserializer_getTracerType_withNull_shouldThrowException() {
        // Given
        String json = """
                    {
                        "tracer": null
                    }
                """;

        // Then
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // When
            objectMapper.readValue(json, DebugTracerParam.class);
        });
    }
}
