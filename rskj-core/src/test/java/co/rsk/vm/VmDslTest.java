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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by ajlopez on 15/04/2020.
 */
class VmDslTest {
    @Test
    void invokeRecursiveContractsUsing400Levels() throws FileNotFoundException, DslProcessorException {
        System.gc();
        DslParser parser = DslParser.fromResource("dsl/recursive01.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b02");

        assertNotNull(block);
        Assertions.assertEquals(1, block.getTransactionsList().size());

        Transaction creationTransaction = world.getTransactionByName("tx01");

        assertNotNull(creationTransaction);

        DataWord counterValue = world
                .getRepositoryLocator()
                .snapshotAt(block.getHeader())
                .getStorageValue(creationTransaction.getContractAddress(), DataWord.ZERO);

        assertNotNull(counterValue);
        Assertions.assertEquals(200, counterValue.intValue());

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");

        assertNotNull(transactionReceipt);

        byte[] status = transactionReceipt.getStatus();

        assertNotNull(status);
        Assertions.assertEquals(1, status.length);
        Assertions.assertEquals(1, status[0]);
    }

    @Test
    void invokeRecursiveContractsUsing401Levels() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/recursive02.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Block block = world.getBlockByName("b02");

        assertNotNull(block);
        Assertions.assertEquals(1, block.getTransactionsList().size());

        Transaction creationTransaction = world.getTransactionByName("tx01");

        assertNotNull(creationTransaction);

        DataWord counterValue = world
                .getRepositoryLocator()
                .snapshotAt(block.getHeader())
                .getStorageValue(creationTransaction.getContractAddress(), DataWord.ZERO);

        Assertions.assertNull(counterValue);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");

        assertNotNull(transactionReceipt);

        byte[] status = transactionReceipt.getStatus();

        assertNotNull(status);
        Assertions.assertEquals(0, status.length);
    }

    @Test
    void testPush0() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/push0test.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Block block2 = world.getBlockByName("b02");
        assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction creationTransactionNew = world.getTransactionByName("txCreateNew");
        assertNotNull(creationTransactionNew);
        TransactionReceipt creationTransactionReceiptNew = world.getTransactionReceiptByName("txCallNew");
        assertNotNull(creationTransactionReceiptNew);
        byte[] statusCreationNew = creationTransactionReceiptNew.getStatus();
        assertNotNull(statusCreationNew);
        Assertions.assertEquals(1, statusCreationNew.length);
        Assertions.assertEquals(1, statusCreationNew[0]);

        Transaction callTransactionNew = world.getTransactionByName("txCallNew");
        assertNotNull(callTransactionNew);
        TransactionReceipt callTransactionReceiptNew = world.getTransactionReceiptByName("txCallNew");
        assertNotNull(callTransactionReceiptNew);
        byte[] statusCallNew = callTransactionReceiptNew.getStatus();
        assertNotNull(statusCallNew);
        Assertions.assertEquals(1, statusCallNew.length);
        Assertions.assertEquals(1, statusCallNew[0]);

        short newGas = ByteBuffer.wrap(callTransactionReceiptNew.getGasUsed()).getShort();

        Block block4 = world.getBlockByName("b04");
        assertNotNull(block4);
        Assertions.assertEquals(1, block4.getTransactionsList().size());


        Transaction creationTransactionOld = world.getTransactionByName("txCreateOld");
        assertNotNull(creationTransactionOld);
        TransactionReceipt creationTransactionReceiptOld = world.getTransactionReceiptByName("txCreateOld");
        assertNotNull(creationTransactionReceiptOld);
        byte[] statusCreationOld = creationTransactionReceiptNew.getStatus();
        assertNotNull(statusCreationOld);
        Assertions.assertEquals(1, statusCreationOld.length);
        Assertions.assertEquals(1, statusCreationOld[0]);

        Transaction callTransactionOld = world.getTransactionByName("txCallOld");
        assertNotNull(callTransactionOld);
        TransactionReceipt callTransactionReceiptOld = world.getTransactionReceiptByName("txCallOld");
        assertNotNull(callTransactionReceiptOld);
        byte[] statusCallOld = callTransactionReceiptOld.getStatus();
        assertNotNull(statusCallOld);
        Assertions.assertEquals(1, statusCallOld.length);
        Assertions.assertEquals(1, statusCallOld[0]);

        short oldGas = ByteBuffer.wrap(callTransactionReceiptOld.getGasUsed()).getShort();
        assertTrue(newGas < oldGas);
    }

    @Test
    void testInitCodeSizeCREATEOpcodeValidationSuccessWithoutInitcodeCost() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE_opcode_validation_success_without_initcode_cost.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATEOpcodeValidationSuccessWithInitcodeCost() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE_opcode_validation_success_with_initcode_cost.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCode", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATEOpcodeValidationDoesntFailWithRSKIP438Deactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE_opcode_validation_doesnt_fail_with_RSKIP_deactivated.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATEOpcodeValidationFailWithRSKIP438Activated() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE_opcode_validation_fail_with_RSKIP_activated.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate", "b02", false);
    }

    @Test
    void testInitCodeSizeCREATE2OpcodeValidationSuccessWithoutInitcodeCost() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE2_opcode_validation_success_without_initcode_cost.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate2", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATE2OpcodeValidationSuccessWithInitCodeCost() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE2_opcode_validation_success_with_initcode_cost.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate2", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATE2OpcodeValidationDoesntFailIfRSKIP438Deactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE2_opcode_validation_doesnt_fail_with_RSKIP_deactivated.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate2", "b02", true);
    }

    @Test
    void testInitCodeSizeCREATE2OpcodeValidationFailIfRSKIP438Activated() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/CREATE2_opcode_validation_fail_with_RSKIP_activated.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractFactory", "b01", true);
        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContractViaOpCodeCreate2", "b02", false);
    }

    private static World createWorldAndProcessIt(String resourceName, TestSystemProperties rskip438Disabled) throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource(resourceName);
        World world = new World(rskip438Disabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);
        return world;
    }

    private void assertTransactionExecutedWasAddedToBlockWithExpectedStatus(World world, String transactionName, String blockName, boolean withSuccess) {
        Transaction contractTransaction = world.getTransactionByName(transactionName);
        assertNotNull(contractTransaction);
        Block bestBlock = world.getBlockByName(blockName);
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
        if(withSuccess) {
            TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(transactionName);
            assertNotNull(contractTransactionReceipt);
            byte[] status = contractTransactionReceipt.getStatus();
            assertNotNull(status);
            assertEquals(1, status.length);
            assertEquals(1, status[0]);
        } else {
            TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(transactionName);
            assertNotNull(contractTransactionReceipt);
            byte[] status = contractTransactionReceipt.getStatus();
            Assertions.assertEquals(0, status.length);
        }
    }
}
