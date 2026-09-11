/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.db.TransactionInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Parity-style traces must report the transaction's real gas on the root frame even
 * though stored Type 1 / Type 2 / Type 4 receipts omit the per-tx {@code gasUsed} field.
 */
class TraceModuleTypedReceiptGasTest {

    private static World world;
    private static ReceiptStore receiptStore;
    private static TraceModuleImpl traceModule;

    /** b02: txType1 in sublist 0, txType2 in sublist 1, txType4 + txType2Second sequential. */
    private static final String[] TYPED_TX_NAMES = {"txType1", "txType2", "txType4", "txType2Second"};

    @BeforeAll
    static void setup() throws Exception {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0)));

        receiptStore = new ReceiptStoreImpl(new HashMapDB());
        world = new World(receiptStore, config);
        new WorldDslProcessor(world).processCommands(
                DslParser.fromResource("dsl/transaction/rskip545/rskip545TraceGasTest.txt"));

        traceModule = new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore,
                world.getBlockExecutor(), null, world.getBlockTxSignatureCache(), world.getConfig());
    }

    /** The fixture only exercises the bug while the typed txs really span the three sublists. */
    @Test
    void fixturePreconditions_typedTxsSpanThreeSublists() {
        Block block = world.getBlockByName("b02");

        assertArrayEquals(new short[]{1, 2}, block.getHeader().getTxExecutionSublistsEdges(),
                "txType1 must start sublist 0, txType2 sublist 1, txType4 the sequential one");

        for (String txName : TYPED_TX_NAMES) {
            assertEquals(0, storedReceipt(txName).getGasUsed().length,
                    txName + " must be stored as a four-field typed receipt with no gasUsed");
        }

        // Without a typed tx that is NOT first in its sublist, cumulative gas equals per-tx gas
        // everywhere and the suite cannot tell the two apart.
        assertEquals(3, block.getTransactionsList().indexOf(world.getTransactionByName("txType2Second")),
                "txType2Second must be the second transaction of the sequential sublist");
        assertNotEquals(storedReceipt("txType2Second").getCumulativeGasLong(), expectedGasUsed("txType2Second"),
                "txType2Second must be mid-sublist, so its cumulative gas differs from its per-tx gas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"txType1", "txType2", "txType4", "txType2Second"})
    void traceTransaction_typedTxAcrossSublists_reportsPerTxGas(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        JsonNode traces = traceModule.traceTransaction(tx.getHash().toJsonString());

        assertNotNull(traces);
        assertEquals(hex(expectedGasUsed(txName)), rootGasUsed(traces));
    }

    @ParameterizedTest
    @ValueSource(strings = {"txType1", "txType2", "txType4", "txType2Second"})
    void traceBlock_typedTxAcrossSublists_reportsPerTxGas(String txName) throws Exception {
        JsonNode traces = traceModule.traceBlock("0x02");

        assertNotNull(traces);
        assertEquals(hex(expectedGasUsed(txName)), rootGasUsedOf(traces, txName));
    }

    /**
     * trace_get selects by POSITION within the whole block's trace list, using the transaction
     * hash only to locate the block, so the position of the transaction's own root frame has to be
     * passed in. Asserting through that position is what makes this a real check of the gas value.
     */
    @ParameterizedTest
    @ValueSource(strings = {"txType1", "txType2", "txType4", "txType2Second"})
    void traceGet_typedTxAcrossSublists_reportsPerTxGas(String txName) throws Exception {
        Transaction tx = world.getTransactionByName(txName);
        int position = rootFramePosition(txName);

        JsonNode trace = traceModule.traceGet(tx.getHash().toJsonString(), List.of(hex(position)));

        assertNotNull(trace);
        assertEquals(tx.getHash().toJsonString(), trace.get("transactionHash").asText());
        assertEquals(hex(expectedGasUsed(txName)), trace.get("result").get("gasUsed").asText());
    }

    @Test
    void traceFilter_typedTxsAcrossSublists_reportPerTxGas() {
        TraceFilterRequest request = new TraceFilterRequest();
        request.setFromBlock("0x02");
        request.setToBlock("0x02");

        JsonNode traces = traceModule.traceFilter(request);

        assertNotNull(traces);
        for (String txName : TYPED_TX_NAMES) {
            assertEquals(hex(expectedGasUsed(txName)), rootGasUsedOf(traces, txName), txName);
        }
    }

    /**
     * Regression guard for the rejected approach: subtracting the previous block transaction's
     * cumulative gas. Cumulative gas restarts per RSKIP-144 sublist, so the delta is 0 at the
     * sublist 1 boundary and 10000 at the sequential boundary. txType2Second is deliberately not
     * listed: it sits mid-sublist, where the naive delta happens to be right.
     */
    @ParameterizedTest
    @CsvSource({"txType2, 0", "txType4, 10000"})
    void naiveBlockOrderDelta_wouldReportWrongGas(String txName, long naiveDelta) {
        assertEquals(naiveDelta, naiveBlockOrderDelta(txName),
                "fixture no longer reproduces the sublist-boundary delta");
        assertNotEquals(naiveDelta, expectedGasUsed(txName),
                txName + ": the naive delta must differ from the real per-tx gas");
    }

    @Test
    void traceTransaction_type1_rootFrameReportsRealGas() throws Exception {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0)));

        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());
        World simpleWorld = new World(store, config);
        new WorldDslProcessor(simpleWorld).processCommands(
                DslParser.fromResource("dsl/transaction/rskip546/rskip546Test.txt"));

        Transaction tx = simpleWorld.getTransactionByName("txType1Basic");

        assertEquals(1, simpleWorld.getBlockByName("b01").getTransactionsList().size(),
                "the oracle below only holds while b01 has a single transaction");

        // b01 holds txType1Basic alone, so its cumulative gas IS its per-tx gas.
        long expectedGas = store.getInMainChain(tx.getHash().getBytes(), simpleWorld.getBlockStore())
                .orElseThrow().getReceipt().getCumulativeGasLong();

        TraceModuleImpl module = new TraceModuleImpl(simpleWorld.getBlockChain(), simpleWorld.getBlockStore(),
                store, simpleWorld.getBlockExecutor(), null, simpleWorld.getBlockTxSignatureCache(),
                simpleWorld.getConfig());

        JsonNode traces = module.traceTransaction(tx.getHash().toJsonString());

        assertNotNull(traces);
        assertEquals(1, traces.size());
        assertEquals(hex(expectedGas), rootGasUsed(traces),
                "root trace gasUsed must be the transaction's real gas, not 0x0");
    }

    // ---- helpers ----

    private static org.ethereum.core.TransactionReceipt storedReceipt(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        return receiptStore.getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElseThrow().getReceipt();
    }

    /**
     * Independent oracle: the sublist-aware derivation {@code cumulative - prevCumulativeInSublist}
     * that {@code Web3Impl} uses for eth_getTransactionReceipt. Read from the stored receipts, so it
     * does not depend on the trace path under test.
     */
    private static long expectedGasUsed(String txName) {
        Block block = world.getBlockByName("b02");
        short[] edges = block.getHeader().getTxExecutionSublistsEdges();
        Transaction target = world.getTransactionByName(txName);
        int index = block.getTransactionsList().indexOf(target);

        long cumulative = storedReceipt(txName).getCumulativeGasLong();

        if (index == 0 || isSublistStart(index, edges)) {
            return cumulative;
        }

        Transaction previous = block.getTransactionsList().get(index - 1);
        TransactionInfo previousInfo = receiptStore
                .getInMainChain(previous.getHash().getBytes(), world.getBlockStore()).orElseThrow();
        return cumulative - previousInfo.getReceipt().getCumulativeGasLong();
    }

    private static long naiveBlockOrderDelta(String txName) {
        Block block = world.getBlockByName("b02");
        Transaction target = world.getTransactionByName(txName);
        int index = block.getTransactionsList().indexOf(target);

        long cumulative = storedReceipt(txName).getCumulativeGasLong();

        if (index == 0) {
            return cumulative;
        }

        Transaction previous = block.getTransactionsList().get(index - 1);
        TransactionInfo previousInfo = receiptStore
                .getInMainChain(previous.getHash().getBytes(), world.getBlockStore()).orElseThrow();
        return cumulative - previousInfo.getReceipt().getCumulativeGasLong();
    }

    private static boolean isSublistStart(int txIndex, short[] edges) {
        if (edges == null) {
            return false;
        }
        for (short edge : edges) {
            if (txIndex == edge) {
                return true;
            }
        }
        return false;
    }

    /** Index of the transaction's root frame inside the block-wide trace list trace_get indexes. */
    private static int rootFramePosition(String txName) throws Exception {
        String txHash = world.getTransactionByName(txName).getHash().toJsonString();
        JsonNode blockTraces = traceModule.traceBlock("0x02");

        for (int i = 0; i < blockTraces.size(); i++) {
            JsonNode trace = blockTraces.get(i);
            if (txHash.equals(trace.get("transactionHash").asText()) && trace.get("traceAddress").isEmpty()) {
                return i;
            }
        }

        throw new AssertionError("no root trace found for " + txName);
    }

    private static String rootGasUsed(JsonNode traces) {
        return traces.get(0).get("result").get("gasUsed").asText();
    }

    private static String rootGasUsedOf(JsonNode traces, String txName) {
        String txHash = world.getTransactionByName(txName).getHash().toJsonString();

        for (JsonNode trace : traces) {
            if (txHash.equals(trace.get("transactionHash").asText()) && trace.get("traceAddress").isEmpty()) {
                return trace.get("result").get("gasUsed").asText();
            }
        }

        throw new AssertionError("no root trace found for " + txName);
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
