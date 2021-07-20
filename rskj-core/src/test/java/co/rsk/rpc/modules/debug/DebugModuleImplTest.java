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
package co.rsk.rpc.modules.debug;

import co.rsk.net.MessageHandler;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;
import static org.mockito.Mockito.when;

public class DebugModuleImplTest {

    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private MessageHandler messageHandler;

    private DebugModuleImpl debugModule;

    @Before
    public void setup(){
        blockStore = Web3Mocks.getMockBlockStore();
        receiptStore = Web3Mocks.getMockReceiptStore();
        messageHandler = Web3Mocks.getMockMessageHandler();

        debugModule = new DebugModuleImpl(blockStore, receiptStore, messageHandler, Web3Mocks.getMockBlockExecutor());
    }

    @Test
    public void debug_wireProtocolQueueSize_basic() throws IOException {
        String result = debugModule.wireProtocolQueueSize();
        try {
            TypeConverter.JSonHexToLong(result);
        } catch (NumberFormatException e) {
            Assert.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    public void debug_wireProtocolQueueSize_value() throws IOException {
        when(messageHandler.getMessageQueueSize()).thenReturn(5L);
        String result = debugModule.wireProtocolQueueSize();
        try {
            long value = TypeConverter.JSonHexToLong(result);
            Assert.assertEquals(5L, value);
        } catch (NumberFormatException e) {
            Assert.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    public void debug_traceTransaction_retrieveUnknownTransactionAsNull() throws Exception {
        byte[] hash = stringHexToByteArray("0x00");
        when(receiptStore.getInMainChain(hash, blockStore)).thenReturn(Optional.empty());

        JsonNode result = debugModule.traceTransaction("0x00", null);

        Assert.assertNull(result);
    }

    @Test
    public void debug_traceTransaction_retrieveSimpleContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assert.assertTrue(oResult.get("error").textValue().isEmpty());
        Assert.assertTrue(oResult.get("result").isTextual());
        JsonNode structLogs = oResult.get("structLogs");
        Assert.assertTrue(structLogs.isArray());
        Assert.assertTrue(structLogs.size() > 0);
    }

    @Test
    public void debug_traceTransaction_retrieveEmptyContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts09.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assert.assertTrue(oResult.get("error").isNull());
        Assert.assertTrue(oResult.get("result").isNull());
        JsonNode structLogs = oResult.get("structLogs");
        Assert.assertNull(structLogs);
    }

    @Test
    public void debug_traceTransaction_retrieveSimpleContractInvocationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assert.assertTrue(oResult.get("error").textValue().isEmpty());
        Assert.assertTrue(oResult.get("result").textValue().isEmpty());
        JsonNode structLogs = oResult.get("structLogs");
        Assert.assertTrue(structLogs.isArray());
        Assert.assertTrue(structLogs.size() > 0);
    }

    @Test
    public void debug_traceTransaction_retrieveSimpleAccountTransfer() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assert.assertTrue(oResult.get("error").isNull());
        Assert.assertTrue(oResult.get("result").isNull());
        JsonNode structLogs = oResult.get("structLogs");
        Assert.assertNull(structLogs);
    }

    @Test
    public void debug_traceTransaction_retrieveSimpleAccountTransferWithTraceOptions() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode resultWithNoOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);
        JsonNode resultWithEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), Collections.emptyMap());

        Assert.assertEquals(resultWithNoOptions, resultWithEmptyOptions);

        JsonNode resultWithNonEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), Collections.singletonMap("disableStorage", "true"));

        Assert.assertEquals(resultWithNoOptions, resultWithNonEmptyOptions);
    }

    @Test
    public void debug_traceBlock_retrieveUnknownBlockAsNull() throws Exception {
        byte[] hash = stringHexToByteArray("0x00");
        when(blockStore.getBlockByHash(hash)).thenReturn(null);

        JsonNode result = debugModule.traceBlock("0x00", null);

        Assert.assertNull(result);
    }

    @Test
    public void debug_traceBlock_retrieveSimpleContractsCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts10.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor());

        JsonNode result = debugModule.traceBlock(block.getHash().toJsonString(), null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode arrNode = (ArrayNode) result;
        arrNode.forEach(jsonNode -> {
            Assert.assertTrue(jsonNode.isObject());
            ObjectNode oResult = (ObjectNode) jsonNode;
            Assert.assertTrue(oResult.get("error").textValue().isEmpty());
            Assert.assertTrue(oResult.get("result").isTextual());
            JsonNode structLogs = oResult.get("structLogs");
            Assert.assertTrue(structLogs.isArray());
            Assert.assertTrue(structLogs.size() > 0);
        });
    }
}
