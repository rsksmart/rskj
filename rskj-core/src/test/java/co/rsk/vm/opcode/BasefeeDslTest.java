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
package co.rsk.vm.opcode;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.util.TransactionReceiptUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BasefeeDslTest {

    @Test
    void testBASEFEE_whenActivated_behavesAsExpected() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/opcode/basefee/baseFeeActivatedTest.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        // There's one block (b01) containing only 1 transaction
        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        // There's a transaction called txTestBasefee
        Transaction txTestBasefee = world.getTransactionByName("txTestBasefee");
        Assertions.assertNotNull(txTestBasefee);

        // Transaction txTestBasefee has a transaction receipt
        TransactionReceipt txTestBasefeeReceipt = world.getTransactionReceiptByName("txTestBasefee");
        Assertions.assertNotNull(txTestBasefeeReceipt);

        // Transaction txTestBasefee has been processed correctly
        byte[] creationStatus = txTestBasefeeReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        // There's one block (b02) containing only 1 transaction
        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        // There's a transaction called txTestBasefeeOKCall
        Transaction txTestBasefeeOKCall = world.getTransactionByName("txTestBasefeeOKCall");
        Assertions.assertNotNull(txTestBasefeeOKCall);

        // Transaction txTestBasefeeOKCall has a transaction receipt
        TransactionReceipt txTestBasefeeOKCallReceipt = world.getTransactionReceiptByName("txTestBasefeeOKCall");
        Assertions.assertNotNull(txTestBasefeeOKCallReceipt);

        // Transaction txTestBasefeeOKCall has been processed correctly
        byte[] txTestBasefeeOKCallCreationStatus = txTestBasefeeOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestBasefeeOKCallCreationStatus);
        Assertions.assertEquals(1, txTestBasefeeOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestBasefeeOKCallCreationStatus[0]);

        // Check events
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestBasefeeOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestBasefeeOKCallReceipt, "ERROR", null));

        // There's one block (b03) containing only 1 transaction
        Block block3 = world.getBlockByName("b03");
        Assertions.assertNotNull(block3);
        Assertions.assertEquals(1, block3.getTransactionsList().size());

        // There's a transaction called txTestBasefeeErrorCall
        Transaction txTestBasefeeErrorCall = world.getTransactionByName("txTestBasefeeErrorCall");
        Assertions.assertNotNull(txTestBasefeeErrorCall);

        // Transaction txTestBasefeeErrorCall has a transaction receipt
        TransactionReceipt txTestBasefeeErrorCallReceipt = world.getTransactionReceiptByName("txTestBasefeeErrorCall");
        Assertions.assertNotNull(txTestBasefeeErrorCallReceipt);

        // Transaction txTestBasefeeErrorCall has been processed correctly
        byte[] txTestBasefeeErrorCallCreationStatus = txTestBasefeeErrorCallReceipt.getStatus();
        Assertions.assertNotNull(txTestBasefeeErrorCallCreationStatus);
        Assertions.assertEquals(1, txTestBasefeeErrorCallCreationStatus.length);
        Assertions.assertEquals(1, txTestBasefeeErrorCallCreationStatus[0]);

        // Check events
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestBasefeeErrorCallReceipt, "ERROR", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestBasefeeErrorCallReceipt, "OK", null));
    }

    @Test
    void testBASEFEE_whenNotActivated_BehavesAsExpected() throws FileNotFoundException, DslProcessorException {

        // Config Spies Setup

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig activationConfig = config.getActivationConfig();

        TestSystemProperties configSpy = spy(config);
        ActivationConfig activationConfigSpy = spy(activationConfig);

        doReturn(activationConfigSpy).when(configSpy).getActivationConfig();
        doReturn(false).when(activationConfigSpy).isActive(eq(ConsensusRule.RSKIP412), anyLong());

        // Test Setup

        DslParser parser = DslParser.fromResource("dsl/opcode/basefee/baseFeeNotActivatedTest.txt");
        World world = new World(configSpy);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        // There's one block (b01) containing only 1 transaction
        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        // There's a transaction called txTestBasefee
        Transaction txTestBasefee = world.getTransactionByName("txTestBasefee");
        Assertions.assertNotNull(txTestBasefee);

        // Transaction txTestBasefee has a transaction receipt
        TransactionReceipt txTestBasefeeReceipt = world.getTransactionReceiptByName("txTestBasefee");
        Assertions.assertNotNull(txTestBasefeeReceipt);

        // Transaction txTestBasefee has been processed correctly
        byte[] creationStatus = txTestBasefeeReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        verify(activationConfigSpy, atLeast(1)).isActive(eq(ConsensusRule.RSKIP412), eq(2L));

        // There's one block (b02) containing only 1 transaction
        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        // There's a transaction called txTestBasefeeNotActivated
        Transaction txTestBasefeeNotActivated = world.getTransactionByName("txTestBasefeeNotActivated");
        Assertions.assertNotNull(txTestBasefeeNotActivated);

        // Transaction txTestBasefeeNotActivated has a transaction receipt
        TransactionReceipt txTestBasefeeNotActivatedReceipt = world.getTransactionReceiptByName("txTestBasefeeNotActivated");
        Assertions.assertNotNull(txTestBasefeeNotActivatedReceipt);

        // Transaction txTestBasefeeNotActivated has failed
        byte[] txTestBasefeeNotActivatedCreationStatus = txTestBasefeeNotActivatedReceipt.getStatus();
        Assertions.assertNotNull(txTestBasefeeNotActivatedCreationStatus);
        Assertions.assertEquals(0, txTestBasefeeNotActivatedCreationStatus.length);
    }

}
