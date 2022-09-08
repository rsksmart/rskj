package co.rsk.vm;

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

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP125;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Created by Sebastian Sicardi on 22/05/2019.
 */
public class Create2Test {

    private ActivationConfig.ForBlock activationConfig;
    private ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private BytecodeCompiler compiler = new BytecodeCompiler();
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig()));
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final Transaction transaction = createTransaction();

    @BeforeEach
    public void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP125)).thenReturn(true);
    }

    @Test
    public void testCREATE2_BasicTest() {
        /**
         * Initial test for Create2, just check that the contract is created
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "PUSH32 0x600b000000000000000000000000000000000000000000000000000000000000",
                2,
                0,
                "3BC3EFA1C487A1EBFC911B47B548E2C82436A212",
                32033);
    }

    @Test
    public void testCREATE2_SaltNumber() {
        /**
         * Check that address changes with different salt than before
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x00000000000000000000000000000000000000000000000000000000cafebabe",
                "PUSH32 0x601b000000000000000000000000000000000000000000000000000000000000",
                2,
                0,
                "19542B03F2D5D4E1910DBE096FAF0842D928883D",
                32033);
    }

    @Test
    public void testCREATE2_Address() {
        /**
         * Check that address changes with different sender address than before
         */
        callCreate2("0xdeadbeef00000000000000000000000000000000",
                "0x00000000000000000000000000000000000000000000000000000000cafebabe",
                "PUSH32 0x601b000000000000000000000000000000000000000000000000000000000000",
                2,
                0,
                "3BA1DC70CC17E740F4BD85052AF074B2B2A49E06",
                32033);
    }

    @Test
    public void testCREATE2_InitCode() {
        /**
         * Check for a different length of init_code
         */
        callCreate2("0xdeadbeef00000000000000000000000000000000",
                "0x00000000000000000000000000000000000000000000000000000000cafebabe",
                "PUSH32 0x601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b",
                32,
                0,
                "D1FB828980EC250DD0A350E59108ECC63C2C4B36",
                32078);
    }

    @Test
    public void testCREATE2_ZeroSize() {
        /**
         * Check for a call with init_code with size 0
         * (Note that it should return same address than next test)
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "PUSH32 0x601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b",
                0,
                0,
                "65BD0714DEFB919BC02F9507D6F9D9CD21195ECC",
                32024);
    }

    @Test
    public void testCREATE2_EmptyCode() {
        /**
         * Check for a call with no init_code
         * (Note that it should return same address than previous test)
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "",
                0,
                0,
                "65BD0714DEFB919BC02F9507D6F9D9CD21195ECC",
                32012);
    }

    @Test
    public void testCREATE2_CodeOffset() {
        /**
         * Check that the offset parameter works correctly
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "PUSH32 0x601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b",
                10,
                8,
                "A992CD9E3E78C0A6BBBB4F06B52B3AD8924B0916",
                32045);
    }

    @Test
    public void testCREATE2_NoCodePushed() {
        /**
         * No code pushed but code sized is greater than zero, it should get zeroes and pass
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "",
                12,
                0,
                "16F27A604035007FA9925DB8CC2CAFDCFFC6278C",
                32021);
    }

    @Test
    public void testCREATE2_InvalidInitCode() {
        /**
         * INIT_CODE fails (Create2 with invalid arguments) so it returns a ZERO address
         * as it fails, it spends all the gas
         */
        callCreate2("0x0000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "PUSH32 0xf500000000000000000000000000000000000000000000000000000000000000",
                1,
                0,
                "0000000000000000000000000000000000000000",
                1000000);
    }

    @Test
    public void testCREATE2_DuplicateContractCreation() {
        /**
         *  Two CREATE2 calls, second should fail and consume all gas
         */
        String address = "0x0000000000000000000000000000000000000000";
        String salt = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String pushInitCode = "PUSH32 0x6000000000000000000000000000000000000000000000000000000000000000";
        String expectedAddress1 = "9CC90A6BDF7A59E213CFB70958A2E1A8EA5AF1E6";
        String expectedAddress2 = "0000000000000000000000000000000000000000";
        int size = 1;
        int intOffset = 0;
        int gasExpected = 1000000;
        int value = 10;

        RskAddress testAddress = new RskAddress(address);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(value + 1000));
        String inSize = "0x" + DataWord.valueOf(size);
        String inOffset = "0x" + DataWord.valueOf(intOffset);

        pushInitCode += " PUSH1 0x00 MSTORE";

        String codeToExecute = pushInitCode +
                " PUSH32 " + salt +
                " PUSH32 " + inSize +
                " PUSH32 " + inOffset +
                " PUSH32 " + "0x" + DataWord.valueOf(value) +
                " CREATE2 ";

        codeToExecute += pushInitCode +
                " PUSH32 " + salt +
                " PUSH32 " + inSize +
                " PUSH32 " + inOffset +
                " PUSH32 " + "0x" + DataWord.valueOf(value) +
                " CREATE2 ";

        Program program = executeCode(codeToExecute);
        Stack stack = program.getStack();
        Assertions.assertEquals(2, stack.size());
        String address2 = ByteUtil.toHexString(stack.pop().getLast20Bytes());
        String address1 = ByteUtil.toHexString(stack.pop().getLast20Bytes());
        Assertions.assertEquals(expectedAddress1.toUpperCase(), address1.toUpperCase());
        Assertions.assertEquals(expectedAddress2.toUpperCase(), address2.toUpperCase());
        Assertions.assertEquals(gasExpected, program.getResult().getGasUsed());
    }

    @Test
    public void testCREATE2_PreserveContractAddressBalance() {
        /**
         *  CREATE2 call with expected address that has non-zero balance
         */
        String address = "0x0000000000000000000000000000000000000000";
        String salt = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String pushInitCode = "PUSH32 0x6000000000000000000000000000000000000000000000000000000000000000";
        String expectedContractAddress = "9CC90A6BDF7A59E213CFB70958A2E1A8EA5AF1E6";
        int size = 1;
        int intOffset = 0;
        int value = 10;
        int initialContractAddressBalance = 100;
        Coin expectedContractBalance = Coin.valueOf(initialContractAddressBalance + value);

        RskAddress testAddress = new RskAddress(address);
        RskAddress contractAddress = new RskAddress(expectedContractAddress);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(value + 1000));
        invoke.getRepository().addBalance(contractAddress, Coin.valueOf(initialContractAddressBalance));
        String inSize = "0x" + DataWord.valueOf(size);
        String inOffset = "0x" + DataWord.valueOf(intOffset);

        pushInitCode += " PUSH1 0x00 MSTORE";

        String codeToExecute = pushInitCode +
                " PUSH32 " + salt +
                " PUSH32 " + inSize +
                " PUSH32 " + inOffset +
                " PUSH32 " + "0x" + DataWord.valueOf(value) +
                " CREATE2 ";

        Program program = executeCode(codeToExecute);
        Coin actualContractBalance = invoke.getRepository().getBalance(contractAddress);
        Stack stack = program.getStack();
        Assertions.assertEquals(1, stack.size());
        String actualAddress = ByteUtil.toHexString(stack.pop().getLast20Bytes());
        Assertions.assertEquals(expectedContractAddress.toUpperCase(), actualAddress.toUpperCase());
        Assertions.assertEquals(expectedContractAddress.toUpperCase(), actualAddress.toUpperCase());
        Assertions.assertEquals(expectedContractBalance, actualContractBalance);
    }

    @Test
    public void testCREATE_CheckFunctionBeforeRSKIP() {
        /**
         * Check that the CREATE opcode functions correctly before the RSKIP
         * It should create the contract and have nonce 0
         */

        when(activationConfig.isActive(RSKIP125)).thenReturn(false);

        String code = "PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 CREATE";

        Program program = executeCode(code);

        Stack stack = program.getStack();
        String address = ByteUtil.toHexString(stack.peek().getLast20Bytes());
        long nonce = program.getStorage().getNonce(new RskAddress(address)).longValue();

        Assertions.assertEquals(0, nonce);
        Assertions.assertEquals("77045E71A7A2C50903D88E564CD72FAB11E82051", address.toUpperCase());
        Assertions.assertEquals(1, stack.size());
    }

    @Test
    public void testCREATE2ShouldFailInvalidOpcode() {
        when(activationConfig.isActive(RSKIP125)).thenReturn(false);

        Assertions.assertThrows(Program.IllegalOperationException.class, () -> callCreate2("0x0000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "PUSH32 0x601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b601b",
                10,
                8,
                "A992CD9E3E78C0A6BBBB4F06B52B3AD8924B0916",
                32045));
    }

    @Test
    public void testCREATE2_EmptyCodeNonStandard() {
        /**
         * Check for a call with no init_code
         * (Note that it should return same address than previous test)
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "",
                0,
                0,
                "65BD0714DEFB919BC02F9507D6F9D9CD21195ECC",
                32012);

        Keccak256 existentHash = invoke.getRepository().getCodeHashNonStandard(new RskAddress("65BD0714DEFB919BC02F9507D6F9D9CD21195ECC"));
        Assertions.assertArrayEquals(Keccak256.ZERO_HASH.getBytes(), existentHash.getBytes());
    }

    @Test
    public void testCREATE2_EmptyCodeStandard() {
        /**
         * Check for a call with no init_code
         * (Note that it should return same address than previous test)
         */
        callCreate2("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "",
                0,
                0,
                "65BD0714DEFB919BC02F9507D6F9D9CD21195ECC",
                32012);

        byte[] emptyHash = Keccak256Helper.keccak256(ExtCodeHashTest.EMPTY_BYTE_ARRAY);
        Keccak256 existentHash = invoke.getRepository().getCodeHashStandard(new RskAddress("65BD0714DEFB919BC02F9507D6F9D9CD21195ECC"));

        Assertions.assertArrayEquals(emptyHash, existentHash.getBytes());
    }

    private void callCreate2(String address, String salt, String pushInitCode, int size, int intOffset, String expected, long gasExpected) {
        int value = 10;
        RskAddress testAddress = new RskAddress(address);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(value + 1));
        String inSize = "0x" + DataWord.valueOf(size);
        String inOffset = "0x" + DataWord.valueOf(intOffset);

        if (!pushInitCode.isEmpty()) {
            pushInitCode += " PUSH1 0x00 MSTORE";
        }

        Program program = executeCode(
                pushInitCode +
                        " PUSH32 " + salt +
                        " PUSH32 " + inSize +
                        " PUSH32 " + inOffset +
                        " PUSH32 " + "0x" + DataWord.valueOf(value) +
                        " CREATE2");
        Stack stack = program.getStack();
        String result = ByteUtil.toHexString(Arrays.copyOfRange(stack.peek().getData(), 12, stack.peek().getData().length));

        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(expected.toUpperCase(), result.toUpperCase());
        Assertions.assertEquals(gasExpected, program.getResult().getGasUsed());
    }

    private static Transaction createTransaction() {
        int number = 0;
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }


    private Program executeCode(String stringCode) {
        byte[] code = compiler.compile(stringCode);
        VM vm = new VM(vmConfig,precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>());

        while (!program.isStopped()){
            vm.step(program);
        }

        return program;
    }
}
