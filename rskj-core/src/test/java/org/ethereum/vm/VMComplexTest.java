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
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author Roman Mandeleil
 * @since 16.06.2014
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VMComplexTest {

    private static Logger logger = LoggerFactory.getLogger("TCK-Test");
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);

    @Ignore //TODO #POC9
    @Test // contract call recursive
    public void test1() {

        /**
         *       #The code will run
         *       ------------------

                 a = contract.storage[999]
                 if a > 0:
                     contract.storage[999] = a - 1

                     # call to contract: 77045e71a7a2c50903d88e564cd72fab11e82051
                     send((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0)
                 else:
                     stop
         */

        int expectedGas = 436;

        DataWord key1 = new DataWord(999);
        DataWord value1 = new DataWord(3);

        // Set contract into Database
        String callerAddr = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";
        String contractAddr = "77045e71a7a2c50903d88e564cd72fab11e82051";
        String code =
                "6103e75460005260006000511115630000004c576001600051036103e755600060006000600060007377045e71a7a2c50903d88e564cd72fab11e820516008600a5a0402f1630000004c00565b00";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }

    @Ignore //TODO #POC9
    @Test // contractB call contractA with data to storage
    public void test2() {

        /**
         *       #The code will run
         *       ------------------

                 contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
                 ---------------
                     a = msg.data[0]
                     b = msg.data[1]

                     contract.storage[a]
                     contract.storage[b]


                 contract B: 83c5541a6c8d2dbad642f385d8d06ca9b6c731ee
                 -----------
                     a = msg((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0, [11, 22, 33], 3, 6)

         */

        long expectedVal_1 = 11;
        long expectedVal_2 = 22;

        // Set contract into Database
        String caller_addr_str = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";

        String contractA_addr_str = "77045e71a7a2c50903d88e564cd72fab11e82051";
        String contractB_addr_str = "83c5541a6c8d2dbad642f385d8d06ca9b6c731ee";

        String code_a = "60006020023560005260016020023560205260005160005560205160015500";
        String code_b = "6000601f5360e05960e05952600060c05901536060596020015980602001600b9052806040016016905280606001602190526080905260007377045e71a7a2c50903d88e564cd72fab11e820516103e8f1602060000260a00160200151600052";

        RskAddress caller_addr = new RskAddress(caller_addr_str);

        RskAddress contractA_addr = new RskAddress(contractA_addr_str);
        byte[] codeA = Hex.decode(code_a);

        RskAddress contractB_addr = new RskAddress(contractB_addr_str);
        byte[] codeB = Hex.decode(code_b);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractB_addr);
        Repository repository = pi.getRepository();

        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(contractB_addr);
        repository.saveCode(contractB_addr, codeB);

        repository.createAccount(caller_addr);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(caller_addr, new Coin(value));


        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }


        System.out.println();
        System.out.println("============ Results ============");


        System.out.println("*** Used gas: " + program.getResult().getGasUsed());


        DataWord value_1 = repository.getStorageValue(contractA_addr, new DataWord(00));
        DataWord value_2 = repository.getStorageValue(contractA_addr, new DataWord(01));


        assertEquals(expectedVal_1, value_1.longValue());
        assertEquals(expectedVal_2, value_2.longValue());

        // TODO: check that the value pushed after exec is 1
    }

    @Ignore
    @Test // contractB call contractA with return expectation
    public void test3() {

        /**
         *       #The code will run
         *       ------------------

         contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
         ---------------

           a = 11
           b = 22
           c = 33
           d = 44
           e = 55
           f = 66

           [asm  192 0 RETURN asm]



         contract B: 83c5541a6c8d2dbad642f385d8d06ca9b6c731ee
         -----------
             a = msg((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0, [11, 22, 33], 3, 6)

         */

        long expectedVal_1 = 11;
        long expectedVal_2 = 22;
        long expectedVal_3 = 33;
        long expectedVal_4 = 44;
        long expectedVal_5 = 55;
        long expectedVal_6 = 66;

        // Set contract into Database
        RskAddress caller_addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        RskAddress contractA_addr = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");
        RskAddress contractB_addr = new RskAddress("83c5541a6c8d2dbad642f385d8d06ca9b6c731ee");

        byte[] codeA = Hex.decode("600b60005260166020526021604052602c6060526037608052604260a05260c06000f2");
        byte[] codeB = Hex.decode("6000601f5360e05960e05952600060c05901536060596020015980602001600b9052806040016016905280606001602190526080905260007377045e71a7a2c50903d88e564cd72fab11e820516103e8f1602060000260a00160200151600052");

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractB_addr);
        Repository repository = pi.getRepository();
        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(contractB_addr);
        repository.saveCode(contractB_addr, codeB);

        repository.createAccount(caller_addr);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(caller_addr, new Coin(value));

        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");
        System.out.println("*** Used gas: " + program.getResult().getGasUsed());

        DataWord value1 = program.memoryLoad(new DataWord(32));
        DataWord value2 = program.memoryLoad(new DataWord(64));
        DataWord value3 = program.memoryLoad(new DataWord(96));
        DataWord value4 = program.memoryLoad(new DataWord(128));
        DataWord value5 = program.memoryLoad(new DataWord(160));
        DataWord value6 = program.memoryLoad(new DataWord(192));

        assertEquals(expectedVal_1, value1.longValue());
        assertEquals(expectedVal_2, value2.longValue());
        assertEquals(expectedVal_3, value3.longValue());
        assertEquals(expectedVal_4, value4.longValue());
        assertEquals(expectedVal_5, value5.longValue());
        assertEquals(expectedVal_6, value6.longValue());

        // TODO: check that the value pushed after exec is 1
    }

    @Test // CREATE magic
    public void test4() {

        /**
         *       #The code will run
         *       ------------------

         contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
         -----------

             a = 0x7f60c860005461012c6020540000000000000000000000000000000000000000
             b = 0x0060005460206000f20000000000000000000000000000000000000000000000
             create(100, 0 41)


         contract B: (the contract to be created the addr will be defined to: 8e45367623a2865132d9bf875d5cfa31b9a0cd94)
         -----------
             a = 200
             b = 300

         */

        // Set contract into Database
        RskAddress caller_addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        RskAddress contractA_addr = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");

        byte[] codeA = Hex.decode("7f7f60c860005461012c602054000000000000" +
                "00000000000000000000000000006000547e60" +
                "005460206000f2000000000000000000000000" +
                "0000000000000000000000602054602960006064f0");

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractA_addr);

        Repository repository = pi.getRepository();

        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(caller_addr);

        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeA, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        logger.info("============ Results ============");

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        // TODO: check that the value pushed after exec is the new address
    }

    @Test // CALL contract with too much gas
    @Ignore
    public void test5() {
        // TODO CALL contract with gas > gasRemaining && gas > Long.MAX_VALUE
    }

    @Ignore
    @Test // contractB call itself with code from contractA
    public void test6() {
        /**
         *       #The code will run
         *       ------------------

         contract A: 945304eb96065b2a98b57a48a06ae28d285a71b5
         ---------------

         PUSH1 0 CALLDATALOAD SLOAD NOT PUSH1 9 JUMPI STOP
         PUSH1 32 CALLDATALOAD PUSH1 0 CALLDATALOAD SSTORE

         contract B: 0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6
         -----------
             { (MSTORE 0 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff)
               (MSTORE 32 0xaaffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffaa)
               [[ 0 ]] (CALLSTATELESS 1000000 0x945304eb96065b2a98b57a48a06ae28d285a71b5 23 0 64 64 0)
             }
         */

        // Set contract into Database
        RskAddress caller_addr = new RskAddress("cd1722f3947def4cf144679da39c4c32bdc35681");

        RskAddress contractA_addr = new RskAddress("945304eb96065b2a98b57a48a06ae28d285a71b5");
        RskAddress contractB_addr = new RskAddress("0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6");

        byte[] codeA = Hex.decode("60003554156009570060203560003555");
        byte[] codeB = Hex.decode("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6000527faaffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffaa6020526000604060406000601773945304eb96065b2a98b57a48a06ae28d285a71b5620f4240f3600055");

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractB_addr);
        pi.setGasLimit(10000000000000l);

        Repository repository = pi.getRepository();
        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);
        repository.addBalance(contractA_addr, Coin.valueOf(23));

        repository.createAccount(contractB_addr);
        repository.saveCode(contractB_addr, codeB);
        final BigInteger value = new BigInteger("1000000000000000000");
        repository.addBalance(contractB_addr, new Coin(value));

        repository.createAccount(caller_addr);
        final BigInteger value1 = new BigInteger("100000000000000000000");
        repository.addBalance(caller_addr, new Coin(value1));

        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");
        System.out.println("*** Used gas: " + program.getResult().getGasUsed());

        DataWord memValue1 = program.memoryLoad(new DataWord(0));
        DataWord memValue2 = program.memoryLoad(new DataWord(32));

        DataWord storeValue1 = repository.getStorageValue(contractB_addr, new DataWord(00));

        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", memValue1.toString());
        assertEquals("aaffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffaa", memValue2.toString());

        assertEquals("0x1", storeValue1.shortHex());

        // TODO: check that the value pushed after exec is 1
    }

    //sha3_memSizeQuadraticCost33
    @Ignore //TODO #POC9
    @Test // contract call quadratic memory use
    public void test7() {

        int expectedGas = 357;

        DataWord key1 = new DataWord(999);
        DataWord value1 = new DataWord(3);

        // Set contract into Database
        String callerAddr = "cd1722f3947def4cf144679da39c4c32bdc35681";
        String contractAddr = "0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6";
        String code = "600161040020600055";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }

    //sha3_memSizeQuadraticCost31
    @Ignore //TODO #POC9
    @Test // contract call quadratic memory use
    public void test8() {

        int expectedGas = 354;

        DataWord key1 = new DataWord(999);
        DataWord value1 = new DataWord(3);

        // Set contract into Database
        String callerAddr = "cd1722f3947def4cf144679da39c4c32bdc35681";
        String contractAddr = "0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6";
        String code = "60016103c020600055";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }

    //sha3_memSizeQuadraticCost32
    @Ignore //TODO #POC9
    @Test // contract call quadratic memory use
    public void test9() {

        int expectedGas = 356;

        DataWord key1 = new DataWord(9999);
        DataWord value1 = new DataWord(3);

        // Set contract into Database
        String callerAddr = "cd1722f3947def4cf144679da39c4c32bdc35681";
        String contractAddr = "0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6";
        String code = "60016103e020600055";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }

    //sha3_memSizeQuadraticCost32_zeroSize
    @Ignore //TODO #POC9
    @Test // contract call quadratic memory use
    public void test10() {

        int expectedGas = 313;

        DataWord key1 = new DataWord(999);
        DataWord value1 = new DataWord(3);

        // Set contract into Database
        String callerAddr = "cd1722f3947def4cf144679da39c4c32bdc35681";
        String contractAddr = "0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6";
        String code = "600061040020600055";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }

    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    private Program getProgram(byte[] code, ProgramInvoke pi) {
        return new Program(vmConfig, precompiledContracts, mock(BlockchainConfig.class), code, pi, null);
    }
}
