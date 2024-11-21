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

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.MessageHandler;
import co.rsk.net.handler.quota.TxQuota;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.trace.DebugTracer;
import co.rsk.rpc.modules.debug.trace.RskTracer;
import co.rsk.rpc.modules.debug.trace.TraceProvider;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.util.HexUtils;
import co.rsk.util.TimeProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.rpc.Web3Mocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebugModuleImplTest {

    private BlockStore blockStoreMock;
    private ReceiptStore receiptStoreMock;
    private MessageHandler messageHandlerMock;
    private TxQuotaChecker txQuotaCheckerMock;
    private Web3InformationRetriever web3InformationRetrieverMock;

    private DebugModuleImpl mockedDebugModule;

    @BeforeEach
    void setup() {
        blockStoreMock = Web3Mocks.getMockBlockStore();
        receiptStoreMock = Web3Mocks.getMockReceiptStore();
        messageHandlerMock = Web3Mocks.getMockMessageHandler();
        txQuotaCheckerMock = mock(TxQuotaChecker.class);
        web3InformationRetrieverMock = mock(Web3InformationRetriever.class);
        mockedDebugModule = getDebugModule(blockStoreMock, Web3Mocks.getMockBlockExecutor(), receiptStoreMock, messageHandlerMock, txQuotaCheckerMock, web3InformationRetrieverMock);
    }

    @Test
    void debug_wireProtocolQueueSize_basic() {
        String result = mockedDebugModule.wireProtocolQueueSize();
        try {
            HexUtils.jsonHexToLong(result);
        } catch (NumberFormatException e) {
            Assertions.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    void debug_wireProtocolQueueSize_value() {
        when(messageHandlerMock.getMessageQueueSize()).thenReturn(5L);
        String result = mockedDebugModule.wireProtocolQueueSize();
        try {
            long value = HexUtils.jsonHexToLong(result);
            Assertions.assertEquals(5L, value);
        } catch (NumberFormatException e) {
            Assertions.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    void debug_traceTransaction_retrieveUnknownTransactionAsNull() throws Exception {
        byte[] hash = HexUtils.stringHexToByteArray("0x00");

        when(receiptStoreMock.getInMainChain(hash, blockStoreMock)).thenReturn(Optional.empty());

        JsonNode result = mockedDebugModule.traceTransaction("0x00");

        Assertions.assertNull(result);
    }

    @Test
    void debug_traceTransaction_retrieveSimpleContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assertions.assertTrue(oResult.get("error").textValue().isEmpty());
        Assertions.assertTrue(oResult.get("result").isTextual());
        JsonNode structLogs = oResult.get("structLogs");
        Assertions.assertTrue(structLogs.isArray());
        Assertions.assertTrue(structLogs.size() > 0);
    }

    @Test
    void debug_traceTransaction_retrieveEmptyContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts09.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assertions.assertTrue(oResult.get("error").isNull());
        Assertions.assertTrue(oResult.get("result").isNull());
        JsonNode structLogs = oResult.get("structLogs");
        Assertions.assertNull(structLogs);
    }

    @Test
    void debug_traceTransaction_retrieveSimpleContractInvocationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assertions.assertTrue(oResult.get("error").textValue().isEmpty());
        Assertions.assertTrue(oResult.get("result").textValue().isEmpty());
        JsonNode structLogs = oResult.get("structLogs");
        Assertions.assertTrue(structLogs.isArray());
        Assertions.assertTrue(structLogs.size() > 0);
    }

    @Test
    void debug_traceTransaction_retrieveSimpleAccountTransfer() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString());

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assertions.assertTrue(oResult.get("error").isNull());
        Assertions.assertTrue(oResult.get("result").isNull());
        JsonNode structLogs = oResult.get("structLogs");
        Assertions.assertNull(structLogs);
    }

    @Test
    void debug_traceTransaction_retrieveSimpleAccountTransferWithTraceOptions() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        JsonNode resultWithNoOptions = debugModule.traceTransaction(transaction.getHash().toJsonString());
        TraceOptions traceOptions = new TraceOptions(Collections.emptyMap());
        JsonNode resultWithEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), traceOptions, null);

        Assertions.assertEquals(resultWithNoOptions, resultWithEmptyOptions);

        Map<String, String> traceOptionMap = new HashMap<>();
        traceOptionMap.put("disableStorage", "true");
        TraceOptions traceOptions2 = new TraceOptions(traceOptionMap);
        JsonNode resultWithNonEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), traceOptions2, null);

        Assertions.assertEquals(resultWithNoOptions, resultWithNonEmptyOptions);
    }

    @Test
    void debug_traceBlockByHash_retrieveUnknownBlockAsNull() {
        byte[] hash = HexUtils.stringHexToByteArray("0x00");

        when(blockStoreMock.getBlockByHash(hash)).thenReturn(null);

        JsonNode result = mockedDebugModule.traceBlockByHash("0x00", null, null);

        Assertions.assertNull(result);
    }

    @Test
    void debug_traceBlockByHash_retrieveSimpleContractsCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts10.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b01");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);


        JsonNode result = debugModule.traceBlockByHash(block.getHash().toJsonString(), null, null);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode arrNode = (ArrayNode) result;
        arrNode.forEach(jsonNode -> {
            Assertions.assertTrue(jsonNode.isObject());
            ObjectNode oResult = (ObjectNode) jsonNode;
            Assertions.assertTrue(oResult.get("error").textValue().isEmpty());
            Assertions.assertTrue(oResult.get("result").isTextual());
            JsonNode structLogs = oResult.get("structLogs");
            Assertions.assertTrue(structLogs.isArray());
            Assertions.assertTrue(structLogs.size() > 0);
        });
    }

    @Test
    void debug_traceBlockByNumber_retrieveUnknownBlockAsNull() throws Exception {
        when(web3InformationRetrieverMock.getBlock("0x1")).thenReturn(Optional.empty());

        JsonNode result = mockedDebugModule.traceBlockByNumber("0x1", null, null);

        Assertions.assertNull(result);
    }

    @Test
    void debug_traceBlockByNumber_retrieveSimpleContractsCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts10.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b01");
        String blockNumber = HexUtils.toQuantityJsonHex(block.getNumber());
        when(web3InformationRetrieverMock.getBlock(blockNumber)).thenReturn(Optional.of(block));

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, web3InformationRetrieverMock);

        JsonNode result = debugModule.traceBlockByNumber(blockNumber, null, null);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isArray());

        ArrayNode arrNode = (ArrayNode) result;
        arrNode.forEach(jsonNode -> {
            Assertions.assertTrue(jsonNode.isObject());
            ObjectNode oResult = (ObjectNode) jsonNode;
            Assertions.assertTrue(oResult.get("error").textValue().isEmpty());
            Assertions.assertTrue(oResult.get("result").isTextual());
            JsonNode structLogs = oResult.get("structLogs");
            Assertions.assertTrue(structLogs.isArray());
            Assertions.assertTrue(structLogs.size() > 0);
        });
    }

    @Test
    void debug_traceTransaction_retrieveSimpleContractInvocationTrace_traceOptions_disableAllFields_OK() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx02");

        DebugModuleImpl debugModule = getDebugModule(world.getBlockStore(),  world.getBlockExecutor(), receiptStore, messageHandlerMock, null, null);

        Map<String, String> traceOptionsMap = new HashMap<>();
        traceOptionsMap.put("disableStack", "true");
        traceOptionsMap.put("disableMemory", "true");
        traceOptionsMap.put("disableStorage", "true");
        TraceOptions traceOptions = new TraceOptions(traceOptionsMap);
        JsonNode witnessResult = debugModule.traceTransaction(transaction.getHash().toJsonString());
        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), traceOptions, null);

        // Sanity Check

        Assertions.assertNotNull(witnessResult);
        Assertions.assertTrue(witnessResult.isObject());

        ObjectNode oWitnessResult = (ObjectNode) witnessResult;
        Assertions.assertTrue(oWitnessResult.get("error").textValue().isEmpty());
        Assertions.assertTrue(oWitnessResult.get("result").textValue().isEmpty());
        JsonNode witnessStructLogs = oWitnessResult.get("structLogs");
        Assertions.assertTrue(witnessStructLogs.isArray());
        Assertions.assertTrue(witnessStructLogs.size() > 0);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isObject());

        ObjectNode oResult = (ObjectNode) result;
        Assertions.assertTrue(oResult.get("error").textValue().isEmpty());
        Assertions.assertTrue(oResult.get("result").textValue().isEmpty());
        JsonNode structLogs = oResult.get("structLogs");
        Assertions.assertTrue(structLogs.isArray());
        Assertions.assertTrue(structLogs.size() > 0);

        // Check Filters

        Assertions.assertNotEquals(witnessResult, result);
        Assertions.assertFalse(witnessStructLogs.findValues("stack").isEmpty());
        Assertions.assertFalse(witnessStructLogs.findValues("memory").isEmpty());
        Assertions.assertFalse(witnessStructLogs.findValues("storage").isEmpty());
        Assertions.assertTrue(structLogs.findValues("stack").isEmpty());
        Assertions.assertTrue(structLogs.findValues("memory").isEmpty());
        Assertions.assertTrue(structLogs.findValues("storage").isEmpty());
    }

    @Test
    void debug_accountTransactionQuota_whenExistingAddress_returnsItsQuota() {
        String rawAddress = "0x7986b3df570230288501eea3d890bd66948c9b79";
        RskAddress address = new RskAddress(rawAddress);

        long initialQuota = 200L;

        long creationTimestamp = 222L;
        TimeProvider timeProvider = mock(TimeProvider.class);
        when(timeProvider.currentTimeMillis()).thenReturn(creationTimestamp);

        TxQuota txQuotaCreated = TxQuota.createNew(address, TestUtils.generateHash("rawAddress"), initialQuota, timeProvider);
        when(txQuotaCheckerMock.getTxQuota(address)).thenReturn(txQuotaCreated);

        TxQuota txQuotaRetrieved = mockedDebugModule.accountTransactionQuota(rawAddress);

        Assertions.assertNotNull(txQuotaRetrieved);

        JsonNode node = new ObjectMapper().valueToTree(txQuotaRetrieved);

        Assertions.assertEquals(creationTimestamp, node.get("timestamp").asLong());
        Assertions.assertEquals(initialQuota, node.get("availableVirtualGas").asDouble(), 0);
    }

    @Test
    void debug_accountTransactionQuota_whenNonExistingAddress_returnsNull() {
        RskAddress address = new RskAddress("0x7986b3df570230288501eea3d890bd66948c9b79");

        TxQuota txQuotaCreated = TxQuota.createNew(address, TestUtils.generateHash("txQuota"), 200L, System::currentTimeMillis);

        when(txQuotaCheckerMock.getTxQuota(address)).thenReturn(txQuotaCreated);

        TxQuota txQuotaRetrieved = mockedDebugModule.accountTransactionQuota("0xbe182646a44fb90dc6501ab50d19e7c91078a35a");

        Assertions.assertNull(txQuotaRetrieved);
    }

    private DebugModuleImpl getDebugModule(BlockStore bockStore, BlockExecutor blockExecutor, ReceiptStore receiptStore, MessageHandler messageHandler, TxQuotaChecker txQuotaChecker, Web3InformationRetriever web3InformationRetriever) {
        DebugTracer tracer = new RskTracer(bockStore, receiptStore, blockExecutor, web3InformationRetriever);
        TraceProvider traceProvider = new TraceProvider(List.of(tracer));
        return new DebugModuleImpl(traceProvider, messageHandler, txQuotaChecker);
    }
}
