/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.DataWord;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


public class NestedCallsStackDepthTest {

    private static final CallTransaction.Function CALL_GCSD_FUNCTION = CallTransaction.Function.fromSignature("callGetCallStackDepth");

    private World world;
    private WorldDslProcessor processor;
    private EthModule ethModule;

    /** ------------------------ **
     *  SETUP
     ** ------------------------ **/
    @BeforeEach
    void setup() {
        TestSystemProperties config = new TestSystemProperties();
        buildEthModule(config);
    }

    /** ------------------------ **
     *  TESTS
     ** ------------------------ **/

    @Test
    void testNestedContractCallsGetCallStackDepth() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        final String contractA = getContractAddressString("tx03");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_GCSD_FUNCTION.encode()));
        String call = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
        assertEquals("0x" + DataWord.valueOf(3).toString(), call);
    }

    @Test
    void testContractCallsGetCallStackDepth() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        final String contractA = getContractAddressString("tx01");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_GCSD_FUNCTION.encode()));
        String call = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
        assertEquals("0x" + DataWord.valueOf(1).toString(), call);
    }

    @Test
    void testContractConsensusInactive() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip203Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.arrowhead600", ConfigValueFactory.fromAnyRef(-1))
        );
        buildEthModule(rskip203Disabled);

        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        final String contractA = getContractAddressString("tx01");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_GCSD_FUNCTION.encode()));
        try {
            ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
            fail("Should throw a RskJsonRpcRequestException");
        } catch (RskJsonRpcRequestException rskje) {
            MatcherAssert.assertThat(rskje.getMessage(), Matchers.containsString("transaction reverted"));
        }
    }

    /** ------------------------ **
     *  UTILITIES
     ** ------------------------ **/

    private RskAddress getContractAddress(String contractTx) {
        return world.getTransactionByName(contractTx).getContractAddress();
    }

    private String getContractAddressString(String contractTx) {
        return "0x" + getContractAddress(contractTx).toHexString();
    }

    private CallArguments buildArgs(String toAddress, String data) {
        final CallArguments args = new CallArguments();
        args.setTo(toAddress);
        args.setData("0x" + data); // call to contract
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        return args;
    }

    private void buildEthModule(TestSystemProperties config) {
        world = new World(config);
        processor = new WorldDslProcessor(world);
        TransactionExecutorFactory executor = EthModuleTestUtils.buildCustomExecutorFactory(
                world,
                config,
                null,
                new BlockFactory(config.getActivationConfig()),
                null);
        ethModule = EthModuleTestUtils.buildCustomEthModule(world, executor, config);
    }
}
