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

import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.Assert.assertEquals;

/**
 * Created by ajlopez on 25/01/2017.
 */
public class VMExecutionTest {
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;
    private Program program;

    @Before
    public void setup() {
        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @After
    public void tearDown() {
        invoke.getRepository().close();
    }

    @Test
    public void testPush1() {
        testCode("PUSH1 0xa0", 1, "00000000000000000000000000000000000000000000000000000000000000A0");
    }

    @Test
    public void testAdd() {
        testCode("PUSH1 1 PUSH1 2 ADD", 3, "0000000000000000000000000000000000000000000000000000000000000003");
    }

    @Test
    public void testMul() {
        testCode("PUSH1 3 PUSH1 2 MUL", 3, "0000000000000000000000000000000000000000000000000000000000000006");
    }

    @Test
    public void testSub() {
        testCode("PUSH1 1 PUSH1 2 SUB", 3, "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void testJumpSkippingInvalidJump() {
        testCode("PUSH1 0x05 JUMP PUSH1 0xa0 JUMPDEST PUSH1 0x01",
                4,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void dupnFirstItem() {
        testCode("PUSH1 0x01 DUPN 0x00",
                2,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void dupnFourthItem() {
        testCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 DUPN 0x03",
                5,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void dupnTwentiethItem() {
        testCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 DUPN 0x13",
                21,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void swapnSecondItem() {
        testCode("PUSH1 0x01 PUSH1 0x02 SWAPN 0x00",
                3,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void swapnFourthItem() {
        testCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 SWAPN 0x02",
                5,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void swapnTwentiethItem() {
        testCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 SWAPN 0x12",
                21,
                "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    public void invalidJustAfterEndOfCode() {
        try {
            testCode("PUSH1 0x03 JUMP", 2, "0000000000000000000000000000000000000000000000000000000000000001");
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[3];", ex.getMessage());
        }
    }

    @Test
    public void invalidJumpOutOfRange() {
        try {
            testCode("PUSH1 0x05 JUMP", 2, "0000000000000000000000000000000000000000000000000000000000000001");
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[5];", ex.getMessage());
        }
    }

    @Test
    public void invalidNegativeJump() {
        try {
            testCode("PUSH32 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff JUMP", 2, "0000000000000000000000000000000000000000000000000000000000000001");
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[-1];", ex.getMessage());
        }
    }

    @Test
    public void invalidTooFarJump() {
        try {
            testCode("PUSH1 0xff JUMP", 2, "0000000000000000000000000000000000000000000000000000000000000001");
            Assert.fail();
        }
        catch (Program.BadJumpDestinationException ex) {
            Assert.assertEquals("Operation with pc isn't 'JUMPDEST': PC[255];", ex.getMessage());
        }
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

    private void testCode(String code, int nsteps, String expected) {
        testCode(compiler.compile(code), nsteps, expected);
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        VM vm = new VM();
        program = new Program(code, invoke);

        for (int k = 0; k < nsteps; k++)
            vm.step(program);

        assertEquals(expected, Hex.toHexString(program.getStack().peek().getData()).toUpperCase());
    }
}
