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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.db.MutableRepository;
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
    private static final String SAME_BLOCK_FIXTURE = "dsl/rskip701/create2_destroy_same_block.txt";
    private static final String SPREAD_FIXTURE = "dsl/rskip701/create2_destroy_same_block_spread.txt";
    private static final RskAddress CONTRACT_K = new RskAddress("86c6da12d2c60244ddcb72193659bafe0a54de2f");
    private static final String PROBE_P = "3b7333f16708e73a68ef9171cf7a6ebbd78c954f";
    private static final long LATE_VALUE = 1000;
    private static final long PLAIN_CALL_GAS = 21_000;
    private static final String SAME_BLOCK_STATE_ROOT = "aaf71281a88c23c9f58b7ae297a59ef5e0e2d428d3182d42302fe22b8a634f4c";

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
        long best = world.getBlockChain().getBestBlock().getNumber();
        assertFalse(world.getConfig().getActivationConfig().isActive(ConsensusRule.RSKIP545, best));
        assertFalse(world.getConfig().getActivationConfig().isActive(ConsensusRule.RSKIP701, best));

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

    /**
     * K is created, destroyed (and written to after its mark), probed, called, paid and re-created, all in block b01.
     * Every step after the destroy sees the cleared account through the block's stacked caches. b02 tries the
     * re-create again.
     */
    @Test
    void create2ContractClearedInTheBlockItWasCreatedIsSeenAsClearedByTheLaterTransactionsOfThatBlock()
            throws FileNotFoundException, DslProcessorException {
        World world = run(SAME_BLOCK_FIXTURE, true);

        RepositorySnapshot afterBlock = snapshotAt(world, "b01");
        assertEquals(BigInteger.ONE, afterBlock.getNonce(CONTRACT_K));
        assertEquals(0, afterBlock.getCodeLength(CONTRACT_K));
        assertEquals(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY, afterBlock.getCodeHashStandard(CONTRACT_K));
        assertEquals(0, afterBlock.getStorageKeysCount(CONTRACT_K), "slot 7, written after the mark, is cleared too");
        assertNull(afterBlock.getStorageValue(CONTRACT_K, DataWord.ZERO));
        assertNull(afterBlock.getStorageValue(CONTRACT_K, DataWord.valueOf(7)));
        assertEquals(Coin.valueOf(LATE_VALUE), afterBlock.getBalance(CONTRACT_K), "the value paid after the clear stays");
        assertEquals(PLAIN_CALL_GAS, gasUsed(world, "callK"), "K has no code, so the call runs nothing");
        assertProbe(afterBlock, MutableRepository.KECCAK_256_OF_EMPTY_ARRAY.getBytes());
        assertRefusedCreation(world, "recreateK");
        assertRefusedCreation(world, "recreateKLater");
        assertEquals(BigInteger.ONE, snapshotAtBest(world).getNonce(CONTRACT_K));
        assertEquals(0, snapshotAtBest(world).getCodeLength(CONTRACT_K));
        assertEquals(SAME_BLOCK_STATE_ROOT, Hex.toHexString(world.getBlockByName("b01").getStateRoot()),
                "golden state root of b01: pins the trie shape of the cleared account");
    }

    @Test
    void beforeActivationCreate2ContractDestroyedInTheBlockItWasCreatedIsDeletedAndCanBeRecreatedNextBlock()
            throws FileNotFoundException, DslProcessorException {
        World world = run(SAME_BLOCK_FIXTURE, false);

        RepositorySnapshot afterBlock = snapshotAt(world, "b01");
        assertEquals(BigInteger.ZERO, afterBlock.getNonce(CONTRACT_K), "deleted, then re-created as an empty account by the plain call");
        assertEquals(0, afterBlock.getCodeLength(CONTRACT_K));
        assertEquals(0, afterBlock.getStorageKeysCount(CONTRACT_K));
        assertEquals(Coin.valueOf(LATE_VALUE), afterBlock.getBalance(CONTRACT_K));
        assertEquals(PLAIN_CALL_GAS, gasUsed(world, "callK"));
        // The probe runs before the plain call re-creates K (no EIP-161 on RSK), so it sees no account
        assertProbe(afterBlock, null);
        // RSKIP131: K was marked in this block, so a CREATE2 at K in a later transaction of the same block is refused
        assertRefusedCreation(world, "recreateK");
        assertTrue(world.getTransactionReceiptByName("recreateKLater").isSuccessful());
        assertEquals(BigInteger.ONE, snapshotAtBest(world).getNonce(CONTRACT_K));
        assertTrue(snapshotAtBest(world).getCodeLength(CONTRACT_K) > 0);
    }

    /**
     * The same transactions spread over three blocks leave the accounts they touch in the same state as one block.
     * The state roots differ only because of the extra blocks (rewards), so the accounts are compared one by one.
     */
    @Test
    void sameBlockClearLeavesTheSameAccountsAsTheSameTransactionsSpreadOverThreeBlocks()
            throws FileNotFoundException, DslProcessorException {
        World sameBlock = run(SAME_BLOCK_FIXTURE, true);
        World spread = run(SPREAD_FIXTURE, true);
        RepositorySnapshot one = snapshotAt(sameBlock, "b01");
        RepositorySnapshot three = snapshotAt(spread, "b03");

        for (String name : new String[]{"factoryK", "driverX", "probeP", "accB"}) {
            assertSameAccount(one, three, sameBlock.getAccountByName(name).getAddress(), name);
        }
        assertSameAccount(one, three, CONTRACT_K, "K");
        assertEquals(gasUsed(sameBlock, "destroyThenStoreK"), gasUsed(spread, "destroyThenStoreK"));
        assertEquals(gasUsed(sameBlock, "probeK"), gasUsed(spread, "probeK"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static World run(String resource, boolean rskip701Active) throws FileNotFoundException, DslProcessorException {
        long activationHeight = rskip701Active ? 0 : -1;
        // RSKIP545 requires RSKIP701, so it follows the RSKIP701 activation
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip701", ConfigValueFactory.fromAnyRef(activationHeight))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(activationHeight)));
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

    /** The probe stored EXTCODEHASH(K) in slot 0, EXTCODESIZE(K) + 1 in slot 1 and BALANCE(K) + 1 in slot 2. */
    private static void assertProbe(RepositorySnapshot repository, byte[] expectedCodeHash) {
        RskAddress probe = new RskAddress(PROBE_P);
        if (expectedCodeHash == null) {
            assertNull(repository.getStorageValue(probe, DataWord.ZERO));
        } else {
            assertEquals(DataWord.valueOf(expectedCodeHash), repository.getStorageValue(probe, DataWord.ZERO));
        }
        assertEquals(DataWord.ONE, repository.getStorageValue(probe, DataWord.ONE), "EXTCODESIZE is 0");
        assertEquals(DataWord.ONE, repository.getStorageValue(probe, DataWord.valueOf(2)), "BALANCE is 0 before the payment");
    }

    private static void assertSameAccount(RepositorySnapshot expected, RepositorySnapshot actual, RskAddress address, String name) {
        assertEquals(expected.isExist(address), actual.isExist(address), name);
        assertEquals(expected.getNonce(address), actual.getNonce(address), name);
        assertEquals(expected.getBalance(address), actual.getBalance(address), name);
        assertEquals(expected.getCodeHashStandard(address), actual.getCodeHashStandard(address), name);
        assertEquals(expected.getStorageKeysCount(address), actual.getStorageKeysCount(address), name);
        for (int slot = 0; slot < 8; slot++) {
            assertEquals(expected.getStorageValue(address, DataWord.valueOf(slot)),
                    actual.getStorageValue(address, DataWord.valueOf(slot)), name + " slot " + slot);
        }
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
