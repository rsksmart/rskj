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
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regtest-constructed reorg across the RSKIP-545 activation boundary.
 */
class Rskip545ReorgAcrossActivationTest {

    private static final int ACTIVATION = 5;
    private static final byte[] DELEGATION_TO_03 = delegationCode((byte) 0x03);

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
        );

        DslParser parser = DslParser.fromResource(
                "dsl/transaction/rskip545/rskip545ReorgAcrossActivation.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    private static byte[] delegationCode(byte suffix) {
        byte[] code = new byte[23];
        System.arraycopy(DelegationCodeResolver.DELEGATION_PREFIX, 0, code, 0, 3);
        code[22] = suffix;
        return code;
    }

    private static RskAddress acc1() {
        return world.getAccountByName("acc1").getAddress();
    }

    private static RepositorySnapshot stateAt(String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
    }

    @Test
    void abandonedBranch_hadDelegationCodeAtTip() {
        assertArrayEquals(DELEGATION_TO_03, stateAt("b07").getCode(acc1()),
                "Type 4 state must actually have existed on the branch that is later abandoned");
    }

    @Test
    void afterReorg_bestBlockIsRebuiltBranch() {
        assertEquals(world.getBlockByName("c08").getHash(),
                world.getBlockChain().getBestBlock().getHash());
        assertEquals(8, world.getBlockChain().getBestBlock().getNumber());
    }

    @Test
    void afterReorg_delegationCodeRolledBack() {
        byte[] code = stateAt("c08").getCode(acc1());
        assertTrue(code == null || code.length == 0,
                "Delegation designator installed at H must not survive a reorg to a branch that never had it");
    }

    @Test
    void rebuiltBranch_blockBelowActivation_dropsType4() {
        Block c04 = world.getBlockByName("c04");
        assertEquals(4, c04.getNumber());
        assertTrue(c04.getTransactionsList().isEmpty(),
                "Type 4 in a block below H must be dropped even though the tip was at H+2 when it was built");
    }

    @Test
    void rebuiltBranch_blockBelowActivation_installsNoDelegation() {
        byte[] code = stateAt("c04").getCode(acc1());
        assertTrue(code == null || code.length == 0,
                "A dropped Type 4 must leave no delegation designator behind");
    }

    @Test
    void afterReorg_abandonedType4HasNoMainChainReceipt() {
        assertNull(world.getTransactionReceiptByName("txMainType4"),
                "Receipt of the reorged-out Type 4 must no longer be in the main chain");
    }

    @Test
    void afterReorg_rebuiltBranchTransactionExecuted() {
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txRebuiltLegacy").getStatus());
    }

    @Test
    void afterReorg_senderNonceMatchesRebuiltBranchOnly() {
        assertEquals(java.math.BigInteger.ZERO, stateAt("c08").getNonce(acc1()),
                "Sender nonce must reflect only the rebuilt branch, which has no acc1 transaction");
    }
}
