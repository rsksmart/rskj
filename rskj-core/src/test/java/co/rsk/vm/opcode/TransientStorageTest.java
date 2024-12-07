/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.vm.opcode;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.vm.BytecodeCompiler;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP446;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransientStorageTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    private VmConfig vmConfig = config.getVmConfig();
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;

    @BeforeEach
    void setup() {
        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @Test
    void testTLoadDynamicExecutionContextUnderflow(){
        //given
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP446)).thenReturn(true);

        //when-then
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCodeWithActivationConfig("TLOAD", 2, activations));
    }

    @Test
    void testTLoadDynamicExecutionContextWorksFine(){
        //given
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP446)).thenReturn(true);
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        //when
        Program program = executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 TLOAD", 2, activations);
        Stack stack = program.getStack();

        //then
        assertEquals(1, stack.size());
        assertEquals(DataWord.valueFromHex(expected), stack.peek());
    }


    @Test
    void testTStoreDynamicExecutionContextUnderflow(){
        //given
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP446)).thenReturn(true);

        //when-then
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCodeWithActivationConfig("TSTORE", 3, activations));
    }


    @Test
    void testTStoreDynamicExecutionContextWorksFine(){
        //given
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP446)).thenReturn(true);
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        //when
        Program program = executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000420 " +
                "PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 " +
                "TSTORE", 3, activations);
        Stack stack = program.getStack();

        //then
        assertEquals(0, stack.size());
        assertNull(program.getResult().getException());
    }

    @Test
    void testTStoreTloadDynamicExecutionContextWorksFine(){
        //given
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP446)).thenReturn(true);
        String expected = "0000000000000000000000000000000000000000000000000000000000000420";

        //when
        Program program = executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000420 " +
                "PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 " +
                "TSTORE " +
                "PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 " +
                "TLOAD ", 5, activations);
        Stack stack = program.getStack();

        //then
        assertEquals(1, stack.size());
        assertEquals(DataWord.valueFromHex(expected), stack.peek());
    }


    private Program executeCodeWithActivationConfig(String code, int nsteps, ActivationConfig.ForBlock activations) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, activations);
    }
    private Program executeCodeWithActivationConfig(byte[] code, int nsteps, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        for (int k = 0; k < nsteps; k++) {
            vm.step(program);
        }

        return program;
    }
}
