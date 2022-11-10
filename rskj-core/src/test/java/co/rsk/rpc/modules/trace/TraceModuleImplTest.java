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

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.mine.*;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.validators.DummyBlockValidationRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.time.Clock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;

public class TraceModuleImplTest {
    @Test
    public void retrieveUnknownTransactionAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction("0x00");

        Assert.assertNull(result);
    }

    @Test
    public void retrieveUnknownBlockAsNull() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock("0x0001020300010203000102030001020300010203000102030001020300010203");

        Assert.assertNull(result);
    }

    @Test
    public void retrievePendingBlock() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = executeMultiContract(receiptStore);
        ExecutionBlockRetriever executionBlockRetriever = createExecutionBlockRetriever(world);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), executionBlockRetriever);

        world.getTransactionPool().addTransaction(createSampleTransaction());

        JsonNode result = traceModule.traceBlock("pending");

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode arrResult = (ArrayNode) result;

        Assert.assertEquals(1, arrResult.size());
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

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNull(oresult.get("action").get("creationMethod"));
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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

    @Test
    public void retrieveTraces() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = executeMultiContract(receiptStore);

        retrieveTraceFilterEmpty(world, receiptStore);
        retrieveTraceFilterPending(world, receiptStore);
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

        Assert.assertNotNull(result);
        Assert.assertEquals(result.get("transactionHash").asText(), transactionHash);
        Assert.assertEquals(result.get("action").get("from").asText(),"0xa0663f719962ec10bb57865532bef522059dfd96");
    }

    private static void retrieveEmptyBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceBlock(block == null ? blkname : block.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(0, aresult.size());
    }

    private static void retrieveNestedContractCreationBlockTrace(World world, ReceiptStore receiptStore, String blkname) throws Exception {
        Block block = world.getBlockByName(blkname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

            if (k > 0) {
                Assert.assertNotNull(oresult.get("action").get("creationMethod"));
                Assert.assertEquals("\"create\"", oresult.get("action").get("creationMethod").toString());
            }

            Assert.assertNotNull(oresult.get("action").get("init"));
            Assert.assertNull(oresult.get("action").get("input"));
        }
    }

    private static void retrieveNestedContractCreationTrace(World world, ReceiptStore receiptStore, String txname) throws Exception {
        Transaction transaction = world.getTransactionByName(txname);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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

    private static void retrieveTraceFilterEmpty(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setFromBlock("0x12300");
        traceFilterRequest.setToBlock("0x12301");

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(0, aresult.size());
    }

    private static void retrieveTraceFilterPending(World world, ReceiptStore receiptStore) throws Exception {
        ExecutionBlockRetriever executionBlockRetriever = createExecutionBlockRetriever(world);

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), executionBlockRetriever);

        world.getTransactionPool().addTransaction(createSampleTransaction());

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setFromBlock("pending");
        traceFilterRequest.setToBlock("pending");

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode arrResult = (ArrayNode) result;

        Assert.assertEquals(1, arrResult.size());
    }

    private static ExecutionBlockRetriever createExecutionBlockRetriever(World world) {
        RskSystemProperties rskSystemProperties = world.getConfig();
        MiningConfig miningConfig = new MiningConfig(
                rskSystemProperties.coinbaseAddress(),
                rskSystemProperties.minerMinFeesNotifyInDollars(),
                rskSystemProperties.minerGasUnitInDollars(),
                rskSystemProperties.minerMinGasPrice(),
                rskSystemProperties.getNetworkConstants().getUncleListLimit(),
                rskSystemProperties.getNetworkConstants().getUncleGenerationLimit(),
                new GasLimitConfig(
                        rskSystemProperties.getNetworkConstants().getMinGasLimit(),
                        rskSystemProperties.getTargetGasLimit(),
                        rskSystemProperties.getForceTargetGasLimit()
                ),
                rskSystemProperties.isMinerServerFixedClock(),
                rskSystemProperties.workSubmissionRateLimitInMills()
        );
        BlockToMineBuilder builder = new BlockToMineBuilder(
                rskSystemProperties.getActivationConfig(),
                miningConfig,
                world.getRepositoryLocator(),
                world.getBlockStore(),
                world.getTransactionPool(),
                new DifficultyCalculator(
                        rskSystemProperties.getActivationConfig(),
                        rskSystemProperties.getNetworkConstants()
                ),
                new GasLimitCalculator(rskSystemProperties.getNetworkConstants()),
                new ForkDetectionDataCalculator(),
                new DummyBlockValidationRule(),
                new MinerClock(miningConfig.isFixedClock(), Clock.systemUTC()),
                new BlockFactory(rskSystemProperties.getActivationConfig()),
                world.getBlockExecutor(),
                new MinimumGasPriceCalculator(Coin.valueOf(miningConfig.getMinGasPriceTarget())),
                new MinerUtils()
        );

        return new ExecutionBlockRetriever(world.getBlockChain(), builder, mock(CompositeEthereumListener.class));
    }

    private static Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("cow").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        return new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .gasPrice(BigInteger.valueOf(200))
                .value(BigInteger.TEN)
                .build();
    }

    private static void retrieveTraceFilter1Record(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(1);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(1, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("from"));
        Assert.assertNotNull(oresult.get("action").get("init"));
        Assert.assertNull(oresult.get("action").get("input"));
    }

    private static void retrieveTraceFilter3Records(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("from"));
        Assert.assertNotNull(oresult.get("action").get("init"));
        Assert.assertNull(oresult.get("action").get("input"));

        oresult = (ObjectNode) result.get(1);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        oresult = (ObjectNode) result.get(2);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());
    }

    private static void retrieveTraceFilterNext3RecordsAndOnly1Remains(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);
        traceFilterRequest.setAfter(3);

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("creationMethod"));
    }

    private static void retrieveTraceFilterByAddress(World world, ReceiptStore receiptStore) throws Exception {
        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        TraceFilterRequest traceFilterRequest = new TraceFilterRequest();

        traceFilterRequest.setCount(3);
        traceFilterRequest.setAfter(3);
        traceFilterRequest.setFromAddress(Stream.of("0xa0663f719962ec10bb57865532bef522059dfd96").collect(Collectors.toList()));

        JsonNode result = traceModule.traceFilter(traceFilterRequest);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(3, aresult.size());

        ObjectNode oresult = (ObjectNode) result.get(0);

        Assert.assertNotNull(oresult.get("type"));
        Assert.assertEquals("\"create\"", oresult.get("type").toString());
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

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

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
    public void executeContractWithCall() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/call01.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Account callAccount = world.getAccountByName("call");
        Account calledAccount = world.getAccountByName("called");

        Assert.assertNotNull(callAccount);
        Assert.assertNotNull(calledAccount);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(2, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("callType"));
        Assert.assertEquals("\"call\"", oresult.get("action").get("callType").toString());
        Assert.assertEquals("\"" + calledAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("to").toString());
        Assert.assertEquals("\"" + callAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("from").toString());
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

        Assert.assertNotNull(delegateCallAccount);
        Assert.assertNotNull(delegatedAccount);

        Transaction transaction = world.getTransactionByName("tx01");

        TraceModuleImpl traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore, world.getBlockExecutor(), null);

        JsonNode result = traceModule.traceTransaction(transaction.getHash().toJsonString());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(2, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("callType"));
        Assert.assertEquals("\"delegatecall\"", oresult.get("action").get("callType").toString());
        Assert.assertEquals("\"" + delegatedAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("to").toString());
        Assert.assertEquals("\"" + delegateCallAccount.getAddress().toJsonString() + "\"", oresult.get("action").get("from").toString());
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

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isArray());

        ArrayNode aresult = (ArrayNode)result;

        Assert.assertEquals(2, aresult.size());
        Assert.assertTrue(result.get(0).isObject());

        ObjectNode oresult = (ObjectNode)result.get(1);

        Assert.assertNotNull(oresult.get("action"));
        Assert.assertNotNull(oresult.get("action").get("creationMethod"));
        Assert.assertEquals("\"create2\"", oresult.get("action").get("creationMethod").toString());
    }

    private static World executeMultiContract(ReceiptStore receiptStore) throws DslProcessorException, FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts08.txt");
        World world = new World(receiptStore);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }
}
