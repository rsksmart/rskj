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

package co.rsk.vm.opcode;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.util.TransactionReceiptUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TransientStorageDslTest {

    @Test
    void testTransientStorageOpcodesExecutionsWithRSKIPActivated() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageOpCodes";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b03", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "OK", null));

        String checkingOpcodesTxName2 = "txTestTransientStorageOpCodesOtherValue";
        TransactionReceipt txReceipt2 = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName2, "b04", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt2, "OK", null));
    }

    @Test
    void testTransientStorageOpcodesShareMemorySameTransaction() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageNestedTransactionShareMemory";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b05", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "OK", null));
    }

    @Test
    void testTransientStorageOpcodesDoesntShareMemoryFromOtherContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageNestedTransactionOtherContractDoesntShareMemory";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b06", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "ERROR", new String[]{"bytes32"}));
    }

    @Test
    void testTransientStorageOpcodesExecutionFailsWithRSKIPDeactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip446Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World(rskip446Disabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageOpCodes";
       assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b03", false);
    }

    @Test
    void testTransientStorageTestsEip1153BasicScenarios() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_eip1153_basic_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String txTestTloadAfterSstore = "txTestTloadAfterSstore";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterSstore, "b02", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTloadAfterTstore = "txTestTloadAfterTstore";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterTstore, "b03", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTransientUnsetValues = "txTestTransientUnsetValues";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTransientUnsetValues, "b04", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTloadAfterTstoreIsZero = "txTestTloadAfterTstoreIsZero";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterTstoreIsZero, "b04", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testTransientStorageTestsCreateContextsOnlyConstructorCode() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_create_context_only_constructor_code.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);
        Assertions.assertEquals(2, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    private static TransactionReceipt assertTransactionReceiptWithStatus(World world, String txName, String blockName, boolean withSuccess) {
        Transaction txCreation = world.getTransactionByName(txName);
        assertNotNull(txCreation);

        Block block = world.getBlockByName(blockName);
        assertEquals(1, block.getTransactionsList().size());

        TransactionReceipt txReceipt = world.getTransactionReceiptByName(txName);
        assertNotNull(txReceipt);

        byte[] status = txReceipt.getStatus();
        assertNotNull(status);

        if(withSuccess) {
            assertEquals(1, status.length);
            assertEquals(1, status[0]);
        } else {
            assertEquals(0, status.length);
        }
        return txReceipt;
    }

}