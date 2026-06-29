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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DSL integration tests for the RSKIP-545 / EIP-7702 transaction-origin invariant:
 * self-sponsoring breaks the assumption that {@code msg.sender == tx.origin} only occurs in
 * the topmost execution frame.
 *
 * <p>Scenario file: {@code dsl/transaction/rskip545/rskip545OriginInvariantTest.txt}
 *
 * <p>See also {@link Rskip545OriginInvariantTest} (unit-level) and {@link Rskip545DslTest}.
 */
class Rskip545OriginInvariantDslTest {

    private static final long SINGLE_AUTH_INTRINSIC =
            GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST;
    private static final long DELEGATION_REPLACEMENT_REFUND =
            GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;
    private static final long RE_AUTH_BASELINE = SINGLE_AUTH_INTRINSIC - DELEGATION_REPLACEMENT_REFUND;

    /** Probe: slot 0 = {@code tx.origin}, slot 1 = {@code msg.sender}. */
    private static final byte[] ORIGIN_CALLER_PROBE =
            Hex.decode("326000553360015500");

    /** Guard marker: slot 2 = {@code (tx.origin == msg.sender) ? 1 : 0}. */
    private static final byte[] ORIGIN_SENDER_GUARD =
            Hex.decode("32231460025500");

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(0))
        );

        world = new World(config);
        seedBytecodeAccounts();

        DslParser parser = DslParser.fromResource(
                "dsl/transaction/rskip545/rskip545OriginInvariantTest.txt");
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    private static void seedBytecodeAccounts() {
        Coin funded = Coin.valueOf(10).multiply(BigInteger.valueOf(1_000_000_000_000_000_000L));
        Account originUser = new AccountBuilder(world).name("originUser").balance(funded).build();
        world.saveAccount("originUser", originUser);

        Account originProbe = new AccountBuilder(world).name("originProbe").balance(Coin.ZERO)
                .code(ORIGIN_CALLER_PROBE).build();
        world.saveAccount("originProbe", originProbe);

        Account originCallDelegate = new AccountBuilder(world).name("originCallDelegate").balance(Coin.ZERO)
                .code(callRuntime(originProbe.getAddress())).build();
        world.saveAccount("originCallDelegate", originCallDelegate);

        Account originGuard = new AccountBuilder(world).name("originGuard").balance(Coin.ZERO)
                .code(ORIGIN_SENDER_GUARD).build();
        world.saveAccount("originGuard", originGuard);

        Account originRelay = new AccountBuilder(world).name("originRelay").balance(Coin.ZERO)
                .code(callRuntime(originGuard.getAddress())).build();
        world.saveAccount("originRelay", originRelay);
    }

    // =========================================================================
    // Origin invariant behaviour
    // =========================================================================

    @Test
    void selfSponsoredTopFrame_originEqualsCallerInAuthorityStorage() {
        Account originUser = world.getAccountByName("originUser");
        RepositorySnapshot repo = repoAt("b01");

        DataWord storedOrigin = repo.getStorageValue(originUser.getAddress(), DataWord.ZERO);
        DataWord storedCaller = repo.getStorageValue(originUser.getAddress(), DataWord.ONE);
        DataWord expected = DataWord.valueOf(originUser.getAddress().getBytes());

        assertEquals(expected, storedOrigin,
                "Delegated top frame must preserve tx.origin in authority storage");
        assertEquals(expected, storedCaller,
                "Delegated top frame must set msg.sender to the authority");
        assertEquals(storedOrigin, storedCaller,
                "msg.sender == tx.origin while executing delegated bytecode in the top frame");
    }

    @Test
    void nestedCall_originEqualsCallerInProbeStorage() {
        Account originUser = world.getAccountByName("originUser");
        RskAddress probe = world.getAccountByName("originProbe").getAddress();
        RepositorySnapshot repo = repoAt("b02");

        DataWord storedOrigin = repo.getStorageValue(probe, DataWord.ZERO);
        DataWord storedCaller = repo.getStorageValue(probe, DataWord.ONE);
        DataWord expected = DataWord.valueOf(originUser.getAddress().getBytes());

        assertEquals(expected, storedOrigin,
                "Nested CALL must preserve tx.origin");
        assertEquals(expected, storedCaller,
                "Nested CALL from delegated EOA must set msg.sender to the authority");
        assertEquals(storedOrigin, storedCaller,
                "msg.sender == tx.origin in nested frames when delegated EOA issues CALL");
    }

    @Test
    void contractRelay_guardDoesNotArmWhenCallerDiffersFromOrigin() {
        RskAddress guard = world.getAccountByName("originGuard").getAddress();
        DataWord guardSlot = repoAt("b03").getStorageValue(guard, DataWord.valueOf(2));

        assertTrue(guardSlot == null || guardSlot.isZero(),
                "Guard must not record success when relay contract is msg.sender but EOA is tx.origin");
        assertNotEquals(DataWord.ONE, guardSlot);
    }

    @Test
    void selfSponsoredTopFrame_writesDelegationIndicator() {
        Account originUser = world.getAccountByName("originUser");
        byte[] code = repoAt("b01").getCode(originUser.getAddress());

        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(world.getAccountByName("originProbe").getAddress(),
                DelegationCodeResolver.extractDelegatedAddress(code));
    }

    // =========================================================================
    // Gas consumption
    // =========================================================================

    @Test
    void type4_originTopFrame_intrinsicGas_matchesRskip545Schedule() {
        assertEquals(SINGLE_AUTH_INTRINSIC, intrinsicGas("txOriginTopFrame"),
                "Intrinsic = TRANSACTION + PER_EMPTY_ACCOUNT_COST");
    }

    @Test
    void type4_originTopFrame_gasUsed_exceedsIntrinsicForDelegatedExecution() {
        long used = gasUsedInBlock("b01", "txOriginTopFrame");
        assertTrue(used > SINGLE_AUTH_INTRINSIC,
                "Delegated probe execution must consume gas beyond auth intrinsic cost");
    }

    @Test
    void type4_originNestedCall_gasUsed_exceedsReAuthBaseline() {
        long nested = gasUsedInBlock("b02", "txOriginNestedCall");
        assertTrue(nested > RE_AUTH_BASELINE,
                "Nested CALL execution must add gas beyond the re-auth baseline "
                        + "(SINGLE_AUTH_INTRINSIC - DELEGATION_REPLACEMENT_REFUND)");
    }

    @Test
    void type4_originNestedCall_gasUsed_lowerThanFirstSelfAuthDueToReplacementRefund() {
        long topFrame = gasUsedInBlock("b01", "txOriginTopFrame");
        long nested = gasUsedInBlock("b02", "txOriginNestedCall");
        assertTrue(nested < topFrame,
                "Second self-auth replaces delegation and refunds PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST");
    }

    @Test
    void type4_originNestedCall_intrinsicGas_matchesSingleAuthSchedule() {
        assertEquals(SINGLE_AUTH_INTRINSIC, intrinsicGas("txOriginNestedCall"));
    }

    @Test
    void legacy_originRelay_gasUsed_exceedsBaseTransaction() {
        long used = gasUsedInBlock("b03", "txOriginRelay");
        assertTrue(used > GasCost.TRANSACTION,
                "Relay contract must execute CALL bytecode beyond the legacy 21000 base");
        assertTrue(used <= 200_000L, "Relay tx gas used must not exceed declared gas limit");
    }

    @Test
    void originInvariant_type4Receipts_succeed() {
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txOriginTopFrame").getStatus());
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txOriginNestedCall").getStatus());
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txOriginRelay").getStatus());
    }

    @Test
    void originInvariant_type4Transactions_areType4() {
        assertEquals(TransactionType.TYPE_4, world.getTransactionByName("txOriginTopFrame").getType());
        assertEquals(TransactionType.TYPE_4, world.getTransactionByName("txOriginNestedCall").getType());
        assertEquals(TransactionType.LEGACY, world.getTransactionByName("txOriginRelay").getType());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static byte[] callRuntime(RskAddress target) {
        return Hex.decode(
                "6000600060006000600073"
                        + Hex.toHexString(target.getBytes())
                        + "5af100"
        );
    }

    private static RepositorySnapshot repoAt(String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
    }

    private static long intrinsicGas(String txName) {
        Transaction tx = world.getTransactionByName(txName);
        return tx.transactionCost(
                world.getConfig().getNetworkConstants(),
                world.getConfig().getActivationConfig().forBlock(1),
                world.getBlockTxSignatureCache());
    }

    private static long gasUsedInBlock(String blockName, String txName) {
        Block block = world.getBlockByName(blockName);
        Transaction target = world.getTransactionByName(txName);
        long prevCumulative = 0;
        for (Transaction tx : block.getTransactionsList()) {
            TransactionInfo info = world.getReceiptStore()
                    .get(tx.getHash().getBytes(), block.getHash().getBytes())
                    .orElseThrow(() -> new IllegalStateException("Missing receipt for " + tx.getHash()));
            long cumulative = info.getReceipt().getCumulativeGasLong();
            if (tx.getHash().equals(target.getHash())) {
                return cumulative - prevCumulative;
            }
            prevCumulative = cumulative;
        }
        throw new IllegalStateException("Transaction " + txName + " not found in block " + blockName);
    }
}
