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

import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

/**
 * Created by ajlopez on 15/04/2020.
 */
public class VmDslTest {
    @Test
    public void invokeRecursiveContractsUsing400Levels() throws FileNotFoundException, DslProcessorException {
        System.gc();
        DslParser parser = DslParser.fromResource("dsl/recursive01.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b02");

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getTransactionsList().size());

        Transaction creationTransaction = world.getTransactionByName("tx01");

        Assertions.assertNotNull(creationTransaction);

        DataWord counterValue = world
                .getRepositoryLocator()
                .snapshotAt(block.getHeader())
                .getStorageValue(creationTransaction.getContractAddress(), DataWord.ZERO);

        Assertions.assertNotNull(counterValue);
        Assertions.assertEquals(200, counterValue.intValue());

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");

        Assertions.assertNotNull(transactionReceipt);

        byte[] status = transactionReceipt.getStatus();

        Assertions.assertNotNull(status);
        Assertions.assertEquals(1, status.length);
        Assertions.assertEquals(1, status[0]);
    }

    @Test
    public void invokeRecursiveContractsUsing401Levels() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/recursive02.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);

        processor.processCommands(parser);

        Block block = world.getBlockByName("b02");

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getTransactionsList().size());

        Transaction creationTransaction = world.getTransactionByName("tx01");

        Assertions.assertNotNull(creationTransaction);

        DataWord counterValue = world
                .getRepositoryLocator()
                .snapshotAt(block.getHeader())
                .getStorageValue(creationTransaction.getContractAddress(), DataWord.ZERO);

        Assertions.assertNull(counterValue);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");

        Assertions.assertNotNull(transactionReceipt);

        byte[] status = transactionReceipt.getStatus();

        Assertions.assertNotNull(status);
        Assertions.assertEquals(0, status.length);
    }
}
