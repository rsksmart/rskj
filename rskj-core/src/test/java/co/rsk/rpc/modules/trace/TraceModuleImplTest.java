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
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;

public class TraceModuleImplTest {
    @Test
    public void retrieveUnknownTransactionAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction("0x00");

        Assert.assertNull(result);
    }

    @Test
    public void retrieveUnknownBlockAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceBlock("0x0001020300010203000102030001020300010203000102030001020300010203");

        Assert.assertNull(result);
    }

    @Test
    public void retrieveSimpleContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("init"));
        Assert.assertNull(oresult.get("action").get("input"));
    }

    @Test
    public void retrieveEmptyContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts09.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

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
    public void retrieveMultiContractTraces() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = executeMultiContract(receiptStore);

        retrieveEmptyBlockTrace(world, receiptStore,"g00");
        retrieveEmptyBlockTrace(world, receiptStore,"0x00");
        retrieveEmptyBlockTrace(world, receiptStore,"earliest");

        retrieveNestedContractCreationBlockTrace(world, receiptStore,"b01");
        retrieveNestedContractCreationBlockTrace(world, receiptStore,"0x01");

        retrieveNestedContractCreationTrace(world, receiptStore,"tx01");
        retrieveNestedContractInvocationTrace(world, receiptStore, "tx02");
        retrieveNestedRevertedInvocationTrace(world, receiptStore, "tx03");
        retrieveSuicideInvocationTrace(world, receiptStore, "tx04");

        retrieveSuicideInvocationBlockTrace(world, receiptStore, "latest");
    }

    private static void retrieveEmptyBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(0, aresult.size());
    }

    private static void retrieveNestedContractCreationBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        Assert.assertEquals(4, result.size());

        for (int k = 0; k < 4; k++) {
            Assert.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assert.assertNotNull(oresult.get("type"));
            Assert.assertEquals("\"create\"", oresult.get("type").toString());

            Assert.assertNotNull(oresult.get("action"));
            Assert.assertNotNull(oresult.get("action").get("init"));
            Assert.assertNull(oresult.get("action").get("input"));
        }
    }

    private static void retrieveNestedContractCreationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(4, aresult.size());

        for (int k = 0; k < 4; k++) {
            Assert.assertTrue(aresult.get(k).isObject());

            ObjectNode oresult = (ObjectNode) aresult.get(k);

            Assert.assertNotNull(oresult.get("type"));
            Assert.assertEquals("\"create\"", oresult.get("type").toString());
        }
    }

    private static void retrieveNestedContractInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(4, aresult.size());

        for (int k = 0; k < 4; k++) {
            Assert.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assert.assertNotNull(oresult.get("type"));
            Assert.assertEquals("\"call\"", oresult.get("type").toString());
        }
    }

    private static void retrieveNestedRevertedInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(3, aresult.size());

        for (int k = 0; k < 3; k++) {
            Assert.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assert.assertNotNull(oresult.get("error"));
            Assert.assertEquals("\"Reverted\"", oresult.get("error").toString());
        }
    }

    private static void retrieveSuicideInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(2, aresult.size());

        Assert.assertTrue(result.get(1).isObject());

        ObjectNode oresult = (ObjectNode) result.get(1);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"suicide\"", oresult.get("type").toString());
        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNull(oresult.get("action").get("from"));
        Assert.assertNotNull(oresult.get("action").get("address"));
        Assert.assertNotNull(oresult.get("action").get("refundAddress"));
        Assert.assertNotNull(oresult.get("action").get("balance"));
    }

    private static void retrieveSuicideInvocationBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(2, aresult.size());

        Assert.assertTrue(result.get(1).isObject());

        ObjectNode oresult = (ObjectNode) result.get(1);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"suicide\"", oresult.get("type").toString());
        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNull(oresult.get("action").get("from"));
        Assert.assertNotNull(oresult.get("action").get("address"));
        Assert.assertNotNull(oresult.get("action").get("refundAddress"));
        Assert.assertNotNull(oresult.get("action").get("balance"));
    }

    @Test
    public void retrieveSimpleContractInvocationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

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
    public void retrieveSimpleAccountTransfer() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor());

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

    private static World executeMultiContract(ReceiptStore receiptStore) throws DslProcessorException, FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts08.txt");
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }
}
