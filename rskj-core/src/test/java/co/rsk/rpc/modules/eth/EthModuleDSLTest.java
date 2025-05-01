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
    void testCall_StateOverride_stateIsOverride() throws DslProcessorException, FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/eth_module/simple_contract.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        final Transaction tx01 = world.getTransactionByName("tx01");
        String contractAddress = tx01.getContractAddress().toHexString();


        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress);
        args.setData("0x6d4ce63c"); //call  get() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        assertEquals(10, HexUtils.jsonHexToInt(result));

        AccountOverride accountOverride = new AccountOverride(new RskAddress(contractAddress));
        DataWord key = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000000");
        DataWord value = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000014");
        accountOverride.setState(Map.of(key, value));

        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));
        assertEquals(20, HexUtils.jsonHexToInt(result2));
    }

    @Test
    void testCall_StateOverride_codeOverride() throws DslProcessorException, FileNotFoundException {
        /**
         * Runtime bytecode of the contract:
         *
         * // SPDX-License-Identifier: MIT
         * pragma solidity ^0.8.0;
         *
         * contract FakeContract {
         *     function get() public pure returns (uint) {
         *         return 999;
         *     }
         * }
         */
        String runtimeByteCode = "0x6103e760005260206000f3";
        DslParser parser = DslParser.fromResource("dsl/eth_module/simple_contract.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        final Transaction tx01 = world.getTransactionByName("tx01");
        String contractAddress = tx01.getContractAddress().toHexString();


        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress);
        args.setData("0x6d4ce63c"); //call  get() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        assertEquals(10, HexUtils.jsonHexToInt(result));

        AccountOverride accountOverride = new AccountOverride(new RskAddress(contractAddress));
        byte[] newCode = HexUtils.stringHexToByteArray(runtimeByteCode);
        accountOverride.setCode(newCode);

        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));
        assertEquals(999, HexUtils.jsonHexToInt(result2));
    }

    @Test
    void testCall_StateOverride_balanceOverride()throws DslProcessorException, FileNotFoundException {
        long defaultBalance = 30000L;
        DslParser parser = DslParser.fromResource("dsl/eth_module/check_balance.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        final Transaction tx01 = world.getTransactionByName("tx01");
        String contractAddress = tx01.getContractAddress().toHexString();
        Account acc = world.getAccountByName("acc1");

        EthModule eth = EthModuleTestUtils.buildBasicEthModule(world);
        final CallArguments args = new CallArguments();
        args.setFrom(acc.getAddress().toHexString());
        args.setTo("0x" + contractAddress);
        args.setData("0x4c738909"); //call  getMyBalance() function
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("latest");
        CallArgumentsParam callArgumentsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        String result = eth.call(callArgumentsParam, blockIdentifierParam);
        assertNotEquals(defaultBalance, HexUtils.jsonHexToInt(result));

        AccountOverride accountOverride = new AccountOverride(acc.getAddress());
        accountOverride.setBalance(BigInteger.valueOf(defaultBalance));
        String result2 = eth.call(callArgumentsParam, blockIdentifierParam, List.of(accountOverride));

        assertEquals(defaultBalance, HexUtils.jsonHexToInt(result2));
    }
}
