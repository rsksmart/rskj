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

package co.rsk.test;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.FileNotFoundException;
import java.math.BigInteger;

import static co.rsk.db.ClearedAccountAssertions.assertClearedAccount;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP701 multi-block cases (7 to 10) run through the DSL, with and without the activation.
 * The addresses are fixed by the DSL account names and the factory's CREATE2 salt; see the fixtures.
 */
class SelfdestructPreservesNonceDslTest {

    private static final RskAddress CONTRACT_C = new RskAddress("564277e6a03a179c7b29165fd3fa2612dd9b6602");
    private static final byte[] CODE_D = Hex.decode("602a600055600035ff");
    private static final long CALL_GAS_LIMIT = 200_000;

    @Test
    void create2ContractIsClearedAndCannotBeRecreated() throws FileNotFoundException, DslProcessorException {
        World world = run("dsl/rskip701/create2_destroy_recreate.txt", true);

        RepositorySnapshot afterDestroy = snapshotAt(world, "b02");
        assertClearedAccount(afterDestroy, CONTRACT_C, BigInteger.ONE);

        // Case 8: a call to the cleared account does not pay the cost of an account that does not exist
        assertEquals(GasCost.NEW_ACCT_CALL, gasUsed(world, "callZ") - gasUsed(world, "callC"));

        // Case 7: the repeated CREATE2 pushes zero and consumes its gas, so the factory runs out of gas
        assertRefusedCreation(world, "recreateC");
        assertClearedAccount(snapshotAtBest(world), CONTRACT_C, BigInteger.ONE);
    }

    @Test
    void beforeActivationCreate2ContractIsDeletedAndRecreated() throws FileNotFoundException, DslProcessorException {
        World world = run("dsl/rskip701/create2_destroy_recreate.txt", false);

        RepositorySnapshot afterDestroy = snapshotAt(world, "b02");
        assertFalse(afterDestroy.isExist(CONTRACT_C));

        assertEquals(gasUsed(world, "callZ"), gasUsed(world, "callC"));

        assertSuccessfulCreation(world, "recreateC");
        assertFreshContract(snapshotAtBest(world), CONTRACT_C);
    }

    @ParameterizedTest(name = "rskip701 active: {0}")
    @ValueSource(booleans = {true, false})
    void create2ContractDestroyedInItsTransactionIsDeletedAndRecreatedNextBlock(boolean active)
            throws FileNotFoundException, DslProcessorException {
        World world = run("dsl/rskip701/create2_destroy_same_transaction.txt", active);

        RepositorySnapshot afterFirstBlock = snapshotAt(world, "b01");
        assertFalse(afterFirstBlock.isExist(CONTRACT_C));
        assertRefusedCreation(world, "recreateSameBlock");

        assertSuccessfulCreation(world, "recreateNextBlock");
        assertFreshContract(snapshotAtBest(world), CONTRACT_C);
    }

    @ParameterizedTest(name = "rskip701 active: {0}")
    @ValueSource(booleans = {true, false})
    void contractDeployedByCreationTransactionIsDeleted(boolean active)
            throws FileNotFoundException, DslProcessorException {
        World world = run("dsl/rskip701/creation_transaction_destroy.txt", active);

        RskAddress contract = world.getTransactionByName("deployC").getContractAddress();
        assertFalse(snapshotAtBest(world).isExist(contract));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static World run(String resource, boolean rskip701Active) throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig.withValue(
                "blockchain.config.consensusRules.rskip701", ConfigValueFactory.fromAnyRef(rskip701Active ? 0 : -1)));
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(DslParser.fromResource(resource));
        return world;
    }

    private static RepositorySnapshot snapshotAt(World world, String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
    }

    private static RepositorySnapshot snapshotAtBest(World world) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockChain().getBestBlock().getHeader());
    }

    /** The CREATE2 is refused: it pushes zero and consumes all the gas it was given, so the factory halts. */
    private static void assertRefusedCreation(World world, String transactionName) {
        assertFalse(world.getTransactionReceiptByName(transactionName).isSuccessful());
        assertEquals(CALL_GAS_LIMIT, gasUsed(world, transactionName));
    }

    private static void assertSuccessfulCreation(World world, String transactionName) {
        assertTrue(world.getTransactionReceiptByName(transactionName).isSuccessful());
        assertTrue(gasUsed(world, transactionName) < CALL_GAS_LIMIT);
    }

    private static long gasUsed(World world, String transactionName) {
        return BigIntegers.fromUnsignedByteArray(world.getTransactionReceiptByName(transactionName).getGasUsed()).longValue();
    }

    private static void assertFreshContract(RepositorySnapshot repository, RskAddress address) {
        assertTrue(repository.isExist(address));
        assertEquals(BigInteger.ONE, repository.getNonce(address));
        assertArrayEquals(CODE_D, repository.getCode(address));
        assertNull(repository.getStorageValue(address, DataWord.ZERO));
    }
}
