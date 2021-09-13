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
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.CallTransaction;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;

import static org.junit.Assert.*;

/**
 * Created by patogallaiovlabs on 28/10/2020.
 *
 * The aim of this class is to test how it behaves when calling nested contracts through different ways.
 * 1 - Call contract through the interface: ContractA(addressA).buy(amount)
 * 2 - Call contract encoding abi call: addressA.call(abi.encodeWithSignature("buy(uint256)", amount))
 *
 * In (1) it should revert all the calls, since Solidity add "iszero" checks to all the calls. While in (2) it should not
 * revert, because we are taking care of the call, and the result its not handled.
 *
 */
public class NestedContractsTest {

    private static final CallTransaction.Function BUY_FUNCTION = CallTransaction.Function.fromSignature("buy", "uint");
    private static final String TX_CONTRACTRR = "tx01";
    private static final String TX_CONTRACTB = "tx02";
    private static final String TX_CONTRACTA = "tx03";
    private static final String BLOCK_CONTRACT_CREATE = "b01";
    private static final String BLOCK_TRANSACTION_FAIL = "b02";
    private static final String BLOCK_TRANSACTION_SUCCESS = "b03";

    private World world;
    private WorldDslProcessor processor;
    private EthModule ethModule;

    /** ------------------------ **
     *  SETUP
     ** ------------------------ **/
    @Before
    public void setup() {
        world = new World();
        processor = new WorldDslProcessor(world);
        ethModule = buildEthModule(world);
    }

    /** ------------------------ **
     *  TESTS
     ** ------------------------ **/

    @Test
    public void testNested_interfaceCall_require() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/contract_call/contract_nested_interface_calls.txt"));

        assertFalse(world.getTransactionReceiptByName("tx04").isSuccessful());
        assertTrue(world.getTransactionReceiptByName("tx05").isSuccessful());

        // Assert state: RevertReason.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTRR, BLOCK_CONTRACT_CREATE, DataWord.ZERO));
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTRR, BLOCK_TRANSACTION_FAIL, DataWord.ZERO));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTRR, BLOCK_TRANSACTION_SUCCESS, DataWord.ZERO));

        // Assert state: ContractB.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTB, BLOCK_CONTRACT_CREATE, DataWord.ONE));
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTB, BLOCK_TRANSACTION_FAIL, DataWord.ONE));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTB, BLOCK_TRANSACTION_SUCCESS, DataWord.ONE));

        // Assert state: ContractA.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTA, BLOCK_CONTRACT_CREATE, DataWord.ONE));
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTA, BLOCK_TRANSACTION_FAIL, DataWord.ONE));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTA, BLOCK_TRANSACTION_SUCCESS, DataWord.ONE));

        //Failed Call ContractA.buy(0) -> 0 > 0
        final String contractA = getContractAddressString(TX_CONTRACTA);
        CallArguments args = buildArgs(contractA, Hex.toHexString(BUY_FUNCTION.encode(0)));
        try {
            ethModule.call(args, "latest");
            fail();
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("Negative value"));
        }

        //Success Call -> 2 > 0
        args = buildArgs(contractA, Hex.toHexString(BUY_FUNCTION.encode(2)));
        final String call = ethModule.call(args, "latest");
        //assertEquals("0x" + DataWord.valueOf(2).toString(), call);
    }

    @Test
    public void testNested_ABICall_require() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/contract_call/contract_nested_abi_calls.txt"));
        world.getRepository().commit();

        assertTrue(world.getTransactionReceiptByName("tx04").isSuccessful());
        assertTrue(world.getTransactionReceiptByName("tx05").isSuccessful());

        // Assert state: RevertReason.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTRR, BLOCK_CONTRACT_CREATE, DataWord.ZERO));
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTRR, BLOCK_TRANSACTION_FAIL, DataWord.ZERO));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTRR, BLOCK_TRANSACTION_SUCCESS, DataWord.ZERO));

        // Assert state: ContractB.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTB, BLOCK_CONTRACT_CREATE, DataWord.ONE));
        //Null == Zero
        assertEquals(null, getStorageValue(TX_CONTRACTB, BLOCK_TRANSACTION_FAIL, DataWord.ONE));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTB, BLOCK_TRANSACTION_SUCCESS, DataWord.ONE));

        // Assert state: ContractA.a
        assertEquals(DataWord.valueOf(11), getStorageValue(TX_CONTRACTA, BLOCK_CONTRACT_CREATE, DataWord.ONE));
        //Null == Zero
        assertEquals(null, getStorageValue(TX_CONTRACTA, BLOCK_TRANSACTION_FAIL, DataWord.ONE));
        assertEquals(DataWord.valueOf(2), getStorageValue(TX_CONTRACTA, BLOCK_TRANSACTION_SUCCESS, DataWord.ONE));

        //Failed Call ContractA.buy(0) -> 0 > 0
        final String contractA = getContractAddressString("tx03");
        CallArguments args = buildArgs(contractA, Hex.toHexString(BUY_FUNCTION.encode(0)));
        String call = ethModule.call(args, "latest");
        assertEquals("0x" + DataWord.valueOf(0).toString(), call);

        //Success Call -> 2 > 0
        args = buildArgs(contractA, Hex.toHexString(BUY_FUNCTION.encode(2)));
        call = ethModule.call(args, "latest");
        assertEquals("0x" + DataWord.valueOf(2).toString(), call);

    }

    /** ------------------------ **
     *  UTILITIES
     ** ------------------------ **/

    private DataWord getStorageValue(String contractTx, String blockId, DataWord param) {
        final RskAddress contractAddress = getContractAddress(contractTx);
        return world
                .getRepositoryLocator()
                .snapshotAt(world.getBlockByName(blockId).getHeader())
                .getStorageValue(contractAddress, param);
    }

    private RskAddress getContractAddress(String contractTx) {
        return world.getTransactionByName(contractTx).getContractAddress();
    }

    private String getContractAddressString(String contractTx) {
        return getContractAddress(contractTx).toHexString();
    }

    private CallArguments buildArgs(String toAddress, String data) {
        final CallArguments args = new CallArguments();
        args.setTo(toAddress);
        args.setData(data); // call to contract
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        return args;
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
                world.getTransactionPool(),
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(null, world.getBlockChain(), null, null),
                world.getRepositoryLocator(),
                null,
                null,
                world.getBridgeSupportFactory(),
                config.getGasEstimationCap());
    }
}

