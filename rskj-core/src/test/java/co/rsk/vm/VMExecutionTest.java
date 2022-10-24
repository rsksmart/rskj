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
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.*;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 25/01/2017.
 */
class VMExecutionTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private VmConfig vmConfig = config.getVmConfig();
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;

    @BeforeEach
    void setup() {
        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @Test
    void testPush1() {
        Program program = executeCode("PUSH1 0xa0", 1);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(0xa0), stack.peek());
    }

    @Test
    void testAdd() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 ADD", 3);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(3), stack.peek());
    }

    @Test
    void testMul() {
        Program program = executeCode("PUSH1 0x03 PUSH1 0x02 MUL", 3);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(6), stack.peek());
    }

    @Test
    void testSub() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 SUB", 3);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
    }

    private void executeShift(String number, String shiftAmount, String expect, String op , ActivationConfig.ForBlock activations){
        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH1 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    void testSHL1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHL",
                activations);
    }

    @Test
    void testSHL2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000002",
                "SHL",
                activations);
    }

    @Test
    void testSHL3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0xff",
                "8000000000000000000000000000000000000000000000000000000000000000",
                "SHL",
                activations);
    }

    @Test
    void testSHL4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String shiftAmount = "0x0100";
        String op = "SHL";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    void testSHL5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SHL",
                activations);
    }

    @Test
    void testSHL6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xff",
                "8000000000000000000000000000000000000000000000000000000000000000",
                "SHL",
                activations);
    }

    @Test
    void testSHL7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x01",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
                "SHL",
                activations);
    }

    @Test
    void testSHR1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }

    @Test
    void testSHR2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "SHR",
                activations);
    }

    @Test
    void testSHR3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0x01",
                "4000000000000000000000000000000000000000000000000000000000000000",
                "SHR",
                activations);
    }

    @Test
    void testSHR4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0xff",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }


    @Test
    void testSHR5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SHR",
                activations);
    }

    @Test
    void testSHR6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xff",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SHR",
                activations);
    }

    @Test
    void testSHR7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x8000000000000000000000000000000000000000000000000000000000000000";
        String shiftAmount = "0x0100";
        String op = "SHR";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }

    @Test
    void testSAR1() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x00",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "SAR",
                activations);
    }


    @Test
    void testSAR2() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x01",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "SAR",
                activations);
    }

    @Test
    void testSAR3() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0x01",
                "c000000000000000000000000000000000000000000000000000000000000000",
                "SAR",
                activations);
    }

    @Test
    void testSAR4() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x8000000000000000000000000000000000000000000000000000000000000000",
                "0xff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    void testSAR5() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x00",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    void testSAR6() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0x01",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "SAR",
                activations);
    }

    @Test
    void testSAR7() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        executeShift("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0xf8",
                "000000000000000000000000000000000000000000000000000000000000007f",
                "SAR",
                activations);
    }

    @Test
    void testSAR8() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(true);

        String number = "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        String shiftAmount = "0x0100";
        String op = "SAR";
        String expect = "0000000000000000000000000000000000000000000000000000000000000000";

        Program program = executeCodeWithActivationConfig("PUSH32 "+number+" PUSH2 "+shiftAmount+" "+op, 3, activations);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueFromHex(expect), stack.peek());
    }


    @Test
    void testSAR3ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);
        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeCodeWithActivationConfig("PUSH32 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff PUSH1 0xff SAR", 3, activations));
    }

    @Test
    void testSHL1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHL", 3, activations));
    }

    @Test
    void testSHR1ShouldFailOnOldVersion() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP120)).thenReturn(false);

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeCodeWithActivationConfig("PUSH32 0x0000000000000000000000000000000000000000000000000000000000000001 PUSH1 0x01 SHR", 3, activations));
    }

    @Test
    void testJumpSkippingInvalidJump() {
        Program program = executeCode("PUSH1 0x05 JUMP PUSH1 0xa0 JUMPDEST PUSH1 0x01", 4);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
    }

    @Test
    void dupnFirstItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x00 DUPN", 3);
        Stack stack = program.getStack();

        Assertions.assertEquals(2, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
        Assertions.assertEquals(DataWord.valueOf(1), stack.get(0));
    }

    @Test
    void dupnFourthItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x03 DUPN", 6);
        Stack stack = program.getStack();

        Assertions.assertEquals(5, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());

        for (int k = 0; k < 4; k++)
            Assertions.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test
    void dupnTwentiethItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 PUSH1 0x13 DUPN", 22);
        Stack stack = program.getStack();

        Assertions.assertEquals(21, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());

        for (int k = 0; k < 20; k++)
            Assertions.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test
    void dupnTwentiethItemWithoutEnoughItems() {
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x13 DUPN", 21));
    }

    @Test
    void dupnTooManyItemsWithOverflow() {
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCode("PUSH1 0x01 PUSH4 0x7f 0xff 0xff 0xff DUPN", 3));
    }

    @Test
    void dupnAsInvalidOpcodeWhenDeactivated() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP191)).thenReturn(true);

        Program program = playCodeWithActivationConfig("PUSH1 0x01 PUSH1 0x00 DUPN", activations);
        ProgramResult result = program.getResult();

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getException());
        Assertions.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assertions.assertEquals("Invalid operation code: opcode[a8], tx[<null>]", result.getException().getMessage());
    }

    @Test
    void swapnSecondItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 SWAPN", 4);
        Stack stack = program.getStack();

        Assertions.assertEquals(2, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
        Assertions.assertEquals(DataWord.valueOf(2), stack.get(0));
    }

    @Test
    void swapnFourthItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x02 SWAPN", 6);
        Stack stack = program.getStack();

        Assertions.assertEquals(4, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
        Assertions.assertEquals(DataWord.valueOf(4), stack.get(0));
        Assertions.assertEquals(DataWord.valueOf(2), stack.get(1));
        Assertions.assertEquals(DataWord.valueOf(3), stack.get(2));
    }

    @Test
    void swapnTwentiethItem() {
        Program program = executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x14 PUSH1 0x12 SWAPN", 22);
        Stack stack = program.getStack();

        Assertions.assertEquals(20, stack.size());
        Assertions.assertEquals(DataWord.valueOf(1), stack.peek());
        Assertions.assertEquals(DataWord.valueOf(20), stack.get(0));

        for (int k = 1; k < 19; k++)
            Assertions.assertEquals(DataWord.valueOf(k + 1), stack.get(k));
    }

    @Test
    void swapnTooManyItemsWithOverflow() {
        ;
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCode("PUSH1 0x01 PUSH1 0x01 PUSH4 0x7f 0xff 0xff 0xff SWAPN", 4));
    }

    @Test
    void swapnTwentiethItemWithoutEnoughItems() {
        Assertions.assertThrows(Program.StackTooSmallException.class, () -> executeCode("PUSH1 0x01 PUSH1 0x02 PUSH1 0x03 PUSH1 0x04 PUSH1 0x05 PUSH1 0x06 PUSH1 0x07 PUSH1 0x08 PUSH1 0x09 PUSH1 0x0a PUSH1 0x0b PUSH1 0x0c PUSH1 0x0d PUSH1 0x0e PUSH1 0x0f PUSH1 0x10 PUSH1 0x11 PUSH1 0x12 PUSH1 0x13 PUSH1 0x12 SWAPN", 22));
    }


    @Test
    void swapnAsInvalidOpcodeWhenDeactivated() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP191)).thenReturn(true);

        Program program = playCodeWithActivationConfig("PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 SWAPN", activations);
        ProgramResult result = program.getResult();

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getException());
        Assertions.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assertions.assertEquals("Invalid operation code: opcode[a9], tx[<null>]", result.getException().getMessage());
    }

    @Test
    void txIndexExecution() {
        invoke.setTransactionIndex(DataWord.valueOf(42));
        Program program = executeCode("TXINDEX", 1);
        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(42), stack.peek());
    }

    @Test
    void txIndexAsInvalidOpcodeWhenDeactivated() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP191)).thenReturn(true);

        invoke.setTransactionIndex(DataWord.valueOf(42));
        Program program = playCodeWithActivationConfig("TXINDEX", activations);
        ProgramResult result = program.getResult();

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getException());
        Assertions.assertTrue(result.getException() instanceof Program.IllegalOperationException);
        Assertions.assertEquals("Invalid operation code: opcode[aa], tx[<null>]", result.getException().getMessage());
    }

    @Test
    void invalidJustAfterEndOfCode() {
        try {
            executeCode("PUSH1 0x03 JUMP", 2);
            Assertions.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assertions.assertEquals("Operation with pc isn't 'JUMPDEST': PC[3], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    void invalidJumpOutOfRange() {
        try {
            executeCode("PUSH1 0x05 JUMP", 2);
            Assertions.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assertions.assertEquals("Operation with pc isn't 'JUMPDEST': PC[5], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    void invalidNegativeJump() {
        try {
            executeCode("PUSH32 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff JUMP", 2);
            Assertions.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assertions.assertEquals("Operation with pc isn't 'JUMPDEST': PC[-1], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    void invalidTooFarJump() {
        try {
            executeCode("PUSH1 0xff JUMP", 2);
            Assertions.fail();
        } catch (Program.BadJumpDestinationException ex) {
            Assertions.assertEquals("Operation with pc isn't 'JUMPDEST': PC[255], tx[<null>]", ex.getMessage());
        }
    }

    @Test
    void dupnArgumentIsNotJumpdest() {
        byte[] code = compiler.compile("JUMPDEST DUPN 0x5b 0x5b");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null, new HashSet<>());

        BitSet jumpdestSet = program.getJumpdestSet();

        Assertions.assertNotNull(jumpdestSet);
        Assertions.assertEquals(4, jumpdestSet.size());
        Assertions.assertTrue(jumpdestSet.get(0));
        Assertions.assertFalse(jumpdestSet.get(1));
        Assertions.assertTrue(jumpdestSet.get(2));
        Assertions.assertTrue(jumpdestSet.get(3));
    }

    @Test
    void swapnArgumentIsNotJumpdest() {
        byte[] code = compiler.compile("JUMPDEST SWAPN 0x5b 0x5b");
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, null, new HashSet<>());

        BitSet jumpdestSet = program.getJumpdestSet();

        Assertions.assertNotNull(jumpdestSet);
        Assertions.assertEquals(4, jumpdestSet.size());
        Assertions.assertTrue(jumpdestSet.get(0));
        Assertions.assertFalse(jumpdestSet.get(1));
        Assertions.assertTrue(jumpdestSet.get(2));
        Assertions.assertTrue(jumpdestSet.get(3));
    }

    @Test
    void thePathOfFifteenThousandJumps() {
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
    void returnDataSizeBasicGasCost() {
        Program program = executeCode("0x3d", 1);

        Assertions.assertNotNull(program);
        Assertions.assertNotNull(program.getResult());
        Assertions.assertNull(program.getResult().getException());
        Assertions.assertEquals(2, program.getResult().getGasUsed());
    }

    @Test
    void returnDataCopyBasicGasCost() {
        Program program = executeCode(
                // push some values for RETURNDATACOPY
                "PUSH1 0x00 PUSH1 0x00 PUSH1 0x01 " +
                // call RETURNDATACOPY
                "0x3e",
        4);

        Assertions.assertNotNull(program);
        Assertions.assertNotNull(program.getResult());
        Assertions.assertNull(program.getResult().getException());
        Assertions.assertEquals(12, program.getResult().getGasUsed());
    }

    @Test
    void callDataCopyBasicGasCost() {
        Program program = executeCode(
                // push some values for CALLDATACOPY
                "PUSH1 0x00 PUSH1 0x00 PUSH1 0x01 " +
                // call CALLDATACOPY
                "0x37",
        4);

        Assertions.assertNotNull(program);
        Assertions.assertNotNull(program.getResult());
        Assertions.assertNull(program.getResult().getException());
        Assertions.assertEquals(12, program.getResult().getGasUsed());
    }

    @Test
    void chainIDMainnet(){
        executeCHAINID(Constants.MAINNET_CHAIN_ID);
    }

    @Test
    void chainIDTestnet(){
        executeCHAINID(Constants.TESTNET_CHAIN_ID);
    }

    @Test
    void chainIDRegtest(){
        executeCHAINID(Constants.REGTEST_CHAIN_ID);
    }

    @Test
    void chainIDIsCorrectOpcodeNumber(){
        Assertions.assertEquals(0x46, OpCodes.OP_CHAINID);
        Assertions.assertEquals(OpCode.CHAINID, OpCode.code((byte) 0x46));
    }

    @Test
    void testChainIDNotActivated(){
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP152)).thenReturn(false);

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeCHAINIDWithActivations(Constants.REGTEST_CHAIN_ID, activations));
    }

    private void executeCHAINID(byte chainIDExpected) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP152)).thenReturn(true);

        executeCHAINIDWithActivations(chainIDExpected, activations);
    }

    private void executeCHAINIDWithActivations(byte chainIDExpected, ActivationConfig.ForBlock activations) {
        vmConfig = mock(VmConfig.class);
        when(vmConfig.getChainId()).thenReturn(chainIDExpected);

        Program program = executeCodeWithActivationConfig("CHAINID", 1, activations);

        Stack stack = program.getStack();

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(chainIDExpected), stack.peek());
    }

    @Test
    void selfBalanceFailsWithRSKIPNotActivated() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(false);

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeCodeWithActivationConfig("SELFBALANCE", 1, activations));
    }

    @Test
    void selfBalanceRunsCorrectly() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(true);

        int balanceValue = 100;

        RskAddress testAddress = new RskAddress(invoke.getCallerAddress());
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(balanceValue));

        Program program = executeCodeWithActivationConfig("SELFBALANCE", 1, activations);
        Stack stack = program.getStack();

        long selfBalanceGas = OpCode.SELFBALANCE.getTier().asInt();

        Assertions.assertEquals(selfBalanceGas, program.getResult().getGasUsed());
        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(DataWord.valueOf(balanceValue), stack.peek());
    }

    @Test
    void selfBalanceIsSameAsBalance() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP151)).thenReturn(true);

        int balanceValue = 100;

        RskAddress testAddress = new RskAddress(invoke.getCallerAddress());
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(balanceValue));

        Program programSelfBalance = executeCodeWithActivationConfig("SELFBALANCE",1, activations);
        Stack stackSelfBalance = programSelfBalance.getStack();

        Program programBalance = executeCodeWithActivationConfig("PUSH20 0x" + testAddress.toHexString() +
                " BALANCE", 2, activations);
        Stack stackBalance = programBalance.getStack();

        Assertions.assertEquals(1, stackSelfBalance.size());

        DataWord selfBalance = stackSelfBalance.pop();
        DataWord balance = stackBalance.pop();

        long selfBalanceGas = OpCode.SELFBALANCE.getTier().asInt();

        Assertions.assertEquals(selfBalanceGas, programSelfBalance.getResult().getGasUsed());
        Assertions.assertEquals(selfBalance, DataWord.valueOf(balanceValue));
        Assertions.assertEquals(balance, selfBalance);
    }

    @Test
    void createContractAndPreserveContractAddressBalance() {
        /**
         *  CREATE call with expected address that has non-zero balance
         */
        String address = "0x0000000000000000000000000000000000000000";
        RskAddress testAddress = new RskAddress(address);
        String pushInitCode = "PUSH32 0x6000000000000000000000000000000000000000000000000000000000000000";
        String expectedContractAddress = new RskAddress(HashUtil.calcNewAddr(testAddress.getBytes(), ByteUtil.longToBytesNoLeadZeroes(0))).toHexString();
        int size = 1;
        int intOffset = 0;
        int value = 10;
        int initialContractAddressBalance = 100;
        Coin expectedContractBalance = Coin.valueOf(initialContractAddressBalance + value);

        RskAddress contractAddress = new RskAddress(expectedContractAddress);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(value + 1000));
        invoke.getRepository().addBalance(contractAddress, Coin.valueOf(initialContractAddressBalance));
        String inSize = "0x" + DataWord.valueOf(size);
        String inOffset = "0x" + DataWord.valueOf(intOffset);

        pushInitCode += " PUSH1 0x00 MSTORE";

        String codeToExecute = pushInitCode +
                " PUSH32 " + inSize +
                " PUSH32 " + inOffset +
                " PUSH32 " + "0x" + DataWord.valueOf(value) +
                " CREATE ";

        Program program = executeCode(codeToExecute, 7);
        Coin actualContractBalance = invoke.getRepository().getBalance(contractAddress);
        Stack stack = program.getStack();
        Assertions.assertEquals(1, stack.size());
        String actualAddress = ByteUtil.toHexString(stack.pop().getLast20Bytes());
        Assertions.assertEquals(expectedContractAddress.toUpperCase(), actualAddress.toUpperCase());
        Assertions.assertEquals(expectedContractAddress.toUpperCase(), actualAddress.toUpperCase());
        Assertions.assertEquals(expectedContractBalance, actualContractBalance);
    }

    private Program executeCode(String code, int nsteps) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, mock(ActivationConfig.ForBlock.class));
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        Program program = executeCodeWithActivationConfig(code, nsteps, mock(ActivationConfig.ForBlock.class));

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    private Program executeCodeWithActivationConfig(String code, int nsteps, ActivationConfig.ForBlock activations) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, activations);
    }

    private Program executeCodeWithActivationConfig(byte[] code, int nsteps, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null, new HashSet<>());

        for (int k = 0; k < nsteps; k++) {
            vm.step(program);
        }

        return program;
    }

    private Program playCodeWithActivationConfig(String code, ActivationConfig.ForBlock activations) {
        return playCodeWithActivationConfig(compiler.compile(code), activations);
    }

    private Program playCodeWithActivationConfig(byte[] code, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,null, new HashSet<>());

        vm.play(program);

        return program;
    }
}
