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

import co.rsk.config.VmConfig;
import co.rsk.crypto.Keccak256;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DetailedProgramTraceProcessorTest {

    @Test
    public void getUnknownTrace() {
        Keccak256 hash = TestUtils.randomHash();
        Keccak256 otherHash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DetailedProgramTrace trace = buildTestTrace(DataWord.valueOf(42));
        processor.processProgramTrace(trace, hash);

        Assert.assertNull(processor.getProgramTrace(otherHash));
        Assert.assertNull(processor.getProgramTraceAsJsonNode(otherHash));
    }

    @Test
    public void getEmptyTrace() {
        Keccak256 hash = TestUtils.randomHash();
        Keccak256 otherHash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DetailedProgramTrace trace = buildTestTrace(DataWord.valueOf(42));
        processor.processProgramTrace(trace, hash);

        JsonNode jsonNode = processor.getProgramTracesAsJsonNode(Collections.singletonList(otherHash));

        Assert.assertNotNull(jsonNode);
        Assert.assertTrue(jsonNode.isArray());
        ArrayNode arrNode = (ArrayNode) jsonNode;
        Assert.assertEquals(0, arrNode.size());
    }

    @Test
    public void setAndGetTrace() {
        Keccak256 hash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DataWord ownerDW = DataWord.valueOf(42);
        DetailedProgramTrace trace = buildTestTrace(ownerDW);
        processor.processProgramTrace(trace, hash);

        ProgramTrace result = processor.getProgramTrace(hash);

        Assert.assertNotNull(result);
        Assert.assertSame(trace, result);
    }

    @Test
    public void setAndGetTraceAsJsonNode() {
        Keccak256 hash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DataWord ownerDW = DataWord.valueOf(42);
        DetailedProgramTrace trace = buildTestTrace(ownerDW);
        processor.processProgramTrace(trace, hash);

        Keccak256 hash2 = TestUtils.randomHash();
        DataWord ownerDW2 = DataWord.valueOf(42);
        DetailedProgramTrace trace2 = buildTestTrace(ownerDW2);
        processor.processProgramTrace(trace2, hash2);

        LinkedList<DataWord> ownerDWList = new LinkedList<>();
        ownerDWList.add(ownerDW);
        ownerDWList.add(ownerDW2);

        JsonNode jsonNode = processor.getProgramTraceAsJsonNode(hash);

        Assert.assertNotNull(jsonNode);
        Assert.assertEquals(ByteUtil.toHexString(ownerDW.getLast20Bytes()), jsonNode.get("contractAddress").asText());

        String jsonText = jsonNode.toString();
        Assert.assertNotNull(jsonText);
        Assert.assertTrue(jsonText.contains("\"contractAddress\""));
        Assert.assertTrue(jsonText.contains("\"initStorage\""));
        Assert.assertTrue(jsonText.contains("\"currentStorage\""));
        Assert.assertTrue(jsonText.contains("\"structLogs\""));
        Assert.assertTrue(jsonText.contains("\"result\""));
        Assert.assertTrue(jsonText.contains("\"storageSize\""));

        jsonNode = processor.getProgramTracesAsJsonNode(Arrays.asList(hash, hash2));

        Assert.assertNotNull(jsonNode);
        Assert.assertTrue(jsonNode.isArray());
        ArrayNode arrNode = (ArrayNode) jsonNode;
        Assert.assertEquals(2, arrNode.size());
        arrNode.forEach(jn -> {
            DataWord owner = ownerDWList.poll();
            Assert.assertNotNull(owner);
            Assert.assertNotNull(jn);
            Assert.assertEquals(ByteUtil.toHexString(owner.getLast20Bytes()), jn.get("contractAddress").asText());

            String jsonStr = jn.toString();
            Assert.assertNotNull(jsonStr);
            Assert.assertTrue(jsonStr.contains("\"contractAddress\""));
            Assert.assertTrue(jsonStr.contains("\"initStorage\""));
            Assert.assertTrue(jsonStr.contains("\"currentStorage\""));
            Assert.assertTrue(jsonStr.contains("\"structLogs\""));
            Assert.assertTrue(jsonStr.contains("\"result\""));
            Assert.assertTrue(jsonStr.contains("\"storageSize\""));
        });
    }

    private DetailedProgramTrace buildTestTrace(DataWord ownerDW) {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);
        when(programInvoke.getOwnerAddress()).thenReturn(ownerDW);
        when(programInvoke.getRepository()).thenReturn(mock(Repository.class));
        VmConfig vmConfig = mock(VmConfig.class);
        when(vmConfig.vmTrace()).thenReturn(true);
        return new DetailedProgramTrace(vmConfig, programInvoke);
    }
}
