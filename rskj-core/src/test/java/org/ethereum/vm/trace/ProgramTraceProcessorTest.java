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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProgramTraceProcessorTest {
    @Test
    public void getUnknownTrace() {
        Keccak256 hash = TestUtils.randomHash();
        Keccak256 otherHash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        ProgramTrace trace = buildTestTrace(DataWord.valueOf(42));
        processor.processProgramTrace(trace, hash);

        Assert.assertNull(processor.getProgramTraceAsJsonNode(otherHash));
    }

    @Test
    public void setAndGetTraceAsJsonNode() {
        Keccak256 hash = TestUtils.randomHash();
        ProgramTraceProcessor processor = new ProgramTraceProcessor();

        DataWord ownerDW = DataWord.valueOf(42);
        ProgramTrace trace = buildTestTrace(ownerDW);
        processor.processProgramTrace(trace, hash);

        JsonNode jnode = processor.getProgramTraceAsJsonNode(hash);

        Assert.assertNotNull(jnode);
        Assert.assertEquals(Hex.toHexString(ownerDW.getLast20Bytes()), jnode.get("contractAddress").asText());

        String jsonText = jnode.toString();
        Assert.assertNotNull(jsonText);
        Assert.assertTrue(jsonText.contains("\"contractAddress\""));
        Assert.assertTrue(jsonText.contains("\"initStorage\""));
        Assert.assertTrue(jsonText.contains("\"currentStorage\""));
        Assert.assertTrue(jsonText.contains("\"structLogs\""));
        Assert.assertTrue(jsonText.contains("\"result\""));
        Assert.assertTrue(jsonText.contains("\"storageSize\""));
    }

    private ProgramTrace buildTestTrace(DataWord ownerDW) {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);
        when(programInvoke.getOwnerAddress()).thenReturn(ownerDW);
        when(programInvoke.getRepository()).thenReturn(mock(Repository.class));
        VmConfig vmConfig = mock(VmConfig.class);
        when(vmConfig.vmTrace()).thenReturn(true);
        return new ProgramTrace(vmConfig, programInvoke);
    }
}
