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

package co.rsk.rpc.modules.trace;

import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;

public class TraceModuleTest {
    @Test
    public void retrieveUnknownTransactionAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction("0x00");

        Assert.assertNull(result);
    }

    @Test
    public void retrieveSimpleContractCreationTrace()  throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());
    }

    @Test
    public void retrieveSimpleContractInvocationTrace()  throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"call\"", oresult.get("type").toString());
    }

    @Test
    public void retrieveSimpleAccountTransfer()  throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"call\"", oresult.get("type").toString());
    }
}
