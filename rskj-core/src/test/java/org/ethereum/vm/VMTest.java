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
import co.rsk.core.RskAddress;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.vm.BitSet;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.Utils;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Program.BadJumpDestinationException;
import org.ethereum.vm.program.Program.StackTooSmallException;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static java.lang.StrictMath.min;
import static org.ethereum.util.ByteUtil.oneByteToHexString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class VMTest {

    private ProgramInvokeMockImpl invoke;
    private Program program;
    private VM vm;

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    private static MockedStatic<LoggerFactory> loggerFactoryMocked;

    protected void setUp(boolean isLogEnabled) {
        Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(isLogEnabled);

        loggerFactoryMocked = mockStatic(LoggerFactory.class, Mockito.CALLS_REAL_METHODS);
        loggerFactoryMocked.when(() -> LoggerFactory.getLogger("VM")).thenReturn(logger);

        vm = getSubject();
        invoke = new ProgramInvokeMockImpl();
    }

    @AfterEach
    void tearDown() {
        loggerFactoryMocked.close();
    }

    @Test
    void testSTATICCALLWithStatusZeroUsingSStore() {
        testSTATICCALLWithStatusZeroUsingOpCode("SSTORE");
    }

    @Test
    void testSTATICCALLWithStatusZeroUsingLogs() {
        for (int k = 0; k < 5; k++) {
            testSTATICCALLWithStatusZeroUsingOpCode("LOG" + k);
        }
    }

    @Test
    void testSTATICCALLWithStatusZeroUsingCreate() {
        testSTATICCALLWithStatusZeroUsingOpCode("CREATE");
    }

    @Test
    void testSTATICCALLWithStatusZeroUsingSuicide() {
        testSTATICCALLWithStatusZeroUsingOpCode("SUICIDE");
    }

    void testSTATICCALLWithStatusZeroUsingOpCode(String opcode) {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 " + opcode), null);
        RskAddress address = invoke.getContractAddress();
        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " STATICCALL"), createTransaction(0));
        program.fullTrace();
        vm.steps(program, Long.MAX_VALUE);

        assertEquals(DataWord.ZERO, program.stackPop());
    }


    @Test
    void testCALLWithBigUserSpecifiedGas() {
        String maxGasValue = "7FFFFFFFFFFFFFFF";
        RskAddress callee = createAddress("callee");
        // purposefully broken contract so as to consume all gas
        invoke = new ProgramInvokeMockImpl(compile("" +
                " ADD"
        ), callee);
        program = getProgram(compile("" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x01" +
                " PUSH20 0x" + callee.toHexString() +
                " PUSH8 0x" + maxGasValue +
                " CALL"
        ), createTransaction(0));
        program.fullTrace();
        vm.steps(program, Long.MAX_VALUE);
        Assertions.assertEquals(
                program.getResult().getGasUsed(),
                invoke.getGas(), "faulty program with bigger gas limit than gas available should use all the gas in a block"
        );
    }

    @Test
    void testLOGWithDataCostBiggerThanPreviousGasSize() {

        // The test wants to try to set dataCost to something between
        // 2**62-1 (the prev gas max size) and 2**63-1 (the current gas max)
        // to check if something is wrong with the change.
        // doLOG() is the only place where this constant was used.
        invoke.setGasLimit(6_800_000);
        long previousGasMaxSize = 0x3fffffffffffffffL;
        long sizeRequired = Math.floorDiv(previousGasMaxSize, GasCost.LOG_DATA_GAS) + 1;
        String sizeInHex = String.format("%016X", sizeRequired);

        // check it is over the previous max size but below our current max gas
        assert (sizeRequired * GasCost.LOG_DATA_GAS > previousGasMaxSize);
        // check it did not overflow
        assert (sizeRequired > 0);

        program = getProgram(compile("" +
                " PUSH8 0x" + sizeInHex +
                " PUSH1 0x00" +
                " LOG0"
        ));
        program.fullTrace();
        try {
            Assertions.assertThrows(Program.OutOfGasException.class, () -> vm.steps(program, Long.MAX_VALUE));
        } finally {
            invoke.setGasLimit(100000);
        }
    }

    @Test
    void testSTATICCALLWithStatusOne() {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 SUB"), null);
        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " STATICCALL"
        ));

        program.fullTrace();
        vm.steps(program, Long.MAX_VALUE);

        assertEquals(DataWord.ONE, program.stackPop());
        assertTrue(program.getStack().isEmpty());
    }

    // This test should throw an exception because we are reading from the RETURNDATABUFFER
    // in a non-existent position. This results in an error according to EIP 211
    @Test
    void returnDataBufferAfterCallToNonExistentContract() {
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0);
        Assertions.assertThrows(RuntimeException.class, () -> doCallToNonExistentContractAndReturnValue(expected, true));
    }

    @Test
    void beforeIrisReturnDataBufferAfterCallToNonExistentContract() {
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0);
        expected[31] = (byte) 21;
        doCallToNonExistentContractAndReturnValue(expected, false);
    }

    private void doCallToNonExistentContractAndReturnValue(byte[] expected, boolean active) {
        invoke = new ProgramInvokeMockImpl(compile(
                "PUSH1 0x10" +
                        " PUSH1 0x05 " +
                        " ADD" +
                        " PUSH1 0x40" +
                        " MSTORE " +
                        " PUSH1 0x20 " +
                        " PUSH1 0x40" +
                        " RETURN"
        ), null);
        program = getProgram(compile(
                " PUSH1 0x20" +  // return size is 32 bytes
                        " PUSH1 0x40" +       // on free memory pointer
                        " PUSH1 0x00" +       // no argument
                        " PUSH1 0x00" +       // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() + // in the mock contract specified above
                        " PUSH4 0x005B8D80" + // with some gas
                        " STATICCALL" +       // call it! result should be 0x15
                        " PUSH1 0x20" +       // now do the same...
                        " PUSH1 0x40" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +     // but call a non-existent contract
                        " PUSH4 0x005B8D80" +
                        " STATICCALL" +
                        " PUSH1 0x20 " +    // now put the 32 bytes
                        " PUSH1 0x00 " +    // from the beginning of the return databuffer
                        " PUSH1 0x40" +     //  to the 0x40 position on memory
                        " RETURNDATACOPY" + // do it!
                        " PUSH1 0x20" +     // and return 32 bytes
                        " PUSH1 0x40" +     // from the 0x40 position on memory
                        " RETURN"           // the return value of the contract should be zero (as last call failed)
        ));
        when(program.getActivations().isActive(ConsensusRule.RSKIP171)).thenReturn(active);
        vm.steps(program, Long.MAX_VALUE);
        byte[] result = program.getResult().getHReturn();
        assertArrayEquals(expected, result);
    }

    @Test
    void returnDataSizeAfterCallToNonExistentContract() {
        doCallToNonExistentContractAndReturnDataSize(true, 0);
    }

    @Test
    void beforeIrisReturnDataSizeAfterCallToNonExistentContract() {
        doCallToNonExistentContractAndReturnDataSize(false, 32);
    }

    private void doCallToNonExistentContractAndReturnDataSize(boolean active, int expectedReturnDataSize) {
        invoke = new ProgramInvokeMockImpl(compile(
                "PUSH1 0x10" +
                        " PUSH1 0x05 " +
                        " ADD" +
                        " PUSH1 0x40" +
                        " MSTORE " +
                        " PUSH1 0x20 " +
                        " PUSH1 0x40" +
                        " RETURN"
        ), null);
        program = getProgram(compile(
                " PUSH1 0x20" +  // return size is 32 bytes
                        " PUSH1 0x40" +  // on free memory pointer
                        " PUSH1 0x00" +  // no argument
                        " PUSH1 0x00" +  // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() + // in the mock contract specified above
                        " PUSH4 0x005B8D80" + // with some gas
                        " STATICCALL" +  // call it! result should be 0x15
                        " PUSH1 0x20" +  // now do the same...
                        " PUSH1 0x40" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" + // but call a non-existent contract
                        " PUSH4 0x005B8D80" +
                        " STATICCALL" +
                        " RETURNDATASIZE" // push the return data size to the stack
        ));
        when(program.getActivations().isActive(ConsensusRule.RSKIP171)).thenReturn(active);
        vm.steps(program, Long.MAX_VALUE);
        assertEquals(expectedReturnDataSize, program.stackPop().intValue());
    }

    @Test
    void returnDataBufferAfterInsufficientFunds() {
        String contract = "471fd3ad3e9eeadeec4608b92d16ce6b500704cc"; // in the mock contract specified above
        doCallInsufficientFunds(true, 0, contract);
    }

    @Test
    void returnDataBufferAfterInsufficientFundsAtPrecompiled() {
        String precompiled = "0000000000000000000000000000000001000010";
        doCallInsufficientFunds(true, 0, precompiled);
    }

    @Test
    void beforeIrisReturnDataBufferWithInsufficientFunds() {
        String contract = "471fd3ad3e9eeadeec4608b92d16ce6b500704cc"; // in the mock contract specified above
        doCallInsufficientFunds(false, 32, contract);
    }

    @Test
    void beforeIrisReturnDataBufferWithInsufficientFundsAtPrecompiled() {
        String precompiled = "0000000000000000000000000000000001000010";
        doCallInsufficientFunds(false, 32, precompiled);
    }

    private void doCallInsufficientFunds(boolean irisHardForkActive, int expectedReturnDataSize, String contract) {
        invoke = new ProgramInvokeMockImpl(compile(
                "PUSH1 0x10" +
                        " PUSH1 0x05 " +
                        " ADD" +
                        " PUSH1 0x40" +
                        " MSTORE " +
                        " PUSH1 0x20 " +
                        " PUSH1 0x40" +
                        " RETURN"
        ), null);
        program = getProgram(compile(
                " PUSH1 0x20" +  // return size is 32 bytes
                        " PUSH1 0x40" +       // on free memory pointer
                        " PUSH1 0x00" +       // no argument
                        " PUSH1 0x00" +       // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() + // in the mock contract specified above
                        " PUSH4 0x005B8D80" + // with some gas
                        " STATICCALL" +       // call it! result should be 0x15
                        " PUSH1 0x20" +  // return size is 32 bytes
                        " PUSH1 0x40" +       // on free memory pointer
                        " PUSH1 0x00" +       // no argument
                        " PUSH1 0x00" +       // no argument size
                        " PUSH4 0xffffffff" +       // with a high value
                        " PUSH20 0x" + contract +
                        " PUSH4 0x005B8D80" + // with some gas
                        " CALL" +       // call it! result should be 0x15
                        " RETURNDATASIZE" // push the return data size to the stack
        ));
        when(program.getActivations().isActive(ConsensusRule.RSKIP171)).thenReturn(irisHardForkActive);
        when(program.getActivations().isActive(ConsensusRule.RSKIP119)).thenReturn(true);
        vm.steps(program, Long.MAX_VALUE);
        assertEquals(expectedReturnDataSize, program.stackPop().intValue());
    }

    @Test
    void returnDataBufferAfterEnoughGasAtPrecompiled() {
        doCallToPrecompiledEnoughGas(true, 0);
    }

    @Test
    void beforeIrisReturnDataBufferAfterEnoughGasAtPrecompiled() {
        doCallToPrecompiledEnoughGas(false, 32);
    }

    private void doCallToPrecompiledEnoughGas(boolean irisHardForkActive, int expectedReturnDataSize) {
        invoke = new ProgramInvokeMockImpl(compile(
                "PUSH1 0x10" +
                        " PUSH1 0x05 " +
                        " ADD" +
                        " PUSH1 0x40" +
                        " MSTORE " +
                        " PUSH1 0x20 " +
                        " PUSH1 0x40" +
                        " RETURN"
        ), null);
        program = getProgramWithTransaction(compile(
                " PUSH1 0x20" +  // return size is 32 bytes
                        " PUSH1 0x40" +       // on free memory pointer
                        " PUSH1 0x00" +       // no argument
                        " PUSH1 0x00" +       // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() + // in the mock contract specified above
                        " PUSH4 0x005B8D80" + // with some gas
                        " STATICCALL" +       // call it! result should be 0x15
                        " PUSH1 0x20" + // return size is 32 bytes
                        " PUSH1 0x40" + // on free memory pointer
                        " PUSH1 0x00" + // no argument
                        " PUSH1 0x00" + // no argument size
                        " PUSH1 0x00" + // with no value
                        " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                        " PUSH1 0x00" + // without gas!
                        " CALL" +
                        " RETURNDATASIZE" // push the return data size to the stack
        ), TransactionUtils.createTransaction());
        when(program.getActivations().isActive(ConsensusRule.RSKIP171)).thenReturn(irisHardForkActive);
        when(program.getActivations().isActive(ConsensusRule.RSKIP119)).thenReturn(true);
        vm.steps(program, Long.MAX_VALUE);
        assertEquals(expectedReturnDataSize, program.stackPop().intValue());
    }

    @Test
    void testReturnDataCopyChargesCorrectGas() {
        invoke = new ProgramInvokeMockImpl(compile("" +
                " PUSH1 0x01 PUSH1 0x02 SUB PUSH1 0x00 MSTORE" +
                " PUSH1 0x20 PUSH1 0x00 RETURN"

        ), null);
        Program goodProgram = getProgram(compile("" +
                " PUSH1 0x20 " + // return data len
                " PUSH1 0x00 " + // return data position
                " PUSH1 0x00" + // input size
                " PUSH1 0x00" + // input position
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH1 0xFF" + // with some gas
                " STATICCALL" +
                " PUSH1 0x20" + // return data size, honest!
                " PUSH1 0x00 " + // return data offset
                " PUSH1 0x20" + // memory offset
                " RETURNDATACOPY" +
                " PUSH1 0x20" +
                " PUSH1 0x20" +
                " RETURN"
        ));

        Program badProgram = getProgram(compile("" +
                " PUSH1 0x20 " + // return data len
                " PUSH1 0x00 " + // return data position
                " PUSH1 0x00" + // input size
                " PUSH1 0x00" + // input position
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH1 0xFF" + // with some gas
                " STATICCALL" +
                " PUSH1 0x00" + // return data size, dishonest bad lying bytecode
                " PUSH1 0x00 " + // return data offset
                " PUSH1 0x20" + // memory offset
                " RETURNDATACOPY" +
                " PUSH1 0x20" +
                " PUSH1 0x20" +
                " RETURN"
        ));
        vm.steps(goodProgram, Long.MAX_VALUE);
        vm.steps(badProgram, Long.MAX_VALUE);

        // i should expected 32 bytes to store the value
        // and then 32 to copy the value in return data copy.
        assertEquals(64, goodProgram.getMemSize(), "good program has 64 mem");
        assertEquals(64, badProgram.getMemSize(), "bad program has 64 mem");
        // bad program is trying to put a word of memory on the last byte of memory,
        // but should not be successful. we should not charge for gas that was not used!
        assertEquals(goodProgram.getResult().getGasUsed(),
                badProgram.getResult().getGasUsed() + 3, "good program uses 3 more gas than the bad one");
    }

    @Test
    void getFreeMemoryUsingPrecompiledContractLyingAboutReturnSize() {
        // just a first run to initialize the precompiled contract
        // and then compare the bad and good programs
        Program initContract = getProgram(compile(
                " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x01" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                        " PUSH1 0xFF" +
                        " CALL"
        ));
        Program bad = getProgram(compile("" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" + // out size, note this is a lie, the precompiled WILL return some data
                " PUSH1 0x20" + // out off
                " PUSH1 0x01" + // in size
                " PUSH1 0x00" + // in off
                " PUSH1 0x00" +
                " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        Program good = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x20" + //out size, this is correct
                " PUSH1 0x20" + //out off
                " PUSH1 0x20" + //in size
                " PUSH1 0x00" + //in off
                " PUSH1 0x00" +
                " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        vm.steps(initContract, Long.MAX_VALUE);
        vm.steps(bad, Long.MAX_VALUE);
        vm.steps(good, Long.MAX_VALUE);
        Assertions.assertEquals(good.getResult().getGasUsed(),
                bad.getResult().getGasUsed() + GasCost.MEMORY, "good program will asign a new word of memory, so will charge 3 more");
        Assertions.assertEquals(good.getMemSize(), bad.getMemSize() + 32,
                "good program will have more memory, as it paid for it");
    }

    @Test
    void testCallDataCopyDoesNotExpandMemoryForFree() {
        invoke = new ProgramInvokeMockImpl(compile(
                " PUSH1 0x00 PUSH1 0x00 MSTORE " +
                        " PUSH1 0x20 PUSH1 0x00 RETURN"
        ), null);
        Program badProgram = getProgram(compile(
                " PUSH1 0x20" + // return size
                        " PUSH1 0x00" + // return place
                        " PUSH1 0x00" + // no argument
                        " PUSH1 0x00" + // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() +
                        " PUSH2 0xFFFF" +
                        " STATICCALL" +
                        " PUSH1 0x00" + // CALLDATA length
                        " PUSH1 0x00" + // CALLDATA offset
                        " PUSH1 0x01 MSIZE SUB" + // put the calldata on the last byte :)
                        " CALLDATACOPY"
        ));
        Program goodProgram = getProgram(compile(
                " PUSH1 0x20" + // return size
                        " PUSH1 0x00" + // return place
                        " PUSH1 0x00" + // no argument
                        " PUSH1 0x00" + // no argument size
                        " PUSH20 0x" + invoke.getContractAddress() +
                        " PUSH2 0xFFFF" +
                        " STATICCALL" +
                        " PUSH1 0x20" + // CALLDATA length
                        " PUSH1 0x00" + // CALLDATA offset
                        " PUSH1 0x01 MSIZE SUB" + // put the calldata on the last byte :)
                        " CALLDATACOPY"
        ));
        vm.steps(goodProgram, Long.MAX_VALUE);
        vm.steps(badProgram, Long.MAX_VALUE);

        // make sure that the lying program memory has
        // not been expanded at all beyond the 32 bytes
        // need to place the return values for the STATICCALL
        assertEquals(32, badProgram.getMemSize());
        assertEquals(64, goodProgram.getMemSize());
    }

    @Test
    void getFreeMemoryUsingPrecompiledContractAndSettingFarOffOffset() {
        // just a first run to initialize the precompiled contract
        // and then compare the bad and good programs
        Program initContract = getProgram(compile(
                " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x01" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                        " PUSH1 0xFF" +
                        " CALL"
        ));
        Program bad = getProgram(compile("" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" + //out size, note this is a lie, the precompiled WILL return some data
                " PUSH4 0x01000000" + //out off, see how far off it is
                " PUSH1 0x01" + //in size
                " PUSH1 0x00" + //in off
                " PUSH1 0x00" +
                " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        Program good = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x01" + //out size
                " PUSH1 0x00" + //out off
                " PUSH1 0x01" + //in size
                " PUSH1 0x00" + //in off
                " PUSH1 0x00" +
                " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        vm.steps(initContract, Long.MAX_VALUE);
        vm.steps(bad, Long.MAX_VALUE);
        vm.steps(good, Long.MAX_VALUE);
        Assertions.assertEquals(good.getResult().getGasUsed(), bad.getResult().getGasUsed());
        Assertions.assertEquals(good.getMemSize(), bad.getMemSize());
    }


    @Test
    void testSTATICCALLWithStatusOneFailsWithOldCode() {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 SUB"), null);
        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " STATICCALL"), null, true);

        program.fullTrace();

        Assertions.assertThrows(EmptyStackException.class, () -> vm.steps(program, Long.MAX_VALUE));
    }

    @Test
    void testSTATICCALLWithStatusOneAndAdditionalValueInStackUsingPreFixStaticCall() {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 SUB"), null);
        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " STATICCALL"), null, true);

        program.fullTrace();
        vm.steps(program, Long.MAX_VALUE);

        assertEquals(DataWord.ONE, program.stackPop());
        assertTrue(program.getStack().isEmpty());
    }

    @Test
    void testSTATICCALLWithStatusOneAndAdditionalValueInStackUsingFixStaticCallLeavesValueInStack() {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 SUB"), null);
        program = getProgram(compile("PUSH1 0x2a" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " STATICCALL"));

        program.fullTrace();
        vm.steps(program, Long.MAX_VALUE);

        assertEquals(DataWord.ONE, program.stackPop());
        assertFalse(program.getStack().isEmpty());
        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.valueOf(42), program.getStack().pop());
    }

    @Test  // PUSH0 OP
    void testPUSH0() {
        program = getProgram(compile("PUSH0"));
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }
    @Test  // PUSH1 OP
    void testPUSH1() {

        program = getProgram(compile("PUSH1 0xa0"));
        String expected = "00000000000000000000000000000000000000000000000000000000000000A0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH2 OP
    void testPUSH2() {

        program = getProgram(compile("PUSH2 0xa0b0"));
        String expected = "000000000000000000000000000000000000000000000000000000000000A0B0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH3 OP
    void testPUSH3() {

        program = getProgram(compile("PUSH3 0xA0B0C0"));
        String expected = "0000000000000000000000000000000000000000000000000000000000A0B0C0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH4 OP
    void testPUSH4() {

        program = getProgram(compile("PUSH4 0xA0B0C0D0"));
        String expected = "00000000000000000000000000000000000000000000000000000000A0B0C0D0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH5 OP
    void testPUSH5() {

        program = getProgram(compile("PUSH5 0xA0B0C0D0E0"));
        String expected = "000000000000000000000000000000000000000000000000000000A0B0C0D0E0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH6 OP
    void testPUSH6() {

        program = getProgram(compile("PUSH6 0xA0B0C0D0E0F0"));
        String expected = "0000000000000000000000000000000000000000000000000000A0B0C0D0E0F0";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH7 OP
    void testPUSH7() {

        program = getProgram(compile("PUSH7 0xA0B0C0D0E0F0A1"));
        String expected = "00000000000000000000000000000000000000000000000000A0B0C0D0E0F0A1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH8 OP
    void testPUSH8() {

        program = getProgram(compile("PUSH8 0xA0B0C0D0E0F0A1B1"));
        String expected = "000000000000000000000000000000000000000000000000A0B0C0D0E0F0A1B1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH9 OP
    void testPUSH9() {

        program = getProgram(compile("PUSH9 0xA0B0C0D0E0F0A1B1C1"));
        String expected = "0000000000000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test  // PUSH10 OP
    void testPUSH10() {

        program = getProgram(compile("PUSH10 0xA0B0C0D0E0F0A1B1C1D1"));
        String expected = "00000000000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH11 OP
    void testPUSH11() {

        program = getProgram(compile("PUSH11 0xA0B0C0D0E0F0A1B1C1D1E1"));
        String expected = "000000000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH12 OP
    void testPUSH12() {

        program = getProgram(compile("PUSH12 0xA0B0C0D0E0F0A1B1C1D1E1F1"));
        String expected = "0000000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH13 OP
    void testPUSH13() {

        program = getProgram(compile("PUSH13 0xA0B0C0D0E0F0A1B1C1D1E1F1A2"));
        String expected = "00000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH14 OP
    void testPUSH14() {

        program = getProgram(compile("PUSH14 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2"));
        String expected = "000000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH15 OP
    void testPUSH15() {

        program = getProgram(compile("PUSH15 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2"));
        String expected = "0000000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH16 OP
    void testPUSH16() {

        program = getProgram(compile("PUSH16 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2"));
        String expected = "00000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH17 OP
    void testPUSH17() {

        program = getProgram(compile("PUSH17 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2"));
        String expected = "000000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH18 OP
    void testPUSH18() {

        program = getProgram(compile("PUSH18 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2"));
        String expected = "0000000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH19 OP
    void testPUSH19() {

        program = getProgram(compile("PUSH19 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3"));
        String expected = "00000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH20 OP
    void testPUSH20() {

        program = getProgram(compile("PUSH20 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3"));
        String expected = "000000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH21 OP
    void testPUSH21() {

        program = getProgram(compile("PUSH21 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3"));
        String expected = "0000000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH22 OP
    void testPUSH22() {

        program = getProgram(compile("PUSH22 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3"));
        String expected = "00000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH23 OP
    void testPUSH23() {

        program = getProgram(compile("PUSH23 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3"));
        String expected = "000000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH24 OP
    void testPUSH24() {

        program = getProgram(compile("PUSH24 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3"));
        String expected = "0000000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH25 OP
    void testPUSH25() {

        program = getProgram(compile("PUSH25 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4"));
        String expected = "00000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH26 OP
    void testPUSH26() {

        program = getProgram(compile("PUSH26 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4"));
        String expected = "000000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH27 OP
    void testPUSH27() {

        program = getProgram(compile("PUSH27 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4"));
        String expected = "0000000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH28 OP
    void testPUSH28() {

        program = getProgram(compile("PUSH28 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4"));
        String expected = "00000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH29 OP
    void testPUSH29() {

        program = getProgram(compile("PUSH29 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4"));
        String expected = "000000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH30 OP
    void testPUSH30() {

        program = getProgram(compile("PUSH30 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4"));
        String expected = "0000A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH31 OP
    void testPUSH31() {

        program = getProgram(compile("PUSH31 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1"));
        String expected = "00A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // PUSH32 OP
    void testPUSH32() {

        program = getProgram(compile("PUSH32 0xA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B1"));
        String expected = "A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B1";

        program.fullTrace();
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // PUSHN OP not enough data
    void testPUSHN_1() {

        program = getProgram(Hex.decode("61AA"));
        //getProgram("61AA");
        String expected = "000000000000000000000000000000000000000000000000000000000000AA00";

        program.fullTrace();
        vm.step(program);

        assertTrue(program.isStopped());
        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // PUSHN OP not enough data
    void testPUSHN_2() {

        program = getProgram("7fAABB");
        String expected = "AABB000000000000000000000000000000000000000000000000000000000000";

        program.fullTrace();
        vm.step(program);

        assertTrue(program.isStopped());
        String result = ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase();
        assertEquals(expected, result);
    }

    @Test  // AND OP
    void testAND_1() {

        program = getProgram("600A600A16");

        String expected = "000000000000000000000000000000000000000000000000000000000000000A";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // AND OP
    void testAND_2() {

        program = getProgram("60C0600A16");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // AND OP mal data
    void testAND_3() {
        program = getProgram("60C016");
        try {
            vm.step(program);
            Assertions.assertThrows(RuntimeException.class, () -> {
                vm.step(program);
            });
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test  // OR OP
    void testOR_1() {

        program = getProgram("60F0600F17");
        String expected = "00000000000000000000000000000000000000000000000000000000000000FF";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // OR OP
    void testOR_2() {

        program = getProgram("60C3603C17");
        String expected = "00000000000000000000000000000000000000000000000000000000000000FF";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // OR OP mal data
    void testOR_3() {
        program = getProgram("60C017");
        vm.step(program);
        Assertions.assertThrows(RuntimeException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test  // XOR OP
    void testXOR_1() {

        program = getProgram("60FF60FF18");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // XOR OP
    void testXOR_2() {

        program = getProgram("600F60F018");
        String expected = "00000000000000000000000000000000000000000000000000000000000000FF";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test  // XOR OP mal data
    void testXOR_3() {
        program = getProgram("60C018");
        vm.step(program);
        Assertions.assertThrows(RuntimeException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test  // BYTE OP
    void testBYTE_1() {

        program = getProgram("65AABBCCDDEEFF601E1A");
        String expected = "00000000000000000000000000000000000000000000000000000000000000EE";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // BYTE OP
    void testBYTE_2() {

        program = getProgram("65AABBCCDDEEFF60201A");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // BYTE OP
    void testBYTE_3() {

        program = getProgram("65AABBCCDDEE3A601F1A");
        String expected = "000000000000000000000000000000000000000000000000000000000000003A";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test  // BYTE OP mal data
    void testBYTE_4() {

        program = getProgram("65AABBCCDDEE3A1A");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> {
                vm.step(program);
            });
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test  // ISZERO OP
    void testISZERO_1() {

        program = getProgram("600015");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // ISZERO OP
    void testISZERO_2() {

        program = getProgram("602A15");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // ISZERO OP mal data
    void testISZERO_3() {
        program = getProgram("15");
        try {
            Assertions.assertThrows(StackTooSmallException.class, () -> {
                vm.step(program);
            });
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test  // EQ OP
    void testEQ_1() {

        program = getProgram("602A602A14");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // EQ OP
    void testEQ_2() {

        program = getProgram("622A3B4C622A3B4C14");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // EQ OP
    void testEQ_3() {

        program = getProgram("622A3B5C622A3B4C14");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // EQ OP mal data
    void testEQ_4() {

        program = getProgram("622A3B4C14");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test  // GT OP
    void testGT_1() {

        program = getProgram("6001600211");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // GT OP
    void testGT_2() {

        program = getProgram("6001610F0011");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // GT OP
    void testGT_3() {

        program = getProgram("6301020304610F0011");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // GT OP mal data
    void testGT_4() {

        program = getProgram("622A3B4C11");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test  // SGT OP
    void testSGT_1() {

        program = getProgram("6001600213");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SGT OP
    void testSGT_2() {

        program = getProgram("7F000000000000000000000000000000000000000000000000000000000000001E" + //   30
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "13");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SGT OP
    void testSGT_3() {

        program = getProgram("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF57" + // -169
                "13");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SGT OP mal
    void testSGT_4() {
        program = getProgram("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "13");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test  // LT OP
    void testLT_1() {

        program = getProgram("6001600210");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // LT OP
    void testLT_2() {

        program = getProgram("6001610F0010");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // LT OP
    void testLT_3() {

        program = getProgram("6301020304610F0010");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // LT OP mal data
    void testLT_4() {
        program = getProgram("622A3B4C10");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test  // SLT OP
    void testSLT_1() {

        program = getProgram("6001600212");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SLT OP
    void testSLT_2() {

        program = getProgram("7F000000000000000000000000000000000000000000000000000000000000001E" + //   30
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "12");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SLT OP
    void testSLT_3() {

        program = getProgram("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF57" + // -169
                "12");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // SLT OP mal
    void testSLT_4() {
        program = getProgram("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "12");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test  // NOT OP
    void testNOT_1() {

        program = getProgram("600119");
        String expected = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE";

        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // NOT OP
    void testNOT_2() {

        program = getProgram("61A00319");
        String expected = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5FFC";

        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test  // BNOT OP
    void testBNOT_4() {
        program = getProgram("1a");

        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));

        assertTrue(program.isStopped());
    }

    @Test  // NOT OP test from real failure
    void testNOT_5() {

        program = getProgram("600019");
        String expected = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test // POP OP
    void testPOP_1() {

        program = getProgram("61000060016200000250");
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // POP OP
    void testPOP_2() {

        program = getProgram("6100006001620000025050");
        String expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test  // POP OP mal data
    void testPOP_3() {

        program = getProgram("61000060016200000250505050");
        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // DUP1...DUP16 OP
    void testDUPS() {
        for (int i = 1; i < 17; i++) {
            testDUPN_1(i);
        }
    }

    /**
     * Generic test function for DUP1-16
     *
     * @param n in DUPn
     */
    private void testDUPN_1(int n) {

        byte operation = (byte) (OpCode.DUP1.val() + n - 1);
        String programCode = "";
        for (int i = 0; i < n; i++) {
            programCode += "60" + (12 + i);
        }
        program = getProgram(ByteUtil.appendByte(Hex.decode(programCode.getBytes()), operation));
        String expected = "0000000000000000000000000000000000000000000000000000000000000012";
        int expectedLen = n + 1;

        for (int i = 0; i < expectedLen; i++) {
            vm.step(program);
        }

        assertEquals(expectedLen, program.getStack().toArray().length);
        assertEquals(expected, ByteUtil.toHexString(program.stackPop().getData()).toUpperCase());
        for (int i = 0; i < expectedLen - 2; i++) {
            Assertions.assertNotEquals(expected, ByteUtil.toHexString(program.stackPop().getData()).toUpperCase());
        }
        assertEquals(expected, ByteUtil.toHexString(program.stackPop().getData()).toUpperCase());
    }

    @Test  // DUPN OP mal data
    void testDUPN_2() {

        program = getProgram("80");
        try {
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // SWAP1...SWAP16 OP
    void testSWAPS() {
        for (int i = 1; i < 17; ++i) {
            testSWAPN_1(i);
        }
    }

    /**
     * Generic test function for SWAP1-16
     *
     * @param n in SWAPn
     */
    private void testSWAPN_1(int n) {

        byte operation = (byte) (OpCode.SWAP1.val() + n - 1);

        String programCode = "";
        String top = DataWord.valueOf(0x10 + n).toString();
        for (int i = n; i > -1; --i) {
            programCode += "60" + oneByteToHexString((byte) (0x10 + i));

        }

        programCode += ByteUtil.toHexString(new byte[]{(byte) (OpCode.SWAP1.val() + n - 1)});

        program = getProgram(ByteUtil.appendByte(Hex.decode(programCode), operation));

        for (int i = 0; i < n + 2; ++i) {
            vm.step(program);
        }

        assertEquals(n + 1, program.getStack().toArray().length);
        assertEquals(top, ByteUtil.toHexString(program.stackPop().getData()));
    }

    @Test  // SWAPN OP mal data
    void testSWAPN_2() {

        program = getProgram("90");

        try {
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // MSTORE OP
    void testMSTORE_1() {

        program = getProgram("611234600052");
        String expected = "0000000000000000000000000000000000000000000000000000000000001234";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getMemory()));
    }


    @Test // LOG0 OP
    public void tesLog0() {

        program = getProgram("61123460005260206000A0");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        List<LogInfo> logInfoList = program.getResult().getLogInfoList();
        LogInfo logInfo = logInfoList.get(0);

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(logInfo.getAddress()));
        assertEquals(0, logInfo.getTopics().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000001234", ByteUtil.toHexString(logInfo
                .getData()));
    }

    @Test // LOG1 OP
    public void tesLog1() {

        program = getProgram("61123460005261999960206000A1");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        List<LogInfo> logInfoList = program.getResult().getLogInfoList();
        LogInfo logInfo = logInfoList.get(0);

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(logInfo.getAddress()));
        assertEquals(1, logInfo.getTopics().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000001234", ByteUtil.toHexString(logInfo
                .getData()));
    }

    @Test // LOG2 OP
    public void tesLog2() {

        program = getProgram("61123460005261999961666660206000A2");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        List<LogInfo> logInfoList = program.getResult().getLogInfoList();
        LogInfo logInfo = logInfoList.get(0);

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(logInfo.getAddress()));
        assertEquals(2, logInfo.getTopics().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000001234", ByteUtil.toHexString(logInfo
                .getData()));
    }

    @Test // LOG3 OP
    public void tesLog3() {

        program = getProgram("61123460005261999961666661333360206000A3");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        List<LogInfo> logInfoList = program.getResult().getLogInfoList();
        LogInfo logInfo = logInfoList.get(0);

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(logInfo.getAddress()));
        assertEquals(3, logInfo.getTopics().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000001234", ByteUtil.toHexString(logInfo
                .getData()));
    }


    @Test // LOG4 OP
    public void tesLog4() {

        program = getProgram("61123460005261999961666661333361555560206000A4");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        List<LogInfo> logInfoList = program.getResult().getLogInfoList();
        LogInfo logInfo = logInfoList.get(0);

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(logInfo.getAddress()));
        assertEquals(4, logInfo.getTopics().size());
        assertEquals("0000000000000000000000000000000000000000000000000000000000001234", ByteUtil.toHexString(logInfo
                .getData()));
    }


    @Test // MSTORE OP
    void testMSTORE_2() {

        program = getProgram("611234600052615566602052");
        String expected = "0000000000000000000000000000000000000000000000000000000000001234" +
                "0000000000000000000000000000000000000000000000000000000000005566";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getMemory()));
    }

    @Test // MSTORE OP
    void testMSTORE_3() {

        program = getProgram("611234600052615566602052618888600052");
        String expected = "0000000000000000000000000000000000000000000000000000000000008888" +
                "0000000000000000000000000000000000000000000000000000000000005566";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getMemory()));
    }

    @Test // MSTORE OP
    void testMSTORE_4() {

        program = getProgram("61123460A052");
        String expected = "" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000001234";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(expected, ByteUtil.toHexString(program.getMemory()));
    }

    @Test // MSTORE OP
    void testMSTORE_5() {

        program = getProgram("61123452");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // MLOAD OP
    void testMLOAD_1() {

        program = getProgram("600051");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // MLOAD OP
    void testMLOAD_2() {

        program = getProgram("602251");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()).toUpperCase());
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test // MLOAD OP
    void testMLOAD_3() {

        program = getProgram("602051");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // MLOAD OP
    void testMLOAD_4() {

        program = getProgram("611234602052602051");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000001234";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000001234";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // MLOAD OP
    void testMLOAD_5() {

        program = getProgram("611234602052601F51");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000001234";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000012";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    // Testes the versioning header, specifying version 0
    @Test
    void testVersioning_1() {

        program = getProgram("FC010100" // this is the header
                + "611234602052601F51");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000001234";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000012";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(1, program.getExeVersion());
        assertEquals(1, program.getScriptVersion());
        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    // Test for currectness of extra header length when over 128 (negative byte)
    @Test
    void testVersioning_2() {

        byte[] header = Hex.decode("FC810180"); // test negative exeVersion also, 0x80 byte to skip

        byte[] skip = new byte[128]; // filled with zeros

        byte[] tail = Hex.decode("611234602052601F51");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(header);
            outputStream.write(skip);
            outputStream.write(tail);
        } catch (IOException e) {
            e.printStackTrace();
            Assertions.assertTrue(false);
        }

        byte code[] = outputStream.toByteArray();

        program = getProgram(code);
        // no negative values allowed. Currently values over 127 are limited
        // in the future exeversion and scriptversion can be made of size int.
        Assertions.assertEquals(127, program.getExeVersion());
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000001234";
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000012";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test
    void testInvalidOpcodes_1() {
        program = getProgram("A5");

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> vm.step(program));
    }

    @Test // MLOAD OP mal data
    void testMLOAD_6() {

        program = getProgram("51");
        try {
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // MSTORE8 OP
    void testMSTORE8_1() {

        program = getProgram("6011600053");
        String m_expected = "1100000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
    }


    @Test // MSTORE8 OP
    void testMSTORE8_2() {

        program = getProgram("6022600153");
        String m_expected = "0022000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
    }

    @Test // MSTORE8 OP
    void testMSTORE8_3() {

        program = getProgram("6022602153");
        String m_expected = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0022000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected, ByteUtil.toHexString(program.getMemory()));
    }

    @Test // MSTORE8 OP mal
    void testMSTORE8_4() {

        program = getProgram("602253");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // SSTORE OP
    void testSSTORE_1() {

        program = getProgram("602260AA55");
        String s_expected_key = "00000000000000000000000000000000000000000000000000000000000000AA";
        String s_expected_val = "0000000000000000000000000000000000000000000000000000000000000022";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord key = DataWord.valueOf(Hex.decode(s_expected_key));
        DataWord val = program.getStorage().getStorageValue(new RskAddress(invoke.getOwnerAddress()), key);

        assertEquals(s_expected_val, ByteUtil.toHexString(val.getData()).toUpperCase());
    }

    @Test // SSTORE OP
    void testSSTORE_2() {

        program = getProgram("602260AA55602260BB55");
        String s_expected_key = "00000000000000000000000000000000000000000000000000000000000000BB";
        String s_expected_val = "0000000000000000000000000000000000000000000000000000000000000022";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        Repository repository = program.getStorage();
        DataWord key = DataWord.valueOf(Hex.decode(s_expected_key));
        DataWord val = repository.getStorageValue(new RskAddress(invoke.getOwnerAddress()), key);

        assertEquals(s_expected_val, ByteUtil.toHexString(val.getData()).toUpperCase());
    }

    @Test // SSTORE OP
    void testSSTORE_3() {

        program = getProgram("602255");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // SLOAD OP
    void testSLOAD_1() {

        program = getProgram("60AA54");
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // SLOAD OP
    void testSLOAD_2() {

        program = getProgram("602260AA5560AA54");
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000022";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // SLOAD OP
    void testSLOAD_3() {

        program = getProgram("602260AA55603360CC5560CC54");
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000033";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // SLOAD OP
    void testSLOAD_4() {

        program = getProgram("56");
        try {
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // PC OP
    void testPC_1() {

        program = getProgram("58");
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test // PC OP
    void testPC_2() {

        program = getProgram("602260AA5260AA5458");
        String s_expected = "0000000000000000000000000000000000000000000000000000000000000008";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    @Test // JUMP OP mal data
    void testJUMP_1() {
        program = getProgram("60AA60BB600E5660CC60DD60EE5B60FF");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        Assertions.assertThrows(BadJumpDestinationException.class, () -> vm.step(program));
    }

    @Test // JUMP OP mal data
    void testJUMP_2() {
        program = getProgram("600C600C905660CC60DD60EE60FF");
        vm.step(program);
        vm.step(program);
        vm.step(program);
        Assertions.assertThrows(BadJumpDestinationException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // JUMPI OP
    void testJUMPI_1() {

        program = getProgram("60016005575B60CC");
        String s_expected = "00000000000000000000000000000000000000000000000000000000000000CC";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }


    @Test // JUMPI OP
    void testJUMPI_2() {

        program = getProgram("630000000060445760CC60DD");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000DD";
        String s_expected_2 = "00000000000000000000000000000000000000000000000000000000000000CC";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        DataWord item2 = program.stackPop();

        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
        assertEquals(s_expected_2, ByteUtil.toHexString(item2.getData()).toUpperCase());
    }

    @Test // JUMPI OP mal
    void testJUMPI_3() {

        program = getProgram("600157");
        try {
            vm.step(program);
            Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // JUMPI OP mal
    void testJUMPI_4() {

        program = getProgram("60016022909057");
        try {
            vm.step(program);
            vm.step(program);
            vm.step(program);
            vm.step(program);
            Assertions.assertThrows(BadJumpDestinationException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test // JUMP OP mal data
    void testJUMPDEST_1() {
        program = getProgram("602360085660015b600255");

        vm.step(program);
        vm.step(program);
        Assertions.assertThrows(BadJumpDestinationException.class, () -> {
            vm.step(program);
        });
    }

    @Test // JUMPDEST OP for JUMPI
    void testJUMPDEST_2() {

        program = getProgram("6023600160095760015b600255");

        String s_expected_key = "0000000000000000000000000000000000000000000000000000000000000002";
        String s_expected_val = "0000000000000000000000000000000000000000000000000000000000000023";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord key = DataWord.valueOf(Hex.decode(s_expected_key));
        DataWord val = program.getStorage().getStorageValue(new RskAddress(invoke.getOwnerAddress()), key);

        assertTrue(program.isStopped());
        assertEquals(s_expected_val, ByteUtil.toHexString(val.getData()).toUpperCase());
    }

    @Test // ADD OP mal
    void testADD_1() {

        program = getProgram("6002600201");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000004";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADD OP
    void testADD_2() {

        program = getProgram("611002600201");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000001004";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADD OP
    void testADD_3() {

        program = getProgram("6110026512345678900901");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000012345678A00B";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADD OP mal
    void testADD_4() {

        program = getProgram("61123401");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // ADDMOD OP mal
    void testADDMOD_1() {
        program = getProgram("60026002600308");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertTrue(program.isStopped());
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADDMOD OP
    void testADDMOD_2() {
        program = getProgram("6110006002611002086000");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000004";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertFalse(program.isStopped());
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADDMOD OP
    void testADDMOD_3() {
        program = getProgram("61100265123456789009600208");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000000000000093B";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertTrue(program.isStopped());
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // ADDMOD OP mal
    void testADDMOD_4() {
        program = getProgram("61123408");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // MUL OP
    void testMUL_1() {
        program = getProgram("6003600202");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000006";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MUL OP
    void testMUL_2() {
        program = getProgram("62222222600302");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000666666";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MUL OP
    void testMUL_3() {

        program = getProgram("622222226233333302");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000006D3A05F92C6";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MUL OP mal
    void testMUL_4() {

        program = getProgram("600102");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // MULMOD OP
    void testMULMOD_1() {
        program = getProgram("60036002600409");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000002";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MULMOD OP
    void testMULMOD_2() {
        program = getProgram("622222226003600409");
        String s_expected_1 = "000000000000000000000000000000000000000000000000000000000000000C";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MULMOD OP
    void testMULMOD_3() {
        program = getProgram("62222222623333336244444409");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MULMOD OP mal
    void testMULMOD_4() {
        program = getProgram("600109");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // DIV OP
    void testDIV_1() {

        program = getProgram("6002600404");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000002";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // DIV OP
    void testDIV_2() {

        program = getProgram("6033609904");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000003";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }


    @Test // DIV OP
    void testDIV_3() {

        program = getProgram("6022609904");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000004";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // DIV OP
    void testDIV_4() {

        program = getProgram("6015609904");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000007";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }


    @Test // DIV OP
    void testDIV_5() {

        program = getProgram("6004600704");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // DIV OP
    void testDIV_6() {

        program = getProgram("600704");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // SDIV OP
    void testSDIV_1() {

        program = getProgram("6103E87FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC1805" +
                "");
        String s_expected_1 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SDIV OP
    void testSDIV_2() {

        program = getProgram("60FF60FF05");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SDIV OP
    void testSDIV_3() {


        program = getProgram("600060FF05");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SDIV OP mal
    void testSDIV_4() {

        program = getProgram("60FF05");

        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // SUB OP
    void testSUB_1() {

        program = getProgram("6004600603");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000002";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SUB OP
    void testSUB_2() {

        program = getProgram("61444461666603");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000002222";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SUB OP
    void testSUB_3() {

        program = getProgram("614444639999666603");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000099992222";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SUB OP mal
    void testSUB_4() {

        program = getProgram("639999666603");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // MSIZE OP
    void testMSIZE_1() {


        program = getProgram("59");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MSIZE OP
    void testMSIZE_2() {


        program = getProgram("602060305259");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000060";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }


    @Test // STOP OP
    void testSTOP_1() {


        program = getProgram("60206030601060306011602300");
        int expectedSteps = 7;

        int i = 0;
        while (!program.isStopped()) {

            vm.step(program);
            ++i;
        }
        assertEquals(expectedSteps, i);
    }

    @Test
    void testEXP_1() {


        program = getProgram("600360020a");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000008";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        long gas = program.getResult().getGasUsed();

        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
        assertEquals(66, gas);
    }

    @Test
    void testEXP_2() {


        program = getProgram("6000621234560a");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        long gas = program.getResult().getGasUsed();

        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
        assertEquals(16, gas);
    }

    @Test
    void testEXP_3() {


        program = getProgram("61112260010a");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        long gas = program.getResult().getGasUsed();

        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
        assertEquals(116, gas);
    }


    @Test // EXP OP mal
    void testEXP_4() {
        program = getProgram("621234560a");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // RETURN OP
    void testRETURN_1() {


        program = getProgram("61123460005260206000F3");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000001234";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected_1, ByteUtil.toHexString(program.getResult().getHReturn()).toUpperCase());
        assertTrue(program.isStopped());
    }


    @Test // RETURN OP
    void testRETURN_2() {


        program = getProgram("6112346000526020601FF3");
        String s_expected_1 = "3400000000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected_1, ByteUtil.toHexString(program.getResult().getHReturn()).toUpperCase());
        assertTrue(program.isStopped());
    }

    @Test // RETURN OP
    void testRETURN_3() {


        program = getProgram("7FA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B160005260206000F3");
        String s_expected_1 = "A0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B1";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected_1, ByteUtil.toHexString(program.getResult().getHReturn()).toUpperCase());
        assertTrue(program.isStopped());
    }


    @Test // RETURN OP
    void testRETURN_4() {


        program = getProgram("7FA0B0C0D0E0F0A1B1C1D1E1F1A2B2C2D2E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B160005260206010F3");
        String s_expected_1 = "E2F2A3B3C3D3E3F3A4B4C4D4E4F4A1B100000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(s_expected_1, ByteUtil.toHexString(program.getResult().getHReturn()).toUpperCase());
        assertTrue(program.isStopped());
    }

    @Disabled("//TODO #POC9")
    @Test // CODECOPY OP
    void testCODECOPY_1() {
        program = getProgram("60036007600039123456");
        String m_expected_1 = "1234560000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        long gas = program.getResult().getGasUsed();
        assertEquals(m_expected_1, ByteUtil.toHexString(program.getMemory()).toUpperCase());
        assertEquals(6, gas);
    }

    @Disabled("//TODO #POC9")
    @Test // CODECOPY OP
    void testCODECOPY_2() {
        program = getProgram("605E60076000396000605f556014600054601e60205463abcddcba6040545b51602001" +
                "600a5254516040016014525451606001601e5254516080016028525460a05254601660" +
                "4860003960166000f26000603f556103e75660005460005360200235602054");

        String m_expected_1 = "6000605F556014600054601E60205463ABCDDCBA6040545B51602001600A5254516040016" +
                "014525451606001601E5254516080016028525460A0525460166048600039" +
                "60166000F26000603F556103E756600054600053602002356020540000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        long gas = program.getResult().getGasUsed();
        assertEquals(m_expected_1, ByteUtil.toHexString(program.getMemory()).toUpperCase());
        assertEquals(10, gas);
    }

    @Disabled("//TODO #POC9")
    @Test // CODECOPY OP
    void testCODECOPY_3() {
        // cost for that:
        // 94 - data copied
        // 95 - new bytes allocated


        program = getProgram("605E60076000396000605f556014600054601e60205463abcddcba6040545b51602001" +
                "600a5254516040016014525451606001601e5254516080016028525460a0525460166048600" +
                "03960166000f26000603f556103e75660005460005360200235");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(10, program.getResult().getGasUsed());
    }

    @Disabled("//TODO #POC9")
    @Test // CODECOPY OP
    void testCODECOPY_4() {
        program = getProgram("605E60076000396000605f556014600054601e60205463abcddcba6040545b51602001600a5254" +
                "516040016014525451606001601e5254516080016028525460a052546016604860003960166000f260006" +
                "03f556103e756600054600053602002351234");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(10, program.getResult().getGasUsed());
    }


    @Test // CODECOPY OP
    void testCODECOPY_5() {
        program = getProgram("611234600054615566602054607060006020396000605f556014600054601e6020546" +
                "3abcddcba6040545b51602001600a5254516040016014525451606001601e5254516080016028525460a05" +
                "2546016604860003960166000f26000603f556103e756600054600053602002351234");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertFalse(program.isStopped());
    }


    @Test // CODECOPY OP mal
    void testCODECOPY_6() {
        program = getProgram("605E6007396000605f556014600054601e60205463abcddcba604054" +
                "5b51602001600a5254516040016014525451606001601e5254516080016028525460a" +
                "052546016604860003960166000f26000603f556103e756600054600053602002351234");

        vm.step(program);
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // EXTCODECOPY OP
    void testEXTCODECOPY_1() {
        program = getProgram("60036007600073471FD3AD3E9EEADEEC4608B92D16CE6B500704CC3C123456");
        String m_expected_1 = "6000600000000000000000000000000000000000000000000000000000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected_1, ByteUtil.toHexString(program.getMemory()).toUpperCase());
    }

    @Test // EXTCODECOPY OP
    void testEXTCODECOPY_2() {
        program = getProgram("603E6007600073471FD3AD3E9EEADEEC4608B92D16CE6B500704CC3C6000605f556014" +
                "600054601e60205463abcddcba6040545b51602001600a5254516040016014525451606001601e52545160" +
                "80016028525460a052546016604860003960166000f26000603f556103e75660005460005360200235602054");

        String m_expected_1 = "6000605F556014600054601E60205463ABCDDCBA6040545B5160200" +
                "1600A5254516040016014525451606001601E5254516080016028525460A0525460160000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected_1, ByteUtil.toHexString(program.getMemory()).toUpperCase());
    }

    @Test // EXTCODECOPY OP
    void testEXTCODECOPY_3() {
        program = getProgram("605E6007600073471FD3AD3E9EEADEEC4608B92D16CE6B500704CC3C60" +
                "00605f556014600054601e60205463abcddcba6040545b51602001600a525451604001601452545160" +
                "6001601e5254516080016028525460a052546016604860003960166000f26000603f556103e75660005460005360200235");

        String m_expected_1 = "6000605F556014600054601E60205463ABCDDCBA6040545B51602001600A5254516040016014" +
                "525451606001601E5254516080016028525460A052546016604860003" +
                "960166000F26000603F556103E756600054600053602002350000000000";

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertEquals(m_expected_1, ByteUtil.toHexString(program.getMemory()).toUpperCase());
    }

    @Test // EXTCODECOPY OP
    void testEXTCODECOPY_4() {
        program = getProgram("611234600054615566602054603E6000602073471FD3AD3E9EEADEEC4608B9" +
                "2D16CE6B500704CC3C6000605f556014600054601e60205463abcddcba6040545b51602001600a5" +
                "254516040016014525451606001601e5254516080016028525460a0525460166" +
                "04860003960166000f26000603f556103e756600054600053602002351234");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);
        vm.step(program);

        assertFalse(program.isStopped());
    }


    @Test // EXTCODECOPY OP mal
    void testEXTCODECOPY_5() {
        program = getProgram("605E600773471FD3AD3E9EEADEEC4608B92D16CE6B500704CC3C");

        vm.step(program);
        vm.step(program);
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }


    @Test // CODESIZE OP
    void testCODESIZE_1() {
        program = getProgram("385E60076000396000605f556014600054601e60205463abcddcba6040545b51602" +
                "001600a5254516040016014525451606001601e5254516080016028525460a05254601660486" +
                "0003960166000f26000603f556103e75660005460005360200235");

        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000062";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Disabled("// todo: test is not testing EXTCODESIZE")
    @Test // EXTCODESIZE OP
    void testEXTCODESIZE_1() {
        // Push address on the stack and perform EXTCODECOPY
        program = getProgram("73471FD3AD3E9EEADEEC4608B92D16CE6B500704CC395E60076000396000605f55" +
                "6014600054601e60205463abcddcba6040545b51602001600a5254516040016014525451606001" +
                "601e5254516080016028525460a052546016604860003960166000f26000603f556103e75660005460005360200235");

        String s_expected_1 = "000000000000000000000000471FD3AD3E9EEADEEC4608B92D16CE6B500704CC";

        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MOD OP
    void testMOD_1() {
        program = getProgram("6003600406");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MOD OP
    void testMOD_2() {

        program = getProgram("61012C6101F406");
        String s_expected_1 = "00000000000000000000000000000000000000000000000000000000000000C8";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MOD OP
    void testMOD_3() {

        program = getProgram("6004600206");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000002";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // MOD OP mal
    void testMOD_4() {
        program = getProgram("600406");

        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test // SMOD OP
    void testSMOD_1() {

        program = getProgram("6003600407");
        String s_expected_1 = "0000000000000000000000000000000000000000000000000000000000000001";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SMOD OP
    void testSMOD_2() {

        program = getProgram("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE2" + //  -30
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "07");
        String s_expected_1 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEC";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SMOD OP
    void testSMOD_3() {
        program = getProgram("7F000000000000000000000000000000000000000000000000000000000000001E" + //   30
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF56" + // -170
                "07");
        String s_expected_1 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEC";

        vm.step(program);
        vm.step(program);
        vm.step(program);

        DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, ByteUtil.toHexString(item1.getData()).toUpperCase());
    }

    @Test // SMOD OP mal
    void testSMOD_4() {
        program = getProgram("7F000000000000000000000000000000000000000000000000000000000000001E" + //   30
                "07");
        vm.step(program);
        Assertions.assertThrows(StackTooSmallException.class, () -> vm.step(program));
        assertTrue(program.isStopped());
    }

    @Test
    void regression1Test() {
        // testing that we are working fine with unknown 0xFE bytecode produced by Serpent compiler
        String code2 = "60006116bf537c01000000000000000000000000000000000000000000000000000000006000350463b041b2858114156101c257600435604052780100000000000000000000000000000000000000000000000060606060599059016000905260028152604051816020015260008160400152809050205404606052606051151561008f57600060a052602060a0f35b66040000000000015460c052600760e0525b60605178010000000000000000000000000000000000000000000000006060606059905901600090526002815260c05181602001526000816040015280905020540413156101b0575b60e05160050a60605178010000000000000000000000000000000000000000000000006060606059905901600090526002815260c05181602001526000816040015280905020540403121561014457600060e05113610147565b60005b1561015a57600160e0510360e0526100ea565b7c010000000000000000000000000000000000000000000000000000000060e05160200260020a6060606059905901600090526002815260c051816020015260018160400152809050205402045460c0526100a1565b60405160c05114610160526020610160f35b63720f60f58114156102435760043561018052601c60445990590160009052016305215b2f601c820352790100000000000000000000000000000000000000000000000000600482015260206101c0602483600061018051602d5a03f1506101c05190506604000000000003556604000000000003546101e05260206101e0f35b63b8c48f8c8114156104325760043560c05260243561020052604435610220526000660400000000000254141515610286576000610240526020610240f3610292565b60016604000000000002555b60c0516604000000000001556060606059905901600090526002815260c051816020015260008160400152809050205461026052610260610200518060181a82538060191a600183015380601a1a600283015380601b1a600383015380601c1a600483015380601d1a600583015380601e1a600683015380601f1a60078301535050610260516060606059905901600090526002815260c05181602001526000816040015280905020556060606059905901600090526002815260c051816020015260008160400152809050205461030052601061030001610220518060101a82538060111a60018301538060121a60028301538060131a60038301538060141a60048301538060151a60058301538060161a60068301538060171a60078301538060181a60088301538060191a600983015380601a1a600a83015380601b1a600b83015380601c1a600c83015380601d1a600d83015380601e1a600e83015380601f1a600f8301535050610300516060606059905901600090526002815260c051816020015260008160400152809050205560016103a05260206103a0f35b632b861629811415610eed57365990590160009052366004823760043560208201016103e0525060483580601f1a6104405380601e1a6001610440015380601d1a6002610440015380601c1a6003610440015380601b1a6004610440015380601a1a600561044001538060191a600661044001538060181a600761044001538060171a600861044001538060161a600961044001538060151a600a61044001538060141a600b61044001538060131a600c61044001538060121a600d61044001538060111a600e61044001538060101a600f610440015380600f1a6010610440015380600e1a6011610440015380600d1a6012610440015380600c1a6013610440015380600b1a6014610440015380600a1a601561044001538060091a601661044001538060081a601761044001538060071a601861044001538060061a601961044001538060051a601a61044001538060041a601b61044001538060031a601c61044001538060021a601d61044001538060011a601e61044001538060001a601f6104400153506104405161040052700100000000000000000000000000000000700100000000000000000000000000000000606060605990590160009052600281526104005181602001526000816040015280905020540204610460526104605161061b57005b6103e05160208103516020599059016000905260208183856000600287604801f15080519050905090506104a0526020599059016000905260208160206104a0600060026068f1508051905080601f1a6105605380601e1a6001610560015380601d1a6002610560015380601c1a6003610560015380601b1a6004610560015380601a1a600561056001538060191a600661056001538060181a600761056001538060171a600861056001538060161a600961056001538060151a600a61056001538060141a600b61056001538060131a600c61056001538060121a600d61056001538060111a600e61056001538060101a600f610560015380600f1a6010610560015380600e1a6011610560015380600d1a6012610560015380600c1a6013610560015380600b1a6014610560015380600a1a601561056001538060091a601661056001538060081a601761056001538060071a601861056001538060061a601961056001538060051a601a61056001538060041a601b61056001538060031a601c61056001538060021a601d61056001538060011a601e61056001538060001a601f6105600153506105605160c0527001000000000000000000000000000000007001000000000000000000000000000000006060606059905901600090526002815260c05181602001526000816040015280905020540204610580526000610580511415156108345760006105c05260206105c0f35b608c3563010000008160031a02620100008260021a026101008360011a028360001a01010190506105e05263010000006105e051046106405262ffffff6105e0511661066052600361064051036101000a610660510261062052600060c05113156108a6576106205160c051126108a9565b60005b15610ee05760c05160c05160c051660400000000000054556060606059905901600090526002815260c0518160200152600081604001528090502054610680526008610680016604000000000000548060181a82538060191a600183015380601a1a600283015380601b1a600383015380601c1a600483015380601d1a600583015380601e1a600683015380601f1a60078301535050610680516060606059905901600090526002815260c05181602001526000816040015280905020556001660400000000000054016604000000000000556060606059905901600090526002815260c051816020015260008160400152809050205461072052610720600178010000000000000000000000000000000000000000000000006060606059905901600090526002815261040051816020015260008160400152809050205404018060181a82538060191a600183015380601a1a600283015380601b1a600383015380601c1a600483015380601d1a600583015380601e1a600683015380601f1a60078301535050610720516060606059905901600090526002815260c051816020015260008160400152809050205560006107e052780100000000000000000000000000000000000000000000000068010000000000000000606060605990590160009052600281526104005181602001526000816040015280905020540204610800526107e06108005180601c1a825380601d1a600183015380601e1a600283015380601f1a600383015350506001610880525b6008610880511215610c07576108805160050a6108a05260016108a05178010000000000000000000000000000000000000000000000006060606059905901600090526002815260c051816020015260008160400152809050205404071415610b7957610880516004026107e0016108005180601c1a825380601d1a600183015380601e1a600283015380601f1a60038301535050610bf7565b610880516004026107e0017c01000000000000000000000000000000000000000000000000000000006108805160200260020a60606060599059016000905260028152610400518160200152600181604001528090502054020480601c1a825380601d1a600183015380601e1a600283015380601f1a600383015350505b6001610880510161088052610adf565b6107e0516060606059905901600090526002815260c051816020015260018160400152809050205550506080608059905901600090526002815260c051816020015260028160400152600081606001528090502060005b6002811215610c8057806020026103e051015182820155600181019050610c5e565b700100000000000000000000000000000000600003816020026103e051015116828201555050610620517bffff000000000000000000000000000000000000000000000000000005610a00526060606059905901600090526002815260c0518160200152600081604001528090502054610a20526010610a2001610a005161046051018060101a82538060111a60018301538060121a60028301538060131a60038301538060141a60048301538060151a60058301538060161a60068301538060171a60078301538060181a60088301538060191a600983015380601a1a600a83015380601b1a600b83015380601c1a600c83015380601d1a600d83015380601e1a600e83015380601f1a600f8301535050610a20516060606059905901600090526002815260c05181602001526000816040015280905020557001000000000000000000000000000000007001000000000000000000000000000000006060606059905901600090526002815260c051816020015260008160400152809050205402046105805266040000000000025461058051121515610e965760c05166040000000000015561058051660400000000000255601c606459905901600090520163c86a90fe601c8203526103e860048201523260248201526020610ae06044836000660400000000000354602d5a03f150610ae051905015610e95576103e8660400000000000454016604000000000004555b5b78010000000000000000000000000000000000000000000000006060606059905901600090526002815260c051816020015260008160400152809050205404610b00526020610b00f35b6000610b40526020610b40f35b63c6605beb811415611294573659905901600090523660048237600435610b6052602435610b80526044356020820101610ba0526064356040525067016345785d8a00003412151515610f47576000610bc0526020610bc0f35b601c6044599059016000905201633d73b705601c82035260405160048201526020610be0602483600030602d5a03f150610be05190508015610f895780610fc1565b601c604459905901600090520163b041b285601c82035260405160048201526020610c20602483600030602d5a03f150610c20519050155b905015610fd5576000610c40526020610c40f35b6060601c61014c59905901600090520163b7129afb601c820352610b60516004820152610b80516024820152610ba05160208103516020026020018360448401526020820360a4840152806101088401528084019350505081600401599059016000905260648160648460006004601cf161104c57fe5b6064810192506101088201518080858260a487015160006004600a8705601201f161107357fe5b508084019350508083036020610d008284600030602d5a03f150610d00519050905090509050610c60526080608059905901600090526002815260405181602001526002816040015260008160600152809050207c010000000000000000000000000000000000000000000000000000000060028201540464010000000060018301540201610d805250610d805180601f1a610de05380601e1a6001610de0015380601d1a6002610de0015380601c1a6003610de0015380601b1a6004610de0015380601a1a6005610de001538060191a6006610de001538060181a6007610de001538060171a6008610de001538060161a6009610de001538060151a600a610de001538060141a600b610de001538060131a600c610de001538060121a600d610de001538060111a600e610de001538060101a600f610de0015380600f1a6010610de0015380600e1a6011610de0015380600d1a6012610de0015380600c1a6013610de0015380600b1a6014610de0015380600a1a6015610de001538060091a6016610de001538060081a6017610de001538060071a6018610de001538060061a6019610de001538060051a601a610de001538060041a601b610de001538060031a601c610de001538060021a601d610de001538060011a601e610de001538060001a601f610de0015350610de051610d4052610d4051610c60511415611286576001610e00526020610e00f3611293565b6000610e20526020610e20f35b5b638f6b104c8114156115195736599059016000905236600482376004356020820101610e4052602435610b6052604435610b80526064356020820101610ba05260843560405260a435610e60525060016080601c6101ac59905901600090520163c6605beb601c820352610b60516004820152610b80516024820152610ba05160208103516020026020018360448401526020820360c48401528061014884015280840193505050604051606482015281600401599059016000905260848160848460006004601ff161136357fe5b6084810192506101488201518080858260c487015160006004600a8705601201f161138a57fe5b508084019350508083036020610e80828434306123555a03f150610e8051905090509050905014156114b3576040601c60ec59905901600090520163f0cf1ff4601c820352610e40516020601f6020830351010460200260200183600484015260208203604484015280608884015280840193505050610b60516024820152816004015990590160009052604481604484600060046018f161142857fe5b604481019250608882015180808582604487015160006004600a8705601201f161144e57fe5b508084019350508083036020610ec082846000610e6051602d5a03f150610ec0519050905090509050610ea0526040599059016000905260018152610ea051602082015260208101905033602082035160200282a150610ea051610f20526020610f20f35b604059905901600090526001815261270f600003602082015260208101905033602082035160200282a150604059905901600090526001815261270f6000036020820152602081019050610e6051602082035160200282a1506000610f80526020610f80f35b6309dd0e8181141561153957660400000000000154610fa0526020610fa0f35b630239487281141561159557780100000000000000000000000000000000000000000000000060606060599059016000905260028152660400000000000154816020015260008160400152809050205404610fc0526020610fc0f35b6361b919a68114156116045770010000000000000000000000000000000070010000000000000000000000000000000060606060599059016000905260028152660400000000000154816020015260008160400152809050205402046110005261100051611040526020611040f35b63a7cc63c28114156118b55766040000000000015460c0527001000000000000000000000000000000007001000000000000000000000000000000006060606059905901600090526002815260c05181602001526000816040015280905020540204611060526000610880525b600a610880511215611853576080608059905901600090526002815260c05181602001526002816040015260008160600152809050207c0100000000000000000000000000000000000000000000000000000000600182015404640100000000825402016110c052506110c05180601f1a6111205380601e1a6001611120015380601d1a6002611120015380601c1a6003611120015380601b1a6004611120015380601a1a600561112001538060191a600661112001538060181a600761112001538060171a600861112001538060161a600961112001538060151a600a61112001538060141a600b61112001538060131a600c61112001538060121a600d61112001538060111a600e61112001538060101a600f611120015380600f1a6010611120015380600e1a6011611120015380600d1a6012611120015380600c1a6013611120015380600b1a6014611120015380600a1a601561112001538060091a601661112001538060081a601761112001538060071a601861112001538060061a601961112001538060051a601a61112001538060041a601b61112001538060031a601c61112001538060021a601d61112001538060011a601e61112001538060001a601f6111200153506111205160c0526001610880510161088052611671565b7001000000000000000000000000000000007001000000000000000000000000000000006060606059905901600090526002815260c0518160200152600081604001528090502054020461114052611140516110605103611180526020611180f35b63b7129afb811415611e35573659905901600090523660048237600435610b6052602435610b80526044356020820101610ba05250610b60516111a0526020610ba05103516111c0526000610880525b6111c051610880511215611e0c5761088051602002610ba05101516111e0526002610b805107611200526001611200511415611950576111e051611220526111a0516112405261196e565b600061120051141561196d576111a051611220526111e051611240525b5b604059905901600090526112205180601f1a6112805380601e1a6001611280015380601d1a6002611280015380601c1a6003611280015380601b1a6004611280015380601a1a600561128001538060191a600661128001538060181a600761128001538060171a600861128001538060161a600961128001538060151a600a61128001538060141a600b61128001538060131a600c61128001538060121a600d61128001538060111a600e61128001538060101a600f611280015380600f1a6010611280015380600e1a6011611280015380600d1a6012611280015380600c1a6013611280015380600b1a6014611280015380600a1a601561128001538060091a601661128001538060081a601761128001538060071a601861128001538060061a601961128001538060051a601a61128001538060041a601b61128001538060031a601c61128001538060021a601d61128001538060011a601e61128001538060001a601f6112800153506112805181526112405180601f1a6112e05380601e1a60016112e0015380601d1a60026112e0015380601c1a60036112e0015380601b1a60046112e0015380601a1a60056112e001538060191a60066112e001538060181a60076112e001538060171a60086112e001538060161a60096112e001538060151a600a6112e001538060141a600b6112e001538060131a600c6112e001538060121a600d6112e001538060111a600e6112e001538060101a600f6112e0015380600f1a60106112e0015380600e1a60116112e0015380600d1a60126112e0015380600c1a60136112e0015380600b1a60146112e0015380600a1a60156112e001538060091a60166112e001538060081a60176112e001538060071a60186112e001538060061a60196112e001538060051a601a6112e001538060041a601b6112e001538060031a601c6112e001538060021a601d6112e001538060011a601e6112e001538060001a601f6112e00153506112e051602082015260205990590160009052602081604084600060026088f1508051905061130052602059905901600090526020816020611300600060026068f1508051905080601f1a6113805380601e1a6001611380015380601d1a6002611380015380601c1a6003611380015380601b1a6004611380015380601a1a600561138001538060191a600661138001538060181a600761138001538060171a600861138001538060161a600961138001538060151a600a61138001538060141a600b61138001538060131a600c61138001538060121a600d61138001538060111a600e61138001538060101a600f611380015380600f1a6010611380015380600e1a6011611380015380600d1a6012611380015380600c1a6013611380015380600b1a6014611380015380600a1a601561138001538060091a601661138001538060081a601761138001538060071a601861138001538060061a601961138001538060051a601a61138001538060041a601b61138001538060031a601c61138001538060021a601d61138001538060011a601e61138001538060001a601f6113800153506113805190506111a0526002610b805105610b80526001610880510161088052611905565b6111a0511515611e265760016000036113a05260206113a0f35b6111a0516113c05260206113c0f35b633d73b7058114156120625760043560405266040000000000015460c0526000610880525b60066108805112156120555760c0516040511415611e7f5760016113e05260206113e0f35b6080608059905901600090526002815260c05181602001526002816040015260008160600152809050207c01000000000000000000000000000000000000000000000000000000006001820154046401000000008254020161142052506114205180601f1a6114805380601e1a6001611480015380601d1a6002611480015380601c1a6003611480015380601b1a6004611480015380601a1a600561148001538060191a600661148001538060181a600761148001538060171a600861148001538060161a600961148001538060151a600a61148001538060141a600b61148001538060131a600c61148001538060121a600d61148001538060111a600e61148001538060101a600f611480015380600f1a6010611480015380600e1a6011611480015380600d1a6012611480015380600c1a6013611480015380600b1a6014611480015380600a1a601561148001538060091a601661148001538060081a601761148001538060071a601861148001538060061a601961148001538060051a601a61148001538060041a601b61148001538060031a601c61148001538060021a601d61148001538060011a601e61148001538060001a601f6114800153506114805160c0526001610880510161088052611e5a565b60006114a05260206114a0f35b6391cf0e96811415612105576004356114c052601c60845990590160009052016367eae672601c8203523360048201526114c051602482015230604482015260206114e06064836000660400000000000354602d5a03f1506114e051905015612104576604000000000004546114c05130310205611500526114c0516604000000000004540366040000000000045560006000600060006115005133611388f1505b5b6313f955e18114156122985736599059016000905236600482376004356020820101611520526024356115405250605061156052600061158052611560516115a0526000610880525b611540516108805112156122895761158051806115a051038080602001599059016000905281815260208101905090508180828286611520510160006004600a8705601201f161219a57fe5b50809050905090506115c0526020601c608c599059016000905201632b861629601c8203526115c0516020601f6020830351010460200260200183600484015260208203602484015280604884015280840193505050816004015990590160009052602481602484600060046015f161220f57fe5b602481019250604882015180808582602487015160006004600a8705601201f161223557fe5b5080840193505080830360206116808284600030602d5a03f150611680519050905090509050610ea05261156051611580510161158052611560516115a051016115a052600161088051016108805261214e565b610ea0516116a05260206116a0f35b50";
        String result = stringifyMultiline(Hex.decode(code2));
        Assertions.assertNotNull(result);
    }

    @Test
    void regression2Test() {
        // testing that we are working fine with unknown 0xFE bytecode produced by Serpent compiler
        String code2 = "6060604052604051602080603f8339016040526060805190602001505b806000600050819055505b50600a8060356000396000f30060606040526008565b000000000000000000000000000000000000000000000000000000000000000021";
        String result = stringifyMultiline(Hex.decode(code2));
        assertTrue(result.contains("00000000000000000000000000000000")); // detecting bynary data in bytecode
    }

    @Test
    void decompileDupnSwapn() {
        String code = "6060a8a962";
        String result = stringifyMultiline(Hex.decode(code));
        assertTrue(result.contains("PUSH1 0x60 (96)"));
        assertTrue(result.contains("DUPN"));
        assertTrue(result.contains("SWAPN"));
    }

    @Test
    void decompileTxindex() {
        String code = "aa";
        String result = stringifyMultiline(Hex.decode(code));
        assertTrue(result.contains("TXINDEX"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Testing an unfinished script header
    // header must be 4 bytes or more to be valid
    @Test
    void testScriptVersion0() {

        program = getProgram("FC");
        try {
            Assertions.assertThrows(Program.IllegalOperationException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    // Testing FC code with scriptVersion ==0.
    // The header is valid
    // Should produce invalidop exception
    @Test
    void testScriptVersion1() {
        program = getProgram("FC000000" + //header
                "FC");
        try {
            // Only one step needs to be exeecuted because header is not.
            Assertions.assertThrows(Program.IllegalOperationException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }
    // Under scriptVersion == 1, opHEADER in a program is still an invalid code.

    @Test
    void testScriptVersion2() {

        program = getProgram(
                "FC010100" + //header
                        "FC" // invalid code
        );
        try {
            // Only one step needs to be exaecuted because header is not.
            Assertions.assertThrows(Program.IllegalOperationException.class, () -> vm.step(program));
        } finally {
            assertTrue(program.isStopped());
        }
    }

    // This is a long header with additional data that is skipped
    @Test
    void testScriptVersion3() {
        program = getProgram(
                "FC01010A" + //header with 10 additional bytes
                        "0102030405060708090A" + // additional header bytes
                        "00" // STOP code
        );
        try {
            // Only one step needs to be exaecuted because header is not.
            vm.step(program);

        } finally {
            assertTrue(program.isStopped());
        }
    }

    @Test
    void whenProgramIsInitializedPrecompiledCalledShouldBeFalse() {
        Program program = getProgram(new byte[]{});
        Assertions.assertTrue(program.precompiledContractsCalled().isEmpty());
    }

    @Test
    void ifATxCallsAPrecompiledContractPrecompiledContractHasBeenCalledShouldBeTrue() {
        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x01" +
                " PUSH1 0x00" +
                " PUSH1 0x01" +
                " PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH20 0x" + PrecompiledContracts.IDENTITY_ADDR_STR +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        vm.steps(program, Long.MAX_VALUE);
        Assertions.assertFalse(program.precompiledContractsCalled().isEmpty());
        Assertions.assertFalse(program.getResult().isRevert());
    }

    @Test
    void ifATxCallsANonPrecompiledContractPrecompiledContractHasBeenCalledShouldBeFalse() {
        invoke = new ProgramInvokeMockImpl(compile("PUSH1 0x01 PUSH1 0x02 SUB"), null);

        program = getProgram(compile("PUSH1 0x00" +
                " PUSH1 0x00" +
                " PUSH1 0x01" + //out size
                " PUSH1 0x00" + //out off
                " PUSH1 0x01" + //in size
                " PUSH1 0x00" + //in off
                " PUSH1 0x00" +
                " PUSH20 0x" + invoke.getContractAddress() +
                " PUSH4 0x005B8D80" +
                " CALL"
        ));
        vm.steps(program, Long.MAX_VALUE);
        Assertions.assertTrue(program.precompiledContractsCalled().isEmpty());
        Assertions.assertFalse(program.getResult().isRevert());
    }

    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    private Program getProgram(String code) {
        return getProgram(Hex.decode(code), null);
    }

    private Program getProgram(byte[] code) {
        return getProgram(code, null);
    }

    private Program getProgramWithTransaction(byte[] code, Transaction transaction) {
        return getProgram(code, transaction);
    }

    private ActivationConfig.ForBlock getBlockchainConfig(boolean preFixStaticCall) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP91)).thenReturn(true);

        when(activations.isActive(ConsensusRule.RSKIP103)).thenReturn(!preFixStaticCall);

        when(activations.isActive(ConsensusRule.RSKIP90)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP89)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP150)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP398)).thenReturn(true);
        return activations;
    }

    private Program getProgram(byte[] code, Transaction transaction) {
        return getProgram(code, transaction, false);
    }

    private Program getProgram(byte[] code, Transaction transaction, boolean preFixStaticCall) {
        return new Program(vmConfig, precompiledContracts, blockFactory, getBlockchainConfig(preFixStaticCall), code, invoke, transaction, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    private byte[] compile(String code) {
        return new BytecodeCompiler().compile(code);
    }

    private static Transaction createTransaction(int number) {
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }

    private static RskAddress createAddress(String name) {
        AccountBuilder accountBuilder = new AccountBuilder();
        accountBuilder.name(name);
        Account account = accountBuilder.build();
        return account.getAddress();

    }

    static String formatBinData(byte[] binData, int startPC) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < binData.length; i += 16) {
            ret.append(Utils.align("" + Integer.toHexString(startPC + (i)) + ":", ' ', 8, false));
            ret.append(ByteUtil.toHexString(binData, i, min(16, binData.length - i))).append('\n');
        }
        return ret.toString();
    }

    public static String stringifyMultiline(byte[] code) {
        int index = 0;
        StringBuilder sb = new StringBuilder();
        BitSet mask = buildReachableBytecodesMask(code);
        ByteArrayOutputStream binData = new ByteArrayOutputStream();
        int binDataStartPC = -1;

        while (index < code.length) {
            final byte opCode = code[index];
            OpCode op = OpCode.code(opCode);

            if (!mask.get(index)) {
                if (binDataStartPC == -1) {
                    binDataStartPC = index;
                }
                binData.write(code[index]);
                index++;
                if (index < code.length) {
                    continue;
                }
            }

            if (binDataStartPC != -1) {
                sb.append(formatBinData(binData.toByteArray(), binDataStartPC));
                binDataStartPC = -1;
                binData = new ByteArrayOutputStream();
                if (index == code.length) {
                    continue;
                }
            }

            sb.append(Utils.align("" + Integer.toHexString(index) + ":", ' ', 8, false));

            if (op == null) {
                sb.append("<UNKNOWN>: ").append(0xFF & opCode).append("\n");
                index++;
                continue;
            }

            if (op.name().startsWith("PUSH")) {
                sb.append(' ').append(op.name()).append(' ');

                int nPush = op.val() - OpCode.PUSH1.val() + 1;
                byte[] data = Arrays.copyOfRange(code, index + 1, index + nPush + 1);
                BigInteger bi = new BigInteger(1, data);
                sb.append("0x").append(bi.toString(16));
                if (bi.bitLength() <= 32) {
                    sb.append(" (").append(new BigInteger(1, data).toString()).append(") ");
                }

                index += nPush + 1;
            } else {
                sb.append(' ').append(op.name());
                index++;
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    static BitSet buildReachableBytecodesMask(byte[] code) {
        NavigableSet<Integer> gotos = new TreeSet<>();
        ByteCodeIterator it = new ByteCodeIterator(code);
        BitSet ret = new BitSet(code.length);
        int lastPush = 0;
        int lastPushPC = 0;
        do {
            ret.set(it.getPC()); // reachable bytecode
            if (it.isPush()) {
                lastPush = new BigInteger(1, it.getCurOpcodeArg()).intValue();
                lastPushPC = it.getPC();
            }
            if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.JUMPI) {
                if (it.getPC() != lastPushPC + 1) {
                    // some PC arithmetic we totally can't deal with
                    // assuming all bytecodes are reachable as a fallback
                    for (int i = 0; i < code.length; i++) {
                        ret.set(i);
                    }
                    return ret;
                }
                int jumpPC = lastPush;
                if (!ret.get(jumpPC)) {
                    // code was not explored yet
                    gotos.add(jumpPC);
                }
            }
            if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.RETURN ||
                    it.getCurOpcode() == OpCode.STOP) {
                if (gotos.isEmpty()) {
                    break;
                }
                it.setPC(gotos.pollFirst());
            }
        } while (it.next());
        return ret;
    }

    static class ByteCodeIterator {
        private byte[] code;
        private int pc;

        public ByteCodeIterator(byte[] code) {
            this.code = code;
        }

        public void setPC(int pc) {
            this.pc = pc;
        }

        public int getPC() {
            return pc;
        }

        public OpCode getCurOpcode() {
            return pc < code.length ? OpCode.code(code[pc]) : null;
        }

        public boolean isPush() {
            return getCurOpcode() != null ? getCurOpcode().name().startsWith("PUSH") : false;
        }

        public byte[] getCurOpcodeArg() {
            if (isPush()) {
                int nPush = getCurOpcode().val() - OpCode.PUSH1.val() + 1;
                return Arrays.copyOfRange(code, pc + 1, pc + nPush + 1);
            } else {
                return new byte[0];
            }
        }

        public boolean next() {
            pc += 1 + getCurOpcodeArg().length;
            return pc < code.length;
        }
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
