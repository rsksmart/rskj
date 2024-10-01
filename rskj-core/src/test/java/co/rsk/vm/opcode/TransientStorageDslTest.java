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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransientStorageDslTest {

    @Test
    void testTransientStorageOpcodesExecutionsWithRSKIPActivated() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String contractCreationTx = "txTestTransientStorageContract";
        Transaction contractTransaction = world.getTransactionByName("txTestTransientStorageContract");
        assertNotNull(contractTransaction);

        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
        TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(contractCreationTx);

        assertNotNull(contractTransactionReceipt);
        byte[] status = contractTransactionReceipt.getStatus();
        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(1, status[0]);
    }

    @Test
    void testTransientStorageOpcodesExecutionsWithRSKIPDeactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip446Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/transaction_storage_rskip446/tload_tstore_basic_contract.txt");
        World world = new World(rskip446Disabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String contractCreationTx = "txTestTransientStorageContract";
        Transaction contractTransaction = world.getTransactionByName("txTestTransientStorageContract");
        assertNotNull(contractTransaction);

        Block bestBlock = world.getBlockByName("b01");
        Assertions.assertEquals(1, bestBlock.getTransactionsList().size());
        TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName(contractCreationTx);

        assertNotNull(contractTransactionReceipt);
        byte[] status = contractTransactionReceipt.getStatus();
        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(1, status[0]);
    }

}
