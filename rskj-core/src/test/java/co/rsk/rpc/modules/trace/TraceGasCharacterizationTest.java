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

import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the parity-trace gas figures for legacy transactions, whose stored receipt and fresh
 * re-execution agree, so a change to the root-frame gas source cannot move them silently.
 */
class TraceGasCharacterizationTest {

    private static World contracts08;
    private static TraceModuleImpl contracts08Module;

    @BeforeAll
    static void setup() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        contracts08 = world("dsl/contracts08.txt", receiptStore);
        contracts08Module = traceModule(contracts08, receiptStore);
    }

    private static World world(String fixture, ReceiptStore receiptStore) throws Exception {
        World world = new World(receiptStore);
        new WorldDslProcessor(world).processCommands(DslParser.fromResource(fixture));
        return world;
    }

    private static TraceModuleImpl traceModule(World world, ReceiptStore receiptStore) {
        return new TraceModuleImpl(world.getBlockChain(), world.getBlockStore(), receiptStore,
                world.getBlockExecutor(), null, world.getBlockTxSignatureCache(), world.getConfig());
    }

    private static JsonNode traceOf(World world, TraceModuleImpl module, String txName) {
        Transaction tx = world.getTransactionByName(txName);
        JsonNode result = module.traceTransaction(tx.getHash().toJsonString());
        assertNotNull(result);
        assertTrue(result.isArray());
        return result;
    }

    private static void assertFrame(JsonNode frame, String traceAddress, String actionGas, String gasUsed) {
        assertEquals(traceAddress, frame.get("traceAddress").toString());
        assertEquals(actionGas, frame.get("action").get("gas").asText());
        assertEquals(gasUsed, frame.get("result").get("gasUsed").asText());
        assertEquals(0, frame.get("transactionPosition").asInt());
    }

    /** Legacy contract creation with nested creations: root frame 0xbda2f, subtraces unchanged. */
    @Test
    void legacyNestedCreation_gasPerFrame() {
        JsonNode traces = traceOf(contracts08, contracts08Module, "tx01");

        assertEquals(4, traces.size());
        assertFrame(traces.get(0), "[]", "0x124f80", "0xbda2f");
        assertFrame(traces.get(1), "[0]", "0x1059ca", "0x3e242");
        assertFrame(traces.get(2), "[0,0]", "0xfdbf5", "0x1111d");
        assertFrame(traces.get(3), "[1]", "0xbaac0", "0x1111d");
    }

    /** Legacy nested CALL: root frame 0x107c7, subtraces unchanged by any receipt-sourcing change. */
    @Test
    void legacyNestedCall_gasPerFrame() {
        JsonNode traces = traceOf(contracts08, contracts08Module, "tx02");

        assertEquals(4, traces.size());
        assertFrame(traces.get(0), "[]", "0xf4240", "0x107c7");
        assertFrame(traces.get(1), "[0]", "0xee868", "0x5734");
        assertFrame(traces.get(2), "[0,0]", "0xee104", "0x4fa8");
        assertFrame(traces.get(3), "[1]", "0xe8a49", "0x4fa8");
    }

    /** Reverted transaction: every frame emits error with a null result, so no gasUsed at all. */
    @Test
    void legacyRevertedCall_hasErrorAndNoResult() {
        JsonNode traces = traceOf(contracts08, contracts08Module, "tx03");

        assertEquals(3, traces.size());
        for (JsonNode frame : traces) {
            assertTrue(frame.get("result").isNull(), "reverted frame must carry a null result");
            assertEquals("Reverted", frame.get("error").asText());
        }
    }

    /** SUICIDE subtrace keeps a null result; the root frame keeps its own gas. */
    @Test
    void legacySuicide_rootGasAndNullSuicideResult() {
        JsonNode traces = traceOf(contracts08, contracts08Module, "tx04");

        assertEquals(2, traces.size());
        assertFrame(traces.get(0), "[]", "0xf4240", "0x33c9");
        assertEquals("suicide", traces.get(1).get("type").asText());
        assertTrue(traces.get(1).get("result").isNull());
    }

    /** trace_block must return the same frames, in the same order, as trace_transaction. */
    @Test
    void legacyTraceBlock_matchesTraceTransaction() throws Exception {
        assertEquals(traceOf(contracts08, contracts08Module, "tx02").toString(),
                contracts08Module.traceBlock("0x02").toString());
        assertEquals(traceOf(contracts08, contracts08Module, "tx03").toString(),
                contracts08Module.traceBlock("0x03").toString());
    }

    /** A sub-call frame's gas is sourced from its ProgramSubtrace, never from the receipt. */
    @Test
    void legacySubcallGas_isIndependentOfReceipt() throws Exception {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = world("dsl/trace_subcall_calldata.txt", receiptStore);
        JsonNode traces = traceOf(world, traceModule(world, receiptStore), "tx02");

        assertEquals(2, traces.size());
        assertFrame(traces.get(0), "[]", "0x124f80", "0x552b");
        assertFrame(traces.get(1), "[0]", "0x1000", "0x6");
    }

    /** Genesis and out-of-range blocks stay empty / null. */
    @Test
    void genesisAndUnknownBlocks_unchanged() throws Exception {
        assertEquals(0, contracts08Module.traceBlock("earliest").size());
        assertNull(contracts08Module.traceBlock("0x99"));
    }
}
