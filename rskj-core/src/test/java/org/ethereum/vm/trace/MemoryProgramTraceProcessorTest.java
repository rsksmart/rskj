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

import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by ajlopez on 16/04/2019.
 */
public class MemoryProgramTraceProcessorTest {
    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void enableTrace() {
        MemoryProgramTraceProcessor processor = new MemoryProgramTraceProcessor(true);

        Assert.assertTrue(processor.enabled());
    }

    @Test
    public void disableTrace() {
        MemoryProgramTraceProcessor processor = new MemoryProgramTraceProcessor(false);

        Assert.assertFalse(processor.enabled());
    }

    @Test
    public void getUnknownTrace() {
        Keccak256 hash = TestUtils.randomHash();
        MemoryProgramTraceProcessor processor = new MemoryProgramTraceProcessor(true);

        Assert.assertNull(processor.getProgramTrace(hash));
    }

    @Test
    public void setAndGetTrace() throws IOException {
        Keccak256 hash = TestUtils.randomHash();
        MemoryProgramTraceProcessor processor = new MemoryProgramTraceProcessor(true);

        ProgramTrace trace = new ProgramTrace(config.getVmConfig(), null);
        processor.processProgramTrace(trace, hash);
        Assert.assertEquals(trace, processor.getProgramTrace(hash));
    }

    @Test
    public void setAndGetTraceAsJsonNode() throws IOException {
        Keccak256 hash = TestUtils.randomHash();
        MemoryProgramTraceProcessor processor = new MemoryProgramTraceProcessor(true);

        ProgramTrace trace = new ProgramTrace(config.getVmConfig(), null);
        processor.processProgramTrace(trace, hash);

        JsonNode jnode = processor.getProgramTraceAsJsonNode(hash);
        Assert.assertNotNull(jnode);

        String jsonText = jnode.toString();

        Assert.assertNotNull(jsonText);
        Assert.assertTrue(jsonText.contains("\"contractAddress\""));
        Assert.assertTrue(jsonText.contains("\"initStorage\""));
        Assert.assertTrue(jsonText.contains("\"currentStorage\""));
        Assert.assertTrue(jsonText.contains("\"structLogs\""));
        Assert.assertTrue(jsonText.contains("\"result\""));
        Assert.assertTrue(jsonText.contains("\"storageSize\""));
    }
}
