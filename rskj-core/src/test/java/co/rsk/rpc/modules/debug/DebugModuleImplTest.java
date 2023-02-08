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
import co.rsk.net.MessageHandler;
import co.rsk.net.handler.quota.TxQuota;
import co.rsk.net.handler.quota.TxQuotaChecker;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebugModuleImplTest {

    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private MessageHandler messageHandler;
    private TxQuotaChecker txQuotaChecker;

    private DebugModuleImpl debugModule;

    @BeforeEach
    void setup() {
        blockStore = Web3Mocks.getMockBlockStore();
        receiptStore = Web3Mocks.getMockReceiptStore();
        messageHandler = Web3Mocks.getMockMessageHandler();
        txQuotaChecker = mock(TxQuotaChecker.class);

        debugModule = new DebugModuleImpl(blockStore, receiptStore, messageHandler, Web3Mocks.getMockBlockExecutor(), txQuotaChecker);
    }

    @Test
    void debug_wireProtocolQueueSize_basic() {
        String result = debugModule.wireProtocolQueueSize();
        try {
            HexUtils.jsonHexToLong(result);
        } catch (NumberFormatException e) {
            Assertions.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    void debug_wireProtocolQueueSize_value() {
        when(messageHandler.getMessageQueueSize()).thenReturn(5L);
        String result = debugModule.wireProtocolQueueSize();
        try {
            long value = HexUtils.jsonHexToLong(result);
            Assertions.assertEquals(5L, value);
        } catch (NumberFormatException e) {
            Assertions.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    void debug_traceTransaction_retrieveUnknownTransactionAsNull() {
        byte[] hash = HexUtils.stringHexToByteArray("0x00");

        when(receiptStore.getInMainChain(hash, blockStore)).thenReturn(Optional.empty());

        JsonNode result = debugModule.traceTransaction("0x00", null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode resultWithNoOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);
        JsonNode resultWithEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), Collections.emptyMap());

        Assertions.assertEquals(resultWithNoOptions, resultWithEmptyOptions);

        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableStorage", "true");

        JsonNode resultWithNonEmptyOptions = debugModule.traceTransaction(transaction.getHash().toJsonString(), traceOptions);

        Assertions.assertEquals(resultWithNoOptions, resultWithNonEmptyOptions);
    }

    @Test
    void debug_traceBlock_retrieveUnknownBlockAsNull() throws Exception {
        byte[] hash = HexUtils.stringHexToByteArray("0x00");

        when(blockStore.getBlockByHash(hash)).thenReturn(null);

        JsonNode result = debugModule.traceBlock("0x00", null);

        Assertions.assertNull(result);
    }

    @Test
    void debug_traceBlock_retrieveSimpleContractsCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/contracts10.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Block block = world.getBlockByName("b01");

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        JsonNode result = debugModule.traceBlock(block.getHash().toJsonString(), null);

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

        DebugModuleImpl debugModule = new DebugModuleImpl(world.getBlockStore(), receiptStore, messageHandler, world.getBlockExecutor(), null);

        Map<String, String> traceOptions = new HashMap<>();
        traceOptions.put("disableStack", "true");
        traceOptions.put("disableMemory", "true");
        traceOptions.put("disableStorage", "true");

        JsonNode witnessResult = debugModule.traceTransaction(transaction.getHash().toJsonString(), null);
        JsonNode result = debugModule.traceTransaction(transaction.getHash().toJsonString(), traceOptions);

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
        when(txQuotaChecker.getTxQuota(address)).thenReturn(txQuotaCreated);

        TxQuota txQuotaRetrieved = debugModule.accountTransactionQuota(rawAddress);

        Assertions.assertNotNull(txQuotaRetrieved);

        JsonNode node = new ObjectMapper().valueToTree(txQuotaRetrieved);

        Assertions.assertEquals(creationTimestamp, node.get("timestamp").asLong());
        Assertions.assertEquals(initialQuota, node.get("availableVirtualGas").asDouble(), 0);
    }

    @Test
    void debug_accountTransactionQuota_whenNonExistingAddress_returnsNull() {
        RskAddress address = new RskAddress("0x7986b3df570230288501eea3d890bd66948c9b79");

        TxQuota txQuotaCreated = TxQuota.createNew(address, TestUtils.generateHash("txQuota"), 200L, System::currentTimeMillis);

        when(txQuotaChecker.getTxQuota(address)).thenReturn(txQuotaCreated);

        TxQuota txQuotaRetrieved = debugModule.accountTransactionQuota("0xbe182646a44fb90dc6501ab50d19e7c91078a35a");

        Assertions.assertNull(txQuotaRetrieved);
    }
}
