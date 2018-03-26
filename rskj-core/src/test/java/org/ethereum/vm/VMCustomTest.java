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
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Program.OutOfGasException;
import org.ethereum.vm.program.Program.StackTooSmallException;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.spongycastle.util.encoders.Hex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VMCustomTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
    private ProgramInvokeMockImpl invoke;
    private Program program;

    @Before
    public void setup() {
        RskAddress ownerAddress = new RskAddress("77045E71A7A2C50903D88E564CD72FAB11E82051");
        byte[] msgData = Hex.decode("00000000000000000000000000000000000000000000000000000000000000A1" +
                "00000000000000000000000000000000000000000000000000000000000000B1");

        invoke = new ProgramInvokeMockImpl(msgData);
        invoke.setOwnerAddress(ownerAddress);

        invoke.getRepository().createAccount(ownerAddress);
        invoke.getRepository().addBalance(ownerAddress, Coin.valueOf(1000L));
    }

    @After
    public void tearDown() {
        invoke.getRepository().close();
    }

    @Test // CALLDATASIZE OP
    public void testCALLDATASIZE_1() {

        VM vm = getSubject();
        program = getProgram("36");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000040";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }


    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_1() {

        VM vm = getSubject();
        program = getProgram("600035");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000A1";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_2() {

        VM vm = getSubject();
        program = getProgram("600235");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000A10000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }


    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_3() {

        VM vm = getSubject();
        program = getProgram("602035");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000B1";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }


    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_4() {

        VM vm = getSubject();
        program = getProgram("602335");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000B1000000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // CALLDATALOAD OP
    public void testCALLDATALOAD_5() {

        VM vm = getSubject();
        program = getProgram("603F35");
        String s_expected_1 = "B100000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test(expected = RuntimeException.class) // CALLDATALOAD OP mal
    public void testCALLDATALOAD_6() {

        VM vm = getSubject();
        program = getProgram("35");
        try {
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_1() {

        VM vm = getSubject();
        program = getProgram("60206000600037");
        String m_expected = "00000000000000000000000000000000000000000000000000000000000000A1";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Hex.toHexString(program.getMemory()).toUpperCase());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_2() {

        VM vm = getSubject();
        program = getProgram("60406000600037");
        String m_expected = "00000000000000000000000000000000000000000000000000000000000000A1" +
                "00000000000000000000000000000000000000000000000000000000000000B1";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Hex.toHexString(program.getMemory()).toUpperCase());
    }


    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_3() {

        VM vm = getSubject();
        program = getProgram("60406004600037");
        String m_expected = "000000000000000000000000000000000000000000000000000000A100000000" +
                "000000000000000000000000000000000000000000000000000000B100000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Hex.toHexString(program.getMemory()).toUpperCase());
    }


    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_4() {

        VM vm = getSubject();
        program = getProgram("60406000600437");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000A100000000000000000000000000000000000000000000000000000000" +
                "000000B100000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Hex.toHexString(program.getMemory()).toUpperCase());
    }

    @Test // CALLDATACOPY OP
    public void testCALLDATACOPY_5() {

        VM vm = getSubject();
        program = getProgram("60406000600437");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000A100000000000000000000000000000000000000000000000000000000" +
                "000000B100000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, Hex.toHexString(program.getMemory()).toUpperCase());
    }


    @Test(expected = StackTooSmallException.class) // CALLDATACOPY OP mal
    public void testCALLDATACOPY_6() {

        VM vm = getSubject();
        program = getProgram("6040600037");

        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test(expected = OutOfGasException.class) // CALLDATACOPY OP mal
    public void testCALLDATACOPY_7() {

        VM vm = getSubject();
        program = getProgram("6020600073CC0929EB16730E7C14FEFC63006AC2D794C5795637");

        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // ADDRESS OP
    public void testADDRESS_1() {

        VM vm = getSubject();
        program = getProgram("30");
        String s_expected_1 = "00000000000000000000000077045E71A7A2C50903D88E564CD72FAB11E82051";

        vm.step(program);

        DataWord item1 = program.stackPop();
        program.getStorage().close();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // BALANCE OP
    public void testBALANCE_1() {

        VM vm = getSubject();
        program = getProgram("3031");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000003E8";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ORIGIN OP
    public void testORIGIN_1() {

        VM vm = getSubject();
        program = getProgram("32");
        String s_expected_1 = "00000000000000000000000013978AEE95F38490E9769C39B2773ED763D9CD5F";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // CALLER OP
    public void testCALLER_1() {

        VM vm = getSubject();
        program = getProgram("33");
        String s_expected_1 = "000000000000000000000000885F93EED577F2FC341EBB9A5C9B2CE4465D96C4";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // CALLVALUE OP
    public void testCALLVALUE_1() {

        VM vm = getSubject();
        program = getProgram("34");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000DE0B6B3A7640000";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SHA3 OP
    public void testSHA3_1() {

        VM vm = getSubject();
        program = getProgram("60016000536001600020");
        String s_expected_1 = "5FE7F977E71DBA2EA1A68E21057BEEBB9BE2AC30C6410AA38D4F3FBE41DCFFD2";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SHA3 OP
    public void testSHA3_2() {

        VM vm = getSubject();
        program = getProgram("6102016000526002601E20");
        String s_expected_1 = "114A3FE82A0219FCC31ABD15617966A125F12B0FD3409105FC83B487A9D82DE4";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test(expected = StackTooSmallException.class) // SHA3 OP mal
    public void testSHA3_3() {

        VM vm = getSubject();
        program = getProgram("610201600052600220");
        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // BLOCKHASH OP
    public void testBLOCKHASH_1() {

        VM vm = getSubject();
        program = getProgram("600140");
        String s_expected_1 = "C89EFDAA54C0F20C7ADF612882DF0950F5A951637E0307CDCB4C672F298B8BC6";

        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // COINBASE OP
    public void testCOINBASE_1() {

        VM vm = getSubject();
        program = getProgram("41");
        String s_expected_1 = "000000000000000000000000E559DE5527492BCB42EC68D07DF0742A98EC3F1E";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // TIMESTAMP OP
    public void testTIMESTAMP_1() {

        VM vm = getSubject();
        program = getProgram("42");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000000005387FE24";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // NUMBER OP
    public void testNUMBER_1() {

        VM vm = getSubject();
        program = getProgram("43");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000021";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // DIFFICULTY OP
    public void testDIFFICULTY_1() {

        VM vm = getSubject();
        program = getProgram("44");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000003ED290";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // GASPRICE OP
    public void testGASPRICE_1() {

        VM vm = getSubject();
        program = getProgram("3A");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000009184E72A000";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Ignore //TODO #POC9
    @Test // GAS OP
    public void testGAS_1() {

        VM vm = getSubject();
        program = getProgram("5A");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000F423F";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test // GASLIMIT OP
    public void testGASLIMIT_1() {

        VM vm = getSubject();
        program = getProgram("45");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000F4240";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
    }

    @Test(expected = Program.IllegalOperationException.class) // INVALID OP
    public void testINVALID_1() {

        VM vm = getSubject();
        program = getProgram("60012F6002");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        try {
            vm.step(program);
            vm.step(program);
        } finally {
            assertTrue(program.isStopped());
            DataWord item1 = program.stackPop();
            assertEquals(s_expected_1, Hex.toHexString(item1.getData()).toUpperCase());
        }
    }

    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    private Program getProgram(String ops) {
        return new Program(vmConfig, precompiledContracts, mock(BlockchainConfig.class), Hex.decode(ops), invoke, null);
    }

}

// TODO: add gas expeted and calculated to all test cases
// TODO: considering: G_TXDATA + G_TRANSACTION

/**
 *   TODO:
 *
 *   22) CREATE:
 *   23) CALL:
 *
 *
 **/

/**

 contract creation (gas usage)
 -----------------------------
 G_TRANSACTION =                                (500)
 60016000546006601160003960066000f261778e600054 (115)
 PUSH1    6001 (1)
 PUSH1    6000 (1)
 MSTORE   54   (1 + 1)
 PUSH1    6006 (1)
 PUSH1    6011 (1)
 PUSH1    6000 (1)
 CODECOPY 39   (1)
 PUSH1    6006 (1)
 PUSH1    6000 (1)
 RETURN   f2   (1)
 61778e600054

 */
