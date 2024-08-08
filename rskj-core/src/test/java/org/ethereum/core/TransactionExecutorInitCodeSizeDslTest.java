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

public class TransactionExecutorInitCodeSizeDslTest {

    @Test
    void testInitCodeSizeValidationSuccess() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_success_rskip_activated.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
    }

    @Test
    void testInitCodeSizeValidationFailsDueNotEnoughGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_just_enough_gas.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(0, bestBlock.getTransactionsList().size());
    }

    @Test
    void testInitCodeSizeValidationDoesntFailDueNotEnoughGasIfRSKIPNotActivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_just_enough_gas_without_rskip_activated.txt");
        World world = new World(rskip438Disabled);

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
    }

    @Test
    void testInitCodeSizeValidationWithRSKIP438Deactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_success_rskip_deactivated.txt");
        World world = new World(rskip438Disabled);

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
    }

    @Test
    void testInitCodeSizeValidationFails() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_higher_initcode_size_rskip_activated.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(0, bestBlock.getTransactionsList().size());
    }

    @Test
    void testInitCodeSizeValidationDoesntFailIfRSKIP438Deactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip438Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/initcode_rskip438/tx_test_higher_initcode_size_rskip_deactivated.txt");
        World world = new World(rskip438Disabled);

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Transaction contractCreationTransaction = world.getTransactionByName("txCreateContract");
        Assertions.assertNotNull(contractCreationTransaction);
        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
    }
}
