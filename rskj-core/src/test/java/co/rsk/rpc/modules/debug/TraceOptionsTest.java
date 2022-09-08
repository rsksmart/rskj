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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TraceOptionsTest {

    @Test
    public void testTraceOptions_allFieldsSetAsDisabled_disabledFieldsShouldReturnAllFields() {
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
    public void testTraceOptions_anyFieldsSetAsDisabled_disabledFieldsShouldReturnEmptySet() {
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
    public void testTraceOptions_someFieldsSetAsDisabled_disabledFieldsShouldReturnDisabledOnes() {
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
    public void testTraceOptions_nullTraceOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnEmptySet() {
        // When
        TraceOptions options = new TraceOptions(null);

        // Then
        Assertions.assertEquals(0, options.getDisabledFields().size());
        Assertions.assertEquals(0, options.getUnsupportedOptions().size());
    }

    @Test
    public void testTraceOptions_emptyTraceOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnEmptySet() {
        // When
        TraceOptions options = new TraceOptions(Collections.emptyMap());

        // Then
        Assertions.assertEquals(0, options.getDisabledFields().size());
        Assertions.assertEquals(0, options.getUnsupportedOptions().size());
    }

    @Test
    public void testTraceOptions_unsupportedOptionsGiven_unsupportedOptionsShouldReturnAllOfThem() {
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
    public void testTraceOptions_mixOfSupportedAndUnsupportedOptionsGiven_disabledFieldsAndUnsupportedOptionsShouldReturnOK() {
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

}
