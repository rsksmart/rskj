/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.util.HexUtils;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.DataWord;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by patogallaiovlabs on 28/10/2020.
 */
class EthModuleDSLTest {

    public static final String SIMPLE_CONTRACT_GET_METHOD = "0x6d4ce63c";
    public static final String GET_MY_BALANCE_FUNCTION = "0x4c738909";
    public static final int SIMPLE_CONTRACT_STORED_DATA = 10;

    @Test
    void testCall_getRevertReason() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/eth_module/revert_reason.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status = transactionReceipt.getStatus();

        Assertions.assertNotNull(status);
        Assertions.assertEquals(0, status.length);

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final Transaction tx01 = world.getTransactionByName("tx01");
        final CallArguments args = new CallArguments();
        args.setTo("0x" + tx01.getContractAddress().toHexString()); //"6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.setData("0xd96a094a0000000000000000000000000000000000000000000000000000000000000000"); // call to contract with param value = 0
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("0x2");
        try {
            eth.call(callArgumentsParam, blockIdentifierParam);
            fail();
        } catch (RskJsonRpcRequestException e) {
            MatcherAssert.assertThat(e.getMessage(), Matchers.containsString("Negative value."));
        }

        args.setData("0xd96a094a0000000000000000000000000000000000000000000000000000000000000001"); // call to contract with param value = 1
        final String call = eth.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("0x2"));
        assertEquals("0x", call);
    }

    @Test
    void testCall_StateOverride_stateIsOverridden() throws DslProcessorException, FileNotFoundException {
        // When
        World world = new World();
        // given a deployed contract with stored state = 10
        String contractAddress = deployContractAndGetAddressFromDsl("dsl/eth_module/simple_contract.txt",world);

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress);
        args.setData(SIMPLE_CONTRACT_GET_METHOD); //call  get() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        // Then
        // when calling get() on the contract without any override
        String result = eth.call(callArgumentsParam, blockIdentifierParam);

        assertEquals(SIMPLE_CONTRACT_STORED_DATA, HexUtils.jsonHexToInt(result));
        // then the result is the stored state value: 10

        AccountOverride accountOverride = new AccountOverride(new RskAddress(contractAddress));
        DataWord key = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000000");
        DataWord value = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000014");
        // given a call to the same contract with a state override setting storage[0] = 20
        accountOverride.setState(Map.of(key, value));
        // when calling get() on the contract with the override
        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));
        // then the returned value is the overridden state value: 20
        assertEquals(20, HexUtils.jsonHexToInt(result2));
    }

    /**
     * Runtime bytecode of the contract:
     * <p>
     * // SPDX-License-Identifier: MIT
     * pragma solidity ^0.8.0;
     * <p>
     * contract FakeContract {
     *     function get() public pure returns (uint) {
     *         return 999;
     *     }
     * }
     */
    @Test
    void testCall_StateOverride_codeIsOverridden() throws DslProcessorException, FileNotFoundException {
        // Given
        // This is the runtime bytecode of the contract above to override the original contract
        String runtimeByteCode = "0x6103e760005260206000f3";
        World world = new World();

        // given a deployed contract with original bytecode returning 10 from get()
        String contractAddress =deployContractAndGetAddressFromDsl("dsl/eth_module/simple_contract.txt",world);

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress);
        args.setData("0x6d4ce63c"); //call  get() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        // Then

        // when calling get() on the contract without code override
        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        // then it returns the original stored value: 10
        assertEquals(SIMPLE_CONTRACT_STORED_DATA, HexUtils.jsonHexToInt(result));

        AccountOverride accountOverride = new AccountOverride(new RskAddress(contractAddress));
        byte[] newCode = HexUtils.stringHexToByteArray(runtimeByteCode);
        // given a call to the same contract with overridden bytecode that returns 999
        accountOverride.setCode(newCode);
        // when calling get() on the contract with the code override
        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));
        // then it returns the overridden value: 999
        assertEquals(999, HexUtils.jsonHexToInt(result2));
    }

    @Test
    void testCall_StateOverride_balanceIsOverridden()throws DslProcessorException, FileNotFoundException {
        // Given
        long defaultBalance = 30000L;
        World world = new World();

        // given a deployed contract that returns msg.sender balance
        String contractAddress = deployContractAndGetAddressFromDsl("dsl/eth_module/check_balance.txt",world);
        // and an account with balance different from 30000
        Account acc = world.getAccountByName("acc1");

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setFrom(acc.getAddress().toHexString());
        args.setTo("0x" + contractAddress);
        args.setData(GET_MY_BALANCE_FUNCTION); //call  getMyBalance() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        // Then

        // when calling getMyBalance() without overrides
        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        // then the returned balance is not equal to 30000
        assertNotEquals(defaultBalance, HexUtils.jsonHexToInt(result));

        AccountOverride accountOverride = new AccountOverride(acc.getAddress());
        accountOverride.setBalance(BigInteger.valueOf(defaultBalance));
        // given a call to the same contract with a balance override setting msg.sender balance to 30000
        // when calling getMyBalance() with the override
        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));
        // then the returned balance is 30000
        assertEquals(defaultBalance, HexUtils.jsonHexToInt(result2));
    }

    @Test
    void testCall_StateOverride_precompiledContractIsMoved() throws DslProcessorException, FileNotFoundException {

        // Given

        String falseInHex = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String trueInHex = "0x0000000000000000000000000000000000000000000000000000000000000001";

        // Test Setup

        DslParser parser = DslParser.fromResource("dsl/eth_module/test_state_override_move_precompiled.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Account acc = world.getAccountByName("acc1");
        RskAddress identityPrecompiledAddress = new RskAddress("0x0000000000000000000000000000000000000004");
        RskAddress movePrecompiledTo = new RskAddress("0x0000000000000000000000000000000000000001");

        // Test Steps

        // 0. Check that DatacopyCaller contract was deployed correctly
        Block block01 = world.getBlockByName("b01");
        Assertions.assertNotNull(block01);
        Assertions.assertEquals(1, block01.getTransactionsList().size());

        String contractAddress = world.getTransactionByName("tx01").getContractAddress().toHexString();

        // 1. Check that checkOriginalDatacopyWorksAsExpected (0xb7deb48b) is executed correctly

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);

        CallArguments args = new CallArguments();
        args.setFrom(acc.getAddress().toHexString());
        args.setTo("0x" + contractAddress);
        args.setData("0xb7deb48b"); // Call checkOriginalDatacopyWorksAsExpected() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        assertEquals(trueInHex, result);

        // 2. Check that checkOverriddenDatacopyWorksAsExpected (0x1cf61a8b) returns "false" before overriding

        CallArguments args2 = new CallArguments();
        args2.setFrom(acc.getAddress().toHexString());
        args2.setTo("0x" + contractAddress);
        args2.setData("0x1cf61a8b"); // Call checkOverriddenDatacopyWorksAsExpected() function
        CallArgumentsParam callArgumentsParam2 = TransactionFactoryHelper.toCallArgumentsParam(args2);

        String result2 = eth.call(callArgumentsParam2, blockIdentifierParam);
        assertEquals(falseInHex, result2);

        // 3. Check that checkOverriddenDatacopyWorksAsExpected (0x1cf61a8b) returns "true" after overriding

        AccountOverride accountOverride = new AccountOverride(identityPrecompiledAddress);
        accountOverride.setMovePrecompileToAddress(movePrecompiledTo);

        String result3 = eth.call(callArgumentsParam2, blockIdentifierParam, List.of(accountOverride));
        assertEquals(trueInHex, result3);

        // 4. Do another call with state override to another address, and check on the same address
        // used in Step 3, it must return "false" as contract is not moved there anymore.

        AccountOverride newAccountOverride = new AccountOverride(identityPrecompiledAddress);
        accountOverride.setMovePrecompileToAddress(new RskAddress("0x0000000000000000000000000000000000000002"));

        String result4 = eth.call(callArgumentsParam2, blockIdentifierParam, List.of(newAccountOverride));
        assertEquals(falseInHex, result4);

    }

    private String deployContractAndGetAddressFromDsl(String dslContractPath, World world) throws DslProcessorException, FileNotFoundException{
        DslParser parser = DslParser.fromResource(dslContractPath);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
        final Transaction tx01 = world.getTransactionByName("tx01");
        return tx01.getContractAddress().toHexString();
    }
}
