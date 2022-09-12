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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DetailedProgramTraceProcessorTest {

    private static final String FLD_CONTRACT_ADDRESS = "contractAddress";
    private static final String FLD_INIT_STORAGE = "initStorage";
    private static final String FLD_CURRENT_STORAGE = "currentStorage";
    private static final String FLD_STRUCT_LOGS = "structLogs";
    private static final String FLD_RESULT = "result";
    private static final String FLD_STORAGE_SIZE = "storageSize";

    private static final List<String> EXPECTED_FIELDS = Arrays.asList(
            FLD_CONTRACT_ADDRESS, FLD_INIT_STORAGE, FLD_CURRENT_STORAGE, FLD_STRUCT_LOGS, FLD_RESULT, FLD_STORAGE_SIZE
    );

    @Test
    void getUnknownTrace() {
        Keccak256 hash = TestUtils.randomHash();
        Keccak256 otherHash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DetailedProgramTrace trace = buildTestTrace(DataWord.valueOf(42));
        processor.processProgramTrace(trace, hash);

        Assertions.assertNull(processor.getProgramTrace(otherHash));
        Assertions.assertNull(processor.getProgramTraceAsJsonNode(otherHash));
    }

    @Test
    void getEmptyTrace() {
        Keccak256 hash = TestUtils.randomHash();
        Keccak256 otherHash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DetailedProgramTrace trace = buildTestTrace(DataWord.valueOf(42));
        processor.processProgramTrace(trace, hash);

        JsonNode jsonNode = processor.getProgramTracesAsJsonNode(Collections.singletonList(otherHash));

        Assertions.assertNotNull(jsonNode);
        Assertions.assertTrue(jsonNode.isArray());
        ArrayNode arrNode = (ArrayNode) jsonNode;
        Assertions.assertEquals(0, arrNode.size());
    }

    @Test
    void setAndGetTrace() {
        Keccak256 hash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DataWord ownerDW = DataWord.valueOf(42);
        DetailedProgramTrace trace = buildTestTrace(ownerDW);
        processor.processProgramTrace(trace, hash);

        ProgramTrace result = processor.getProgramTrace(hash);

        Assertions.assertNotNull(result);
        Assertions.assertSame(trace, result);
    }

    @Test
    void setAndGetTraceAsJsonNode() {
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

        Assertions.assertNotNull(jsonNode);
        Assertions.assertEquals(ByteUtil.toHexString(ownerDW.getLast20Bytes()), jsonNode.get(FLD_CONTRACT_ADDRESS).asText());

        String jsonText = jsonNode.toString();
        Assertions.assertNotNull(jsonText);
        EXPECTED_FIELDS.forEach(fld -> Assertions.assertTrue(jsonText.contains("\"" + fld + "\"")));

        jsonNode = processor.getProgramTracesAsJsonNode(Arrays.asList(hash, hash2));

        Assertions.assertNotNull(jsonNode);
        Assertions.assertTrue(jsonNode.isArray());
        ArrayNode arrNode = (ArrayNode) jsonNode;
        Assertions.assertEquals(2, arrNode.size());
        arrNode.forEach(jn -> {
            DataWord owner = ownerDWList.poll();
            Assertions.assertNotNull(owner);
            Assertions.assertNotNull(jn);
            Assertions.assertEquals(ByteUtil.toHexString(owner.getLast20Bytes()), jn.get(FLD_CONTRACT_ADDRESS).asText());

            String jsonStr = jn.toString();
            Assertions.assertNotNull(jsonStr);
            EXPECTED_FIELDS.forEach(fld -> Assertions.assertTrue(jsonStr.contains("\"" + fld + "\"")));
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
