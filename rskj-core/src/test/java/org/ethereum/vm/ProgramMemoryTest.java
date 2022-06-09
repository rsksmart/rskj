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

package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.peg.utils.PegUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class ProgramMemoryTest {

    ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
    Program program;

    private final PegUtils pegUtils = PegUtils.getInstance();

    @Before
    public void createProgram() {
        TestSystemProperties config = new TestSystemProperties();

        program = new Program(config.getVmConfig(), new PrecompiledContracts(config, null, pegUtils),
                new BlockFactory(config.getActivationConfig()), mock(ActivationConfig.ForBlock.class),
                ByteUtil.EMPTY_BYTE_ARRAY, pi, null, new HashSet<>());
    }

    @Test
    public void testGetMemSize() {
        byte[] memory = new byte[64];
        program.initMem(memory);
        assertEquals(64, program.getMemSize());
    }

    @Test
    @Ignore
    public void testMemorySave() {
        fail("Not yet implemented");
    }

    @Test
    @Ignore
    public void testMemoryLoad() {
        fail("Not yet implemented");
    }

    @Test
    public void testMemoryChunk1() {
        program.initMem(new byte[64]);
        int offset = 128;
        int size = 32;
        program.memoryChunk(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test // size 0 doesn't increase memory
    public void testMemoryChunk2() {
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 0;
        program.memoryChunk(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemory1() {

        program.initMem(new byte[64]);
        int offset = 32;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemory2() {

        // memory.limit() > offset, == size
        // memory.limit() < offset + size
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemory3() {

        // memory.limit() > offset, > size
        program.initMem(new byte[64]);
        int offset = 0;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemory4() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemory5() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 0;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemory6() {

        // memory.limit() == offset, > size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemory7() {

        // memory.limit() == offset - size
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(128, program.getMemSize());
    }

    @Test
    public void testAllocateMemory8() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemory9() {

        // memory.limit() < offset, > size
        // memory.limit() < offset - size
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 0;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    /************************************************/


    @Test
    public void testAllocateMemory10() {

        // memory = null, offset > size
        int offset = 32;
        int size = 0;
        program.allocateMemory(offset, size);
        assertEquals(0, program.getMemSize());
    }

    @Test
    public void testAllocateMemory11() {

        // memory = null, offset < size
        int offset = 0;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Test
    public void testAllocateMemory12() {

        // memory.limit() < offset, < size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    public void testAllocateMemory13() {

        // memory.limit() > offset, < size
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 128;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    public void testAllocateMemory14() {

        // memory.limit() < offset, == size
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    public void testAllocateMemory15() {

        // memory.limit() == offset, < size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    public void testAllocateMemory16() {

        // memory.limit() == offset, == size
        // memory.limit() > offset - size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(128, program.getMemSize());
    }

    @Test
    public void testAllocateMemory17() {

        // memory.limit() > offset + size
        program.initMem(new byte[96]);
        int offset = 32;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded1() {

        // memory unrounded
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded2() {

        // offset unrounded
        program.initMem(new byte[64]);
        int offset = 16;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded3() {

        // size unrounded
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded4() {

        // memory + offset unrounded
        program.initMem(new byte[64]);
        int offset = 16;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded5() {

        // memory + size unrounded
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded6() {

        // offset + size unrounded
        program.initMem(new byte[32]);
        int offset = 16;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Test
    public void testAllocateMemoryUnrounded7() {

        // memory + offset + size unrounded
        program.initMem(new byte[32]);
        int offset = 16;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Ignore
    @Test
    public void testInitialInsert() {


        // todo: fix the array out of bound here
        int offset = 32;
        int size = 00;
        program.memorySave(32, 0, new byte[0]);
        assertEquals(32, program.getMemSize());
    }
}
