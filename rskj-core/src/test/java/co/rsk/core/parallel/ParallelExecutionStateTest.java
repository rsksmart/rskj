/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core.parallel;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

class ParallelExecutionStateTest {
    private World createWorld(String dsl, int rskip144) throws DslProcessorException {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144))
        );

        World world = new World(receiptStore, config);

        DslParser parser = new DslParser(dsl);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }

    private byte[] getStateRoot (World world) {
        return world.getBlockChain().getBestBlock().getHeader().getStateRoot();
    }

    private void testProcessingBothAndAssertStateRootEquals (String dsl) throws DslProcessorException {
        World parallel = this.createWorld(dsl, 0);
        World series = this.createWorld(dsl, -1);

        Assertions.assertArrayEquals(
                this.getStateRoot(series),
                this.getStateRoot(parallel)
        );
    }

    @Test
    void empty() throws DslProcessorException {
        this.testProcessingBothAndAssertStateRootEquals("block_chain g00");
    }

    @Test
    void oneTx() throws DslProcessorException {
        this.testProcessingBothAndAssertStateRootEquals("account_new acc1 10000000\n" +
                "account_new acc2 0\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiver acc2\n" +
                "    value 1000\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "\n" +
                "assert_best b01\n" +
                "assert_balance acc2 1000\n" +
                "assert_tx_success tx01\n" +
                "\n");
    }
}