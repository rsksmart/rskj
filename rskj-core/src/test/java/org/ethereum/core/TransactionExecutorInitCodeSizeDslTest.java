/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TransactionExecutorInitCodeSizeDslTest {

    @Test
    void testInitCodeSizeTransactionCreationValidationSuccessWithRSKIPActivated() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_success_with_rskip_activated.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", true);
    }

    @Test
    void testInitCodeSizeTransactionCreationValidationFailsNotEnoughGas() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_fails_not_enough_gas.txt", new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", false);
    }

    @Test
    void testInitCodeSizeTransactionCreationValidationDoesntFailDueNotEnoughGasIfRSKIPNotActivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_doesnt_fails_due_not_enough_gas_with_rskip_deactivated.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", true);
    }

    @Test
    void testInitCodeSizeTransactionCreationValidationSuccessWithRSKIP438Deactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_success_with_rskip_deactivated.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", true);
    }

    @Test
    void testInitCodeSizeTransactionCreationValidationFailsWithRSKIPActiveWithInitcodeSizeMaxReached() throws FileNotFoundException, DslProcessorException {
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_fails_with_rskip_active_with_initcode_size_max_reached.txt",  new TestSystemProperties());

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", false);
    }

    @Test
    void testInitCodeSizeTransactionCreationValidationSuccessWithRSKIPDeactivatedWithInitcodeSizeMaxReached() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );
        World world = createWorldAndProcessIt("dsl/initcode_size_rskip438/tx_creation_validation_success_with_rskip_deactivated_with_initcode_size_max_reached.txt", rskip438Disabled);

        assertTransactionExecutedWasAddedToBlockWithExpectedStatus(world, "txCreateContract", "b01", true);
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
        if(withSuccess) {
            Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
            TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(transactionName);
            assertNotNull(contractTransactionReceipt);
            byte[] status = contractTransactionReceipt.getStatus();
            assertNotNull(status);
            assertEquals(1, status.length);
            assertEquals(1, status[0]);
        } else {
            Assertions.assertEquals(0, bestBlock.getTransactionsList().size());
            TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(transactionName);
            assertNull(contractTransactionReceipt);
        }
    }
}
