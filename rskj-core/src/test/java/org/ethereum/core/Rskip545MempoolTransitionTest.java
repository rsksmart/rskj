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
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regtest-constructed mempool transition across the RSKIP-545 boundary.
 */
class Rskip545MempoolTransitionTest {

    private static final int ACTIVATION = 5;
    private static final String BASE =
            "dsl/transaction/rskip545/rskip545MempoolTransition.txt";
    private static final String TO_H1 =
            "dsl/transaction/rskip545/rskip545MempoolTransitionToH1.txt";
    private static final String TO_H =
            "dsl/transaction/rskip545/rskip545MempoolTransitionToH.txt";
    private static final String MINE_ATTEMPT =
            "dsl/transaction/rskip545/rskip545MempoolTransitionMineAttempt.txt";

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

    private static TransactionPoolAddResult submitType4(World world) {
        return world.getTransactionPool().addTransaction(world.getTransactionByName("txMempoolType4"));
    }

    @Test
    void tipBelowActivation_poolRejectsType4() throws Exception {
        World world = worldWith(BASE);
        assertEquals(3, world.getBlockChain().getBestBlock().getNumber());

        TransactionPoolAddResult result = submitType4(world);

        assertFalse(result.transactionsWereAdded(),
                "Type 4 must not be admitted while the next block to mine is below H");
        assertTrue(result.getErrorMessage().contains("not supported before its activation"),
                "unexpected rejection reason: " + result.getErrorMessage());
    }

    @Test
    void tipBelowActivation_poolStillAcceptsLegacy() throws Exception {
        World world = worldWith(BASE);

        TransactionPoolAddResult result = world.getTransactionPool()
                .addTransaction(world.getTransactionByName("txMempoolLegacy"));

        assertTrue(result.transactionsWereAdded(),
                "control: the pool must still admit legacy traffic below H");
    }

    @Test
    void tipAtActivationMinusOne_poolRejectsType4() throws Exception {
        World world = worldWith(BASE, TO_H1);
        assertEquals(ACTIVATION - 1, world.getBlockChain().getBestBlock().getNumber());

        TransactionPoolAddResult result = submitType4(world);

        assertFalse(result.transactionsWereAdded());
        assertTrue(result.getErrorMessage().contains("not supported before its activation"),
                "unexpected rejection reason: " + result.getErrorMessage());
    }

    @Test
    void tipAtActivation_poolAcceptsType4() throws Exception {
        World world = worldWith(BASE, TO_H1, TO_H);
        assertEquals(ACTIVATION, world.getBlockChain().getBestBlock().getNumber());

        TransactionPoolAddResult result = submitType4(world);

        assertTrue(result.transactionsWereAdded(),
                "Type 4 must be admitted once the tip reaches H: " + result.getErrorMessage());
        assertTrue(world.getTransactionPool().getPendingTransactions().stream()
                        .anyMatch(tx -> tx.getHash().equals(
                                world.getTransactionByName("txMempoolType4").getHash())),
                "admitted Type 4 must be visible to the miner");
    }

    @Test
    void type4_isNotMinableIntoPreActivationBlock() throws Exception {
        World world = worldWith(BASE, MINE_ATTEMPT);

        Block block = world.getBlockByName("bPreWithType4");
        assertEquals(ACTIVATION - 1, block.getNumber());
        assertTrue(block.getTransactionsList().isEmpty(),
                "Type 4 must be dropped from a block below H even if it reaches the block builder");
    }
}
