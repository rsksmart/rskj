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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP120;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class VMExecutionTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
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
        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH1 "+shiftAmount+" "+op, 3, activations);
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

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
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

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
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

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }



    @Test(expected = Program.IllegalOperationException.class)
    public void testSAR3ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);
        executeCodeWithActivationConfig("PUSH32 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff PUSH1 0xff SAR", 3, activations);
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void testSHL1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHL", 3, activations);
    }

    @Test(expected = Program.IllegalOperationException.class)
    public void testSHR1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHR", 3, activations);
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
    public void txindexExecution() {
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
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[3];", ex.getMessage());
        }
    }

    @Test
    public void invalidJumpOutOfRange() {
        try {
            executeCode("PUSH1 0x05 JUMP", 2);
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[5];", ex.getMessage());
        }
    }

    @Test
    public void invalidNegativeJump() {
        try {
            executeCode("PUSH32 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff JUMP", 2);
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[-1];", ex.getMessage());
        }
    }

    @Test
    public void invalidTooFarJump() {
        try {
            executeCode("PUSH1 0xff JUMP", 2);
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[255];", ex.getMessage());
        }
    }

    @Test
    public void dupnArgumentIsNotJumpdest() {
        byte[] code = compiler.compile("JUMPDEST DUPN 0x5b 0x5b");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null);

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
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null);

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

    private Program executeCode(String code, int nsteps) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, mock(ActivationConfig.ForBlock.class));
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        Program program = executeCodeWithActivationConfig(code, nsteps, mock(ActivationConfig.ForBlock.class));

        assertEquals(expected, Hex.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    private Program executeCodeWithActivationConfig(String code, int nsteps, ActivationConfig.ForBlock activations) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, activations);
    }

    private Program executeCodeWithActivationConfig(byte[] code, int nsteps, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null);

        for (int k = 0; k < nsteps; k++) {
            vm.step(program);
        }

        return program;
    }
}
