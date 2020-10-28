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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.config.Constants;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyByte;

/**
 * Created by patogallaiovlabs on 28/10/2020.
 */
public class EthModuleDLSTest {
    @Test
    public void testCall_getRevertReason() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/eth_module/revert_reason.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status = transactionReceipt.getStatus();

        Assert.assertNotNull(status);
        Assert.assertEquals(0, status.length);

        EthModule eth = buildEthModule(world);
        final Transaction tx01 = world.getTransactionByName("tx01");
        final Web3.CallArguments args = new Web3.CallArguments();
        args.to = tx01.getContractAddress().toHexString(); //"6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.data = "d96a094a0000000000000000000000000000000000000000000000000000000000000000"; // call to contract with param value = 0
        args.value = "0";
        args.nonce = "1";
        args.gas = "10000000";
        try {
            eth.call(args, "0x2");
            fail();
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("Negative value."));
        }

        args.data = "d96a094a0000000000000000000000000000000000000000000000000000000000000001"; // call to contract with param value = 1
        final String call = eth.call(args, "0x2");
        assertEquals("0x00", call);
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
