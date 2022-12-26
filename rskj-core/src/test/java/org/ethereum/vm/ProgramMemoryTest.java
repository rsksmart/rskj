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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;


@SuppressWarnings("squid:S1607") // many @Disabled annotations for diverse reasons
class ProgramMemoryTest {

    ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
    Program program;

    @BeforeEach
    void createProgram() {
        TestSystemProperties config = new TestSystemProperties();

        program = new Program(config.getVmConfig(), new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache())),
                new BlockFactory(config.getActivationConfig()), mock(ActivationConfig.ForBlock.class),
                ByteUtil.EMPTY_BYTE_ARRAY, pi, null, new HashSet<>(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    @Test
    void testGetMemSize() {
        byte[] memory = new byte[64];
        program.initMem(memory);
        assertEquals(64, program.getMemSize());
    }

    @Test
    @Disabled
    void testMemorySave() {
        fail("Not yet implemented");
    }

    @Test
    @Disabled
    void testMemoryLoad() {
        fail("Not yet implemented");
    }

    @Test
    void testMemoryChunk1() {
        program.initMem(new byte[64]);
        int offset = 128;
        int size = 32;
        program.memoryChunk(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test // size 0 doesn't increase memory
    void testMemoryChunk2() {
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 0;
        program.memoryChunk(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemory1() {

        program.initMem(new byte[64]);
        int offset = 32;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemory2() {

        // memory.limit() > offset, == size
        // memory.limit() < offset + size
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemory3() {

        // memory.limit() > offset, > size
        program.initMem(new byte[64]);
        int offset = 0;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemory4() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemory5() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 0;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemory6() {

        // memory.limit() == offset, > size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemory7() {

        // memory.limit() == offset - size
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(128, program.getMemSize());
    }

    @Test
    void testAllocateMemory8() {

        program.initMem(new byte[64]);
        int offset = 0;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemory9() {

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
    void testAllocateMemory10() {

        // memory = null, offset > size
        int offset = 32;
        int size = 0;
        program.allocateMemory(offset, size);
        assertEquals(0, program.getMemSize());
    }

    @Test
    void testAllocateMemory11() {

        // memory = null, offset < size
        int offset = 0;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Test
    void testAllocateMemory12() {

        // memory.limit() < offset, < size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    void testAllocateMemory13() {

        // memory.limit() > offset, < size
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 128;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    void testAllocateMemory14() {

        // memory.limit() < offset, == size
        program.initMem(new byte[64]);
        int offset = 96;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    void testAllocateMemory15() {

        // memory.limit() == offset, < size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 96;
        program.allocateMemory(offset, size);
        assertEquals(160, program.getMemSize());
    }

    @Test
    void testAllocateMemory16() {

        // memory.limit() == offset, == size
        // memory.limit() > offset - size
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 64;
        program.allocateMemory(offset, size);
        assertEquals(128, program.getMemSize());
    }

    @Test
    void testAllocateMemory17() {

        // memory.limit() > offset + size
        program.initMem(new byte[96]);
        int offset = 32;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded1() {

        // memory unrounded
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded2() {

        // offset unrounded
        program.initMem(new byte[64]);
        int offset = 16;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded3() {

        // size unrounded
        program.initMem(new byte[64]);
        int offset = 64;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(96, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded4() {

        // memory + offset unrounded
        program.initMem(new byte[64]);
        int offset = 16;
        int size = 32;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded5() {

        // memory + size unrounded
        program.initMem(new byte[64]);
        int offset = 32;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(64, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded6() {

        // offset + size unrounded
        program.initMem(new byte[32]);
        int offset = 16;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Test
    void testAllocateMemoryUnrounded7() {

        // memory + offset + size unrounded
        program.initMem(new byte[32]);
        int offset = 16;
        int size = 16;
        program.allocateMemory(offset, size);
        assertEquals(32, program.getMemSize());
    }

    @Disabled
    @Test
    void testInitialInsert() {


        // todo: fix the array out of bound here
        int offset = 32;
        int size = 00;
        program.memorySave(32, 0, new byte[0]);
        assertEquals(32, program.getMemSize());
    }
}
