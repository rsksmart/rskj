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
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceModuleImplTest {
    @Test
    public void retrieveUnknownTransactionAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction("0x00");

        Assertions.assertNull(result);
    }

    @Test
    public void retrieveUnknownBlockAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock("0x0001020300010203000102030001020300010203000102030001020300010203");

        Assertions.assertNull(result);
    }

    @Test
    public void retrieveSimpleContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(1, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNull(oresult.get("action").get("creationMethod"));
        Assertions.assertNotNull(oresult.get("action").get("init"));
        Assertions.assertNull(oresult.get("action").get("input"));
    }

    @Test
    public void retrieveEmptyContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts09.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(1, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());
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

    @Test
    public void retrieveTraces() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = executeMultiContract(receiptStore);

        retrieveTraceFilterEmpty(world, receiptStore);
        retrieveTraceFilter1Record(world, receiptStore);
        retrieveTraceFilter3Records(world, receiptStore);
        retrieveTraceFilterNext3RecordsAndOnly1Remains(world, receiptStore);
        retrieveTraceFilterByAddress(world, receiptStore);
    }

    @Test
    public void getASingleTrace() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = executeMultiContract(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        String transactionHash = "0x64cbd00a73bad9df13ee188931c84555a5662057e6381b3476bdc20ab3c09ef3";
        JsonNode result = traceModule.traceGet(transactionHash, Stream.of("0x0").collect(Collectors.toList()));

        Assertions.assertNotNull(result);
        Assertions.assertEquals(result.get("transactionHash").asText(), transactionHash);
        Assertions.assertEquals(result.get("action").get("from").asText(),"0xa0663f719962ec10bb57865532bef522059dfd96");
    }

    private static void retrieveEmptyBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(0, aresult.size());
    }

    private static void retrieveNestedContractCreationBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        Assertions.assertEquals(4, result.size());

        for (int k = 0; k < 4; k++) {
            Assertions.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assertions.assertNotNull(oresult.get("type"));
            Assertions.assertEquals("\"create\"", oresult.get("type").toString());

            Assertions.assertNotNull(oresult.get("action"));

            if (k > 0) {
                Assertions.assertNotNull(oresult.get("action").get("creationMethod"));
                Assertions.assertEquals("\"create\"", oresult.get("action").get("creationMethod").toString());
            }

            Assertions.assertNotNull(oresult.get("action").get("init"));
            Assertions.assertNull(oresult.get("action").get("input"));
        }
    }

    private static void retrieveNestedContractCreationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(4, aresult.size());

        for (int k = 0; k < 4; k++) {
            Assertions.assertTrue(aresult.get(k).isObject());

            ObjectNode oresult = (ObjectNode) aresult.get(k);

            Assertions.assertNotNull(oresult.get("type"));
            Assertions.assertEquals("\"create\"", oresult.get("type").toString());
        }
    }

    private static void retrieveNestedContractInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(4, aresult.size());

        for (int k = 0; k < 4; k++) {
            Assertions.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assertions.assertNotNull(oresult.get("type"));
            Assertions.assertEquals("\"call\"", oresult.get("type").toString());
        }
    }

    private static void retrieveNestedRevertedInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(3, aresult.size());

        for (int k = 0; k < 3; k++) {
            Assertions.assertTrue(result.get(k).isObject());

            ObjectNode oresult = (ObjectNode) result.get(k);

            Assertions.assertNotNull(oresult.get("error"));
            Assertions.assertEquals("\"Reverted\"", oresult.get("error").toString());
        }
    }

    private static void retrieveSuicideInvocationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(2, aresult.size());

        Assertions.assertTrue(result.get(1).isObject());

        ObjectNode oresult = (ObjectNode) result.get(1);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"suicide\"", oresult.get("type").toString());
        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNull(oresult.get("action").get("from"));
        Assertions.assertNotNull(oresult.get("action").get("address"));
        Assertions.assertNotNull(oresult.get("action").get("refundAddress"));
        Assertions.assertNotNull(oresult.get("action").get("balance"));
    }

    private static void retrieveSuicideInvocationBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(2, aresult.size());

        Assertions.assertTrue(result.get(1).isObject());

        ObjectNode oresult = (ObjectNode) result.get(1);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"suicide\"", oresult.get("type").toString());
        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNull(oresult.get("action").get("from"));
        Assertions.assertNotNull(oresult.get("action").get("address"));
        Assertions.assertNotNull(oresult.get("action").get("refundAddress"));
        Assertions.assertNotNull(oresult.get("action").get("balance"));
    }

    private static void retrieveTraceFilterEmpty(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setFromBlock("0x12300");
        traceFilterRequest.setToBlock("0x12301");

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(0, aresult.size());
    }

    private static void retrieveTraceFilter1Record(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(1);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(1, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("from"));
        Assertions.assertNotNull(oresult.get("action").get("init"));
        Assertions.assertNull(oresult.get("action").get("input"));
    }

    private static void retrieveTraceFilter3Records(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("from"));
        Assertions.assertNotNull(oresult.get("action").get("init"));
        Assertions.assertNull(oresult.get("action").get("input"));

        oresult = (ObjectNode) result.get(1);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());

        oresult = (ObjectNode) result.get(2);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());
    }

    private static void retrieveTraceFilterNext3RecordsAndOnly1Remains(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);
        traceFilterRequest.setAfter(3);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("creationMethod"));
    }

    private static void retrieveTraceFilterByAddress(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);
        traceFilterRequest.setAfter(3);
        traceFilterRequest.setFromAddress(Stream.of("0xa0663f719962ec10bb57865532bef522059dfd96").collect(Collectors.toList()));

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"create\"", oresult.get("type").toString());
    }

    @Test
    public void retrieveSimpleContractInvocationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(1, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"call\"", oresult.get("type").toString());
    }

    @Test
    public void retrieveSimpleAccountTransfer() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(1, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assertions.assertNotNull(oresult.get("type"));
        Assertions.assertEquals("\"call\"", oresult.get("type").toString());
    }

    @Test
    public void executeContractWithCall() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/call01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Account callAccount = world.getAccountByName("call");
        Account calledAccount = world.getAccountByName("called");

        Assertions.assertNotNull(callAccount);
        Assertions.assertNotNull(calledAccount);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(2, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("callType"));
        Assertions.assertEquals("\"call\"", oresult.get("action").get("callType").toString());
        Assertions.assertEquals("\"" + calledAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("to").toString());
        Assertions.assertEquals("\"" + callAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("from").toString());
    }

    @Test
    public void executeContractWithDelegateCall() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/delegatecall01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Account delegateCallAccount = world.getAccountByName("delegatecall");
        Account delegatedAccount = world.getAccountByName("delegated");

        Assertions.assertNotNull(delegateCallAccount);
        Assertions.assertNotNull(delegatedAccount);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(2, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("callType"));
        Assertions.assertEquals("\"delegatecall\"", oresult.get("action").get("callType").toString());
        Assertions.assertEquals("\"" + delegatedAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("to").toString());
        Assertions.assertEquals("\"" + delegateCallAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("from").toString());
    }

    @Test
    public void executeContractWithCreate2() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/create201.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assertions.assertEquals(2, aresult.size());
        Assertions.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assertions.assertNotNull(oresult.get("action"));
        Assertions.assertNotNull(oresult.get("action").get("creationMethod"));
        Assertions.assertEquals("\"create2\"", oresult.get("action").get("creationMethod").toString());
    }

    private static World executeMultiContract(ReceiptStore receiptStore) throws DslProcessorException, FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts08.txt");
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }
}
