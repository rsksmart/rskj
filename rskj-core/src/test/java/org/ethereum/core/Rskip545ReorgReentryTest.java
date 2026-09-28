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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regtest-constructed mempool re-entry after a reorg across the RSKIP-545 boundary.
 */
class Rskip545ReorgReentryTest {

    private static final int ACTIVATION = 5;
    private static final String BASE = "dsl/transaction/rskip545/rskip545ReorgReentry.txt";
    private static final String FORK = "dsl/transaction/rskip545/rskip545ReorgReentryFork.txt";

    private static TestSystemProperties config() {
        return new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
        );
    }

    private static World worldWith(String... fixtures) throws FileNotFoundException, DslProcessorException {
        World world = new World(config());
        WorldDslProcessor processor = new WorldDslProcessor(world);
        for (String fixture : fixtures) {
            processor.processCommands(DslParser.fromResource(fixture));
        }
        return world;
    }

    private static List<Keccak256> poolHashes(World world) {
        List<Transaction> all = new ArrayList<>(world.getTransactionPool().getPendingTransactions());
        all.addAll(world.getTransactionPool().getQueuedTransactions());
        return all.stream().map(Transaction::getHash).toList();
    }

    private static Keccak256 hashOf(World world, String txName) {
        return world.getTransactionByName(txName).getHash();
    }

    @Test
    void beforeReorg_type4IsMinedAtActivationHeight() throws Exception {
        World world = worldWith(BASE);

        Block b05 = world.getBlockByName("b05");
        assertEquals(ACTIVATION, b05.getNumber());
        assertTrue(b05.getTransactionsList().stream()
                        .anyMatch(tx -> tx.getHash().equals(hashOf(world, "txReentryType4"))),
                "the Type 4 transaction must really have been mined at H before the reorg");
    }

    @Test
    void afterReorg_tipMovesBackBelowActivation() throws Exception {
        World world = worldWith(BASE, FORK);

        Block best = world.getBlockChain().getBestBlock();
        assertEquals(world.getBlockByName("c04").getHash(), best.getHash());
        assertEquals(ACTIVATION - 1, best.getNumber(),
                "the reorg must leave the tip below the activation height");
    }

    @Test
    void afterReorg_retractedLegacyTransactionReentersPool() throws Exception {
        World world = worldWith(BASE, FORK);

        assertTrue(poolHashes(world).contains(hashOf(world, "txReentryLegacy")),
                "control: retraction must really have fed the reorged-out block back into the pool");
    }

    @Test
    void afterReorg_retractedType4IsNotReadmitted() throws Exception {
        World world = worldWith(BASE, FORK);

        assertFalse(poolHashes(world).contains(hashOf(world, "txReentryType4")),
                "a Type 4 re-entering the pool must be re-validated against the new target height, not the old one");
    }

    @Test
    void afterReorg_resubmittedType4IsRejectedWithActivationError() throws Exception {
        World world = worldWith(BASE, FORK);

        TransactionPoolAddResult result = world.getTransactionPool()
                .addTransaction(world.getTransactionByName("txReentryType4"));

        assertFalse(result.transactionsWereAdded());
        assertTrue(result.getErrorMessage().contains("not supported before its activation"),
                "unexpected rejection reason: " + result.getErrorMessage());
    }
}
