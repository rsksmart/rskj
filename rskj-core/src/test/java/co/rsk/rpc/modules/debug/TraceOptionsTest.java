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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class TraceOptionsTest {

    @Test
    void testTraceOptions_allFieldsSetAsDisabled_disabledFieldsShouldReturnAllFields() {
        // Given
        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableStorage", "true");
        traceOptions.put("disableMemory", "true");
        traceOptions.put("disableStack", "true");

        // When
        TraceOptions options = new TraceOptions(traceOptions);

        // Then
        Assertions.assertEquals(3, options.getDisabledFields().size());
        Assertions.assertTrue(options.getDisabledFields().contains("storage"));
        Assertions.assertTrue(options.getDisabledFields().contains("memory"));
        Assertions.assertTrue(options.getDisabledFields().contains("stack"));
    }

    @Test
    void testTraceOptions_anyFieldsSetAsDisabled_disabledFieldsShouldReturnEmptySet() {
        // Given
        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableStorages", "false");
        traceOptions.put("disablesMemory", "false");
        traceOptions.put("disableStack", "false");

        // When
        TraceOptions options = new TraceOptions(traceOptions);

        // Then
        Assertions.assertEquals(0, options.getDisabledFields().size());
        Assertions.assertFalse(options.getDisabledFields().contains("storage"));
        Assertions.assertFalse(options.getDisabledFields().contains("memory"));
        Assertions.assertFalse(options.getDisabledFields().contains("stack"));
    }

    @Test
    void testTraceOptions_someFieldsSetAsDisabled_disabledFieldsShouldReturnDisabledOnes() {
        // Given
        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableStorage", "true");
        traceOptions.put("disableMemory", "false");
        traceOptions.put("disableStack", "true");

        // When
        TraceOptions options = new TraceOptions(traceOptions);

        // Then
        Assertions.assertEquals(2, options.getDisabledFields().size());
        Assertions.assertTrue(options.getDisabledFields().contains("storage"));
        Assertions.assertFalse(options.getDisabledFields().contains("memory"));
        Assertions.assertTrue(options.getDisabledFields().contains("stack"));
    }

    @Test
    void testTraceOptions_nullTraceOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnEmptySet() {
        // When
        TraceOptions options = new TraceOptions(null);

        // Then
        Assertions.assertEquals(0, options.getDisabledFields().size());
        Assertions.assertEquals(0, options.getUnsupportedOptions().size());
    }

    @Test
    void testTraceOptions_emptyTraceOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnEmptySet() {
        // When
        TraceOptions options = new TraceOptions(Collections.emptyMap());

        // Then
        Assertions.assertEquals(0, options.getDisabledFields().size());
        Assertions.assertEquals(0, options.getUnsupportedOptions().size());
    }

    @Test
    void testTraceOptions_unsupportedOptionsGiven_unsupportedOptionsShouldReturnAllOfThem() {
        // Given
        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("unsupportedOption.1", "1");
        traceOptions.put("unsupportedOption.2", null);

        // When
        TraceOptions options = new TraceOptions(traceOptions);

        // Then
        Assertions.assertEquals(2, options.getUnsupportedOptions().size());
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption.1"));
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption.2"));
    }

    @Test
    void testTraceOptions_mixOfSupportedAndUnsupportedOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnOK() {
        // Given
        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableMemory", "true");
        traceOptions.put("disableStorage", "True"); // True != true but should also work
        traceOptions.put("unsupportedOption.1", "1");
        traceOptions.put("unsupportedOption.2", null);

        // When
        TraceOptions options = new TraceOptions(traceOptions);

        // Then
        Assertions.assertEquals(2, options.getDisabledFields().size());
        Assertions.assertTrue(options.getDisabledFields().contains("storage"));
        Assertions.assertTrue(options.getDisabledFields().contains("memory"));

        Assertions.assertEquals(2, options.getUnsupportedOptions().size());
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption.1"));
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption.2"));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserialize_withValidJson_shouldSetAllFields() throws IOException {
        // Given
        String json = """
            {
                "onlyTopCall": "true",
                "withLog": "false",
                "disableMemory": "true",
                "disableStack": "false",
                "disableStorage": "true"
            }
        """;

        // When
        TraceOptions options = objectMapper.readValue(json, TraceOptions.class);

        // Then
        Assertions.assertTrue(options.isOnlyTopCall());
        Assertions.assertFalse(options.isWithLog());
        Assertions.assertTrue(options.getDisabledFields().contains("memory"));
        Assertions.assertFalse(options.getDisabledFields().contains("stack"));
        Assertions.assertTrue(options.getDisabledFields().contains("storage"));
    }

    @Test
    void testDeserialize_withUnsupportedOptions_shouldAddToUnsupported() throws IOException {
        // Given
        String json = """
            {
                "onlyTopCall": "true",
                "withLog": "true",
                "unsupportedOption1": "true",
                "unsupportedOption2": "false"
            }
        """;

        // When
        TraceOptions options = objectMapper.readValue(json, TraceOptions.class);

        // Then
        Assertions.assertTrue(options.isOnlyTopCall());
        Assertions.assertTrue(options.isWithLog());
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption1"));
        Assertions.assertTrue(options.getUnsupportedOptions().contains("unsupportedOption2"));
    }

    @Test
    void testDeserialize_withTracerConfig_shouldHandleNestedOptions() throws IOException {
        // Given
        String json = """
            {
                "tracerConfig": {
                    "disableMemory": "true",
                    "disableStack": "true"
                },
                "withLog": "false"
            }
        """;

        // When
        TraceOptions options = objectMapper.readValue(json, TraceOptions.class);

        // Then
        Assertions.assertFalse(options.isWithLog());
        Assertions.assertTrue(options.getDisabledFields().contains("memory"));
        Assertions.assertTrue(options.getDisabledFields().contains("stack"));
    }

    @Test
    void testDeserialize_withEmptyJson_shouldReturnDefaultValues() throws IOException {
        // Given
        String json = "{}";

        // When
        TraceOptions options = objectMapper.readValue(json, TraceOptions.class);

        // Then
        Assertions.assertFalse(options.isOnlyTopCall());
        Assertions.assertFalse(options.isWithLog());
        Assertions.assertTrue(options.getDisabledFields().isEmpty());
        Assertions.assertTrue(options.getUnsupportedOptions().isEmpty());
    }

    @Test
    void testDeserialize_withInvalidJson_shouldThrowException() {
        // Given
        String invalidJson = "{ invalid json }";

        // Then
        Assertions.assertThrows(IOException.class, () -> {
            // When
            objectMapper.readValue(invalidJson, TraceOptions.class);
        });
    }

    @Test
    void testDeserialize_withConflictingOptions_shouldResolveCorrectly() throws IOException {
        // Given
        String json = """
            {
                "onlyTopCall": "true",
                "withLog": "true",
                "disableMemory": "true",
                "disableMemory": "false"
            }
        """;

        // When
        TraceOptions options = objectMapper.readValue(json, TraceOptions.class);

        // Then
        Assertions.assertTrue(options.isOnlyTopCall());
        Assertions.assertTrue(options.isWithLog());
        // Last occurrence of conflicting key takes precedence
        Assertions.assertFalse(options.getDisabledFields().contains("memory"));
    }

}
