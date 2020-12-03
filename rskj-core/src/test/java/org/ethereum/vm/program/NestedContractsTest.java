package org.ethereum.vm.program;
/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.config.Constants;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;

import static org.junit.Assert.*;

/**
 * Created by patogallaiovlabs on 28/10/2020.
 */
public class NestedContractsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(co.rsk.rpc.modules.eth.EthModuleDLSTest.class);
    private static final String BCK_ID = "0x2";

    @Test
    public void testNestedABICall_require() throws FileNotFoundException, DslProcessorException {
        World world = new World();
        EthModule eth = setupEthModule("dsl/contract_call/contract_nested_abi_calls.txt", world);

        final String toAddress = world.getTransactionByName("tx03").getContractAddress().toHexString();

        //Failed Call -> 0 > 0
        Web3.CallArguments args = buildArgs(toAddress, "0");
        try {
            eth.call(args, BCK_ID);
            fail();
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("Negative value."));
        }

        //Success Call -> 2 > 0
        args = buildArgs(toAddress, "2");
        final String call = eth.call(args, BCK_ID);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000002", call);
    }

    @Test
    public void testNestedCall_require() throws FileNotFoundException, DslProcessorException {
        World world = new World();
        EthModule eth = setupEthModule("dsl/contract_call/contract_nested_calls.txt", world);

        final String toAddress = world.getTransactionByName("tx03").getContractAddress().toHexString();

        //Failed Call -> 0 > 0
        Web3.CallArguments args = buildArgs(toAddress, "0");
        String call = eth.call(args, BCK_ID);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", call);

        //Success Call -> 2 > 0
        args = buildArgs(toAddress, "2");
        call = eth.call(args, BCK_ID);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000002", call);
    }

    private Web3.CallArguments buildArgs(String toAddress, String param) {
        final Web3.CallArguments args = new Web3.CallArguments();
        args.to = toAddress;
        args.data = "d96a094a000000000000000000000000000000000000000000000000000000000000000" + param; // call to contract with param value = 0
        args.value = "0";
        args.nonce = "1";
        args.gas = "10000000";
        return args;
    }

    private EthModule setupEthModule(String resourceName, World world) throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource(resourceName);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return buildEthModule(world);
    }

    private EthModule buildEthModule(World world) {
        final TestSystemProperties config = new TestSystemProperties();
        TransactionExecutorFactory executor = new TransactionExecutorFactory(
                config,
                world.getBlockStore(),
                null,
                null,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, world.getBridgeSupportFactory()),
                null
        );

        return new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                world.getBlockChain(),
                null,
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(null, world.getBlockChain(), null, null),
                null,
                null,
                null,
                world.getBridgeSupportFactory());
    }
}

