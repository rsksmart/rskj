/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.*;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class VMExecutionTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private VmConfig vmConfig = config.getVmConfig();
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;

    @Before
    public void setup() {
        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @Test
    public void testPush1() {
        Program program = executeCode("PUSH1 0xa0", 1);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(0xa0), stack.peek());
    }

    @Test
    public void testAdd() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 ADD", 3);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(3), stack.peek());
    }

    @Test
    public void testMul() {
        Program program = executeCode("PUSH1 0x03 PUSH1 0x02 MUL", 3);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(6), stack.peek());
    }

    @Test
    public void testSub() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 SUB", 3);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
    }

    private void executeShift(String number, String shiftAmount, String expect, String op , ActivationConfig.ForBlock activations){
        Program program = executeCode("PUSH32 "+number+" PUSH1 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    public void testSHL1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHL",
                activations);
    }

    @Test
    public void testSHL2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000002",
                "SHL",
                activations);
    }

    @Test
    public void testSHL3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0xff",
                "8000000000000000000000000000000000000000000000000000000000000000",
                "SHL",
                activations);
    }

    @Test
    public void testSHL4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String shiftAmount = "0x0100";
        String op = "SHL";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCode("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    public void testSHL5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SHL",
                activations);
    }

    @Test
    public void testSHL6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xff",
                "8000000000000000000000000000000000000000000000000000000000000000",
                "SHL",
                activations);
    }

    @Test
    public void testSHL7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x01",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
                "SHL",
                activations);
    }

    @Test
    public void testSHR1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }

    @Test
    public void testSHR2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "SHR",
                activations);
    }

    @Test
    public void testSHR3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0x01",
                "4000000000000000000000000000000000000000000000000000000000000000",
                "SHR",
                activations);
    }

    @Test
    public void testSHR4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0xff",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }


    @Test
    public void testSHR5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SHR",
                activations);
    }

    @Test
    public void testSHR6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xff",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }

    @Test
    public void testSHR7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x8000000000000000000000000000000000000000000000000000000000000000";
        String shiftAmount = "0x0100";
        String op = "SHR";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCode("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    public void testSAR1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SAR",
                activations);
    }


    @Test
    public void testSAR2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "SAR",
                activations);
    }

    @Test
    public void testSAR3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0x01",
                "c000000000000000000000000000000000000000000000000000000000000000",
                "SAR",
                activations);
    }

    @Test
    public void testSAR4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0xff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    public void testSAR5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    public void testSAR6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x01",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    public void testSAR7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xf8",
                "000000000000000000000000000000000000000000000000000000000000007f",
                "SAR",
                activations);
    }

    @Test
    public void testSAR8() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        String shiftAmount = "0x0100";
        String op = "SAR";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCode("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }


    @Test(expected = Program.IllegalOperationException.class)
    public void testSAR3ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);
        executeCode("PUSH32 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff PUSH1 0xff SAR", 3, activations);
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void testSHL1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        executeCode("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHL", 3, activations);
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void testSHR1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        executeCode("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHR", 3, activations);
    }

    @Test
    public void testJumpSkippingInvalidJump() {
        Program program = executeCode("PUSH1 0x05 JUMP PUSH1 0xa0 JUMPDEST PUSH1 0x01", 4);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
    }

    @Test
    public void dupnFirstItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x00 DUPN", 3);
        Stack stack = program.getStack();

        Assert.assertEquals(2, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
        Assert.assertEquals(DataWord.valueOf(1), stack.get(0));
    }

    @Test
    public void dupnFourthItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x03 DUPN", 6);
        Stack stack = program.getStack();

        Assert.assertEquals(5, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());

        for (int k = 0; k < 4; k++)
            Assert.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test
    public void dupnTwentiethItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 PUSH1 0x13 DUPN", 22);
        Stack stack = program.getStack();

        Assert.assertEquals(21, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());

        for (int k = 0; k < 20; k++)
            Assert.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test(expected = Program.StackTooSmallException.class)
    public void dupnTwentiethItemWithoutEnoughItems() {
        executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x13 DUPN", 21);
    }

    @Test(expected = Program.StackTooSmallException.class)
    public void dupnTooManyItemsWithOverflow() {
        executeCode("PUSH1 0x01 PUSH4 0x7f 0xff 0xff 0xff DUPN", 3);
    }

    @Test
    public void swapnSecondItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 SWAPN", 4);
        Stack stack = program.getStack();

        Assert.assertEquals(2, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
        Assert.assertEquals(DataWord.valueOf(2), stack.get(0));
    }

    @Test
    public void swapnFourthItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x02 SWAPN", 6);
        Stack stack = program.getStack();

        Assert.assertEquals(4, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
        Assert.assertEquals(DataWord.valueOf(4), stack.get(0));
        Assert.assertEquals(DataWord.valueOf(2), stack.get(1));
        Assert.assertEquals(DataWord.valueOf(3), stack.get(2));
    }

    @Test
    public void swapnTwentiethItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 PUSH1 0x12 SWAPN", 22);
        Stack stack = program.getStack();

        Assert.assertEquals(20, stack.size());
        Assert.assertEquals(DataWord.valueOf(1), stack.peek());
        Assert.assertEquals(DataWord.valueOf(20), stack.get(0));

        for (int k = 1; k < 19; k++)
            Assert.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test(expected = Program.StackTooSmallException.class)
    public void swapnTooManyItemsWithOverflow() {
        executeCode("PUSH1 0x01 PUSH1 0x01 PUSH4 0x7f 0xff 0xff 0xff SWAPN", 4);
    }

    @Test(expected = Program.StackTooSmallException.class)
    public void swapnTwentiethItemWithoutEnoughItems() {
        executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x12 SWAPN", 22);
    }

    @Test
    public void txIndexExecution() {
        invoke.setTransactionIndex(DataWord.valueOf(42));
        Program program = executeCode("TXINDEX", 1);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(42), stack.peek());
    }

    @Test
    public void invalidJustAfterEndOfCode() {
        try {
            executeCode("PUSH1 0x03 JUMP", 2);
            Assert.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[3], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    public void invalidJumpOutOfRange() {
        try {
            executeCode("PUSH1 0x05 JUMP", 2);
            Assert.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[5], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    public void invalidNegativeJump() {
        try {
            executeCode("PUSH32 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff JUMP", 2);
            Assert.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[-1], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    public void invalidTooFarJump() {
        try {
            executeCode("PUSH1 0xff JUMP", 2);
            Assert.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[255], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    public void invalidJumpUsingPlayCode() {
        Program program = playCode("PUSH1 0x03 JUMP");

        ProgramResult programResult = program.getResult();

        Assert.assertNotNull(programResult);
        Assert.assertNotNull(programResult.getException());
        Assert.assertEquals(invoke.getGas(), programResult.getGasUsed());
    }

    @Test
    public void dupnArgumentIsNotJumpdest() {
        byte[] code = compiler.compile("JUMPDEST DUPN 0x5b 0x5b");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null, new HashSet<>());

        BitSet jumpdestSet = program.getJumpdestSet();

        Assert.assertNotNull(jumpdestSet);
        Assert.assertEquals(4, jumpdestSet.size());
        Assert.assertTrue(jumpdestSet.get(0));
        Assert.assertFalse(jumpdestSet.get(1));
        Assert.assertTrue(jumpdestSet.get(2));
        Assert.assertTrue(jumpdestSet.get(3));
    }

    @Test
    public void swapnArgumentIsNotJumpdest() {
        byte[] code = compiler.compile("JUMPDEST SWAPN 0x5b 0x5b");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null, new HashSet<>());

        BitSet jumpdestSet = program.getJumpdestSet();

        Assert.assertNotNull(jumpdestSet);
        Assert.assertEquals(4, jumpdestSet.size());
        Assert.assertTrue(jumpdestSet.get(0));
        Assert.assertFalse(jumpdestSet.get(1));
        Assert.assertTrue(jumpdestSet.get(2));
        Assert.assertTrue(jumpdestSet.get(3));
    }

    @Test
    public void thePathOfFifteenThousandJumps() {
        byte[] bytecode = new byte[15000 * 6 + 3];

        int k = 0;

        while (k < 15000 * 6) {
            int target = k + 6;
            bytecode[k++] = 0x5b; // JUMPDEST
            bytecode[k++] = 0x62; // PUSH3
            bytecode[k++] = (byte)(target >> 16);
            bytecode[k++] = (byte)(target >> 8);
            bytecode[k++] = (byte)(target & 0xff);
            bytecode[k++] = 0x56; // JUMP
        }

        bytecode[k++] = 0x5b; // JUMPDEST
        bytecode[k++] = 0x60; // PUSH1
        bytecode[k++] = 0x01; // 1

        ThreadMXBean thread = ManagementFactory.getThreadMXBean();

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        long initialTime = thread.getCurrentThreadCpuTime();
        testCode(bytecode, 15000 * 3 + 2, "0000000000000000000000000000000000000000000000000000000000000001");
        long finalTime = thread.getCurrentThreadCpuTime();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();

        System.out.println(String.format("Execution Time %s nanoseconds", finalTime - initialTime));
        System.out.println(String.format("Delta memory %s", finalMemory - initialMemory));
    }

    @Test
    public void returnDataSizeBasicGasCost() {
        Program program = executeCode("0x3d", 1);

        Assert.assertNotNull(program);
        Assert.assertNotNull(program.getResult());
        Assert.assertNull(program.getResult().getException());
        Assert.assertEquals(2, program.getResult().getGasUsed());
    }

    @Test
    public void returnDataCopyBasicGasCost() {
        Program program = executeCode(
                // push some values for RETURNDATACOPY
                "PUSH1 0x00 PUSH1 0x00 PUSH1 0x01 " +
                // call RETURNDATACOPY
                "0x3e",
        4);

        Assert.assertNotNull(program);
        Assert.assertNotNull(program.getResult());
        Assert.assertNull(program.getResult().getException());
        Assert.assertEquals(12, program.getResult().getGasUsed());
    }

    @Test
    public void callDataCopyBasicGasCost() {
        Program program = executeCode(
                // push some values for CALLDATACOPY
                "PUSH1 0x00 PUSH1 0x00 PUSH1 0x01 " +
                // call CALLDATACOPY
                "0x37",
        4);

        Assert.assertNotNull(program);
        Assert.assertNotNull(program.getResult());
        Assert.assertNull(program.getResult().getException());
        Assert.assertEquals(12, program.getResult().getGasUsed());
    }

    @Test
    public void chainIDMainnet(){
        executeCHAINID(Constants.MAINNET_CHAIN_ID);
    }

    @Test
    public void chainIDTestnet(){
        executeCHAINID(Constants.TESTNET_CHAIN_ID);
    }

    @Test
    public void chainIDRegtest(){
        executeCHAINID(Constants.REGTEST_CHAIN_ID);
    }

    @Test
    public void chainIDIsCorrectOpcodeNumber(){
        Assert.assertEquals(0x46, OpCodes.OP_CHAINID);
        Assert.assertEquals(OpCode.CHAINID, OpCode.code((byte) 0x46));
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void testChainIDNotActivated(){
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP152)).thenReturn(false);

        executeCHAINIDWithActivations(Constants.REGTEST_CHAIN_ID, activations);
    }

    private void executeCHAINID(byte chainIDExpected) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP152)).thenReturn(true);

        executeCHAINIDWithActivations(chainIDExpected, activations);
    }

    private void executeCHAINIDWithActivations(byte chainIDExpected, ActivationConfig.ForBlock activations) {
        vmConfig = mock(VmConfig.class);
        when(vmConfig.getChainId()).thenReturn(chainIDExpected);

        Program program = executeCode("CHAINID", 1, activations);

        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(chainIDExpected), stack.peek());
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void selfBalanceFailsWithRSKIPNotActivated() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(false);

        executeCode("SELFBALANCE", 1, activations);
    }

    @Test
    public void selfBalanceRunsCorrectly() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(true);

        int balanceValue = 100;

        RskAddress testAddress = new RskAddress(invoke.getCallerAddress());
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(balanceValue));

        Program program = executeCode("SELFBALANCE", 1, activations);
        Stack stack = program.getStack();

        long selfBalanceGas = OpCode.SELFBALANCE.getTier().asInt();

        Assert.assertEquals(selfBalanceGas, program.getResult().getGasUsed());
        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(balanceValue), stack.peek());
    }

    @Test
    public void selfBalanceIsSameAsBalance() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(true);

        int balanceValue = 100;

        RskAddress testAddress = new RskAddress(invoke.getCallerAddress());
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(balanceValue));

        Program programSelfBalance = executeCode("SELFBALANCE",1, activations);
        Stack stackSelfBalance = programSelfBalance.getStack();

        Program programBalance = executeCode("PUSH20 0x" + testAddress.toHexString() +
                " BALANCE", 2, activations);
        Stack stackBalance = programBalance.getStack();

        Assert.assertEquals(1, stackSelfBalance.size());

        DataWord selfBalance = stackSelfBalance.pop();
        DataWord balance = stackBalance.pop();

        long selfBalanceGas = OpCode.SELFBALANCE.getTier().asInt();

        Assert.assertEquals(selfBalanceGas, programSelfBalance.getResult().getGasUsed());
        Assert.assertEquals(selfBalance, DataWord.valueOf(balanceValue));
        Assert.assertEquals(balance, selfBalance);
    }

    @Test
    public void invalidBeginsubOpcodeWhenNotActivated() {
        Program program = playCode("BEGINSUB");

        ProgramResult result = program.getResult();

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getException());
        Assert.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assert.assertEquals("Invalid operation code: opcode[5c], tx[<null>]", result.getException().getMessage());
    }

    @Test
    public void invalidReturnsubOpcodeWhenNotActivated() {
        Program program = playCode("RETURNSUB");

        ProgramResult result = program.getResult();

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getException());
        Assert.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assert.assertEquals("Invalid operation code: opcode[5d], tx[<null>]", result.getException().getMessage());
    }

    @Test
    public void invalidJumpsubOpcodeWhenNotActivated() {
        Program program = playCode("PUSH1 0x01 JUMPSUB");

        ProgramResult result = program.getResult();

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getException());
        Assert.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assert.assertEquals("Invalid operation code: opcode[5e], tx[<null>]", result.getException().getMessage());
    }

    @Test
    public void executeSimpleSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH1 0x04 JUMPSUB STOP BEGINSUB RETURNSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(18, program.getResult().getGasUsed());
    }

    @Test
    public void executeSimpleSubroutineWithStackOperation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH1 0x04 JUMPSUB STOP BEGINSUB PUSH1 0x2a RETURNSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueOf(42), stack.pop());
        Assert.assertEquals(21, program.getResult().getGasUsed());
    }

    @Test
    public void pushArgumentIsNotBeginsub() {
        byte[] code = compiler.compile("PUSH1 0x04 JUMPSUB STOP BEGINSUB PUSH1 0x5c RETURNSUB");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null, new HashSet<>());

        BitSet beginsubSet = program.getBeginsubSet();

        Assert.assertNotNull(beginsubSet);
        Assert.assertEquals(8, beginsubSet.size());

        for (int k = 0; k < beginsubSet.size(); k++) {
            if (k == 4) {
                Assert.assertTrue(beginsubSet.get(k));
            }
            else {
                Assert.assertFalse(beginsubSet.get(k));
            }
        }
    }

    @Test
    public void executeTwoLevelsOfSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH9 0x00000000000000000c JUMPSUB STOP BEGINSUB PUSH1 0x11 JUMPSUB RETURNSUB BEGINSUB RETURNSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(36, program.getResult().getGasUsed());
    }

    @Test
    public void execute1023LevelsOfSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH2 0x03ff PUSH1 0x07 JUMP BEGINSUB JUMPDEST DUP1 PUSH1 0x0d JUMPI STOP JUMPDEST PUSH1 0x01 SWAP1 SUB PUSH1 0x06 JUMPSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.ZERO, stack.pop());
        Assert.assertNull(program.getResult().getException());
    }

    @Test
    public void revertWhenExecute1024LevelsOfSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH2 0x0400 PUSH1 0x07 JUMP BEGINSUB JUMPDEST DUP1 PUSH1 0x0d JUMPI STOP JUMPDEST PUSH1 0x01 SWAP1 SUB PUSH1 0x06 JUMPSUB", activations);

        Assert.assertEquals(invoke.getGas(), program.getResult().getGasUsed());
        Assert.assertNotNull(program.getResult().getException());
        Assert.assertTrue(program.getResult().getException() instanceof Program.ReturnStackOverflowException);
        Assert.assertEquals("Return stack overflow: PC[20], tx[<null>]", program.getResult().getException().getMessage());
    }

    @Test
    public void executeSubroutineAtEndOfCode() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH1 0x05 JUMP BEGINSUB RETURNSUB JUMPDEST PUSH1 0x03 JUMPSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(30, program.getResult().getGasUsed());
    }

    @Test
    public void executeInvalidJumpToSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("PUSH9 0x01000000000000000c JUMPSUB STOP BEGINSUB RETURNSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(invoke.getGas(), program.getResult().getGasUsed());
        Assert.assertNotNull(program.getResult().getException());
        Assert.assertTrue(program.getResult().getException() instanceof Program.BadJumpDestinationException);
        Assert.assertEquals("Operation with pc isn't 'BEGINSUB': PC[-1], tx[<null>]", program.getResult().getException().getMessage());
    }

    @Test
    public void executeInvalidJumpToSubroutineWhenStackIsEmpty() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("JUMPSUB", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(invoke.getGas(), program.getResult().getGasUsed());
        Assert.assertNotNull(program.getResult().getException());
        Assert.assertTrue(program.getResult().getException() instanceof Program.StackTooSmallException);
        Assert.assertEquals("Expected stack size 1 but actual 0, tx: <null>", program.getResult().getException().getMessage());
    }

    @Test
    public void executeInvalidShallowReturnStack() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("RETURNSUB PC PC", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(invoke.getGas(), program.getResult().getGasUsed());
        Assert.assertNotNull(program.getResult().getException());
        Assert.assertTrue(program.getResult().getException() instanceof Program.InvalidReturnSubException);
        Assert.assertEquals("Invalid 'RETURNSUB': PC[0], tx[<null>]", program.getResult().getException().getMessage());
    }

    @Test
    public void executeInvalidWalkIntoSubroutine() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP172)).thenReturn(true);

        Program program = playCode("BEGINSUB RETURNSUB STOP", activations);
        Stack stack = program.getStack();

        Assert.assertEquals(0, stack.size());
        Assert.assertEquals(invoke.getGas(), program.getResult().getGasUsed());
        Assert.assertNotNull(program.getResult().getException());
        Assert.assertTrue(program.getResult().getException() instanceof Program.InvalidBeginSubException);
        Assert.assertEquals("Invalid 'BEGINSUB': PC[0], tx[<null>]", program.getResult().getException().getMessage());
    }

    private Program playCode(String code) {
        return playCode(compiler.compile(code), mock(ActivationConfig.ForBlock.class));
    }

    private Program playCode(String code, ActivationConfig.ForBlock activations) {
        return playCode(compiler.compile(code), activations);
    }

    private Program playCode(byte[] code, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null, new HashSet<>());

        vm.play(program);

        return program;
    }

    private Program executeCode(String code, int nsteps) {
        return executeCode(compiler.compile(code), nsteps, mock(ActivationConfig.ForBlock.class));
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        Program program = executeCode(code, nsteps, mock(ActivationConfig.ForBlock.class));

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    private Program executeCode(String code, int nsteps, ActivationConfig.ForBlock activations) {
        return executeCode(compiler.compile(code), nsteps, activations);
    }

    private Program executeCode(byte[] code, int nsteps, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null, new HashSet<>());

        for (int k = 0; k < nsteps; k++) {
            vm.step(program);
        }

        return program;
    }
}
