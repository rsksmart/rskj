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
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.utils.RejectedPegoutReason;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP-545 interaction matrix cells that need a real chain: Bridge / PowPeg,
 * REMASC fee accounting and the self-sponsored authorization nonce.
 */
class Rskip545SubsystemInteractionDslTest {

    private static final byte[] PEGOUT_REJECTED_TOPIC =
            BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent().encodeSignatureLong();
    private static final byte[] PEGOUT_RECEIVED_TOPIC =
            BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent().encodeSignatureLong();

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

        DslParser parser = DslParser.fromResource(
                "dsl/transaction/rskip545/rskip545SubsystemInteraction.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    // =========================================================================
    // Bridge / PowPeg: peg-out from a delegated account
    // =========================================================================

    @Test
    void pegoutSender_carriesDelegationDesignatorWhenTheBridgeRuns() {
        byte[] code = stateAt("b01").getCode(address("acc1"));
        assertTrue(DelegationCodeResolver.isDelegatedCode(code),
                "the authorization list is applied before the call, so the Bridge sees a delegated sender");
        assertEquals(address("markerImpl"), DelegationCodeResolver.extractDelegatedAddress(code));
    }

    @Test
    void pegoutFromDelegatedSender_isAccepted() {
        byte[] topic = topic0(onlyBridgeLog("txPegoutFromDelegatedSender"));
        assertFalse(java.util.Arrays.equals(PEGOUT_REJECTED_TOPIC, topic),
                "BridgeUtils.isContractTx keys off the transaction class, not off sender code: "
                        + "a delegation designator must not turn a peg-out into a rejected request");
        assertArrayEquals(PEGOUT_RECEIVED_TOPIC, topic,
                "the peg-out request from a delegated EOA must be received by the Bridge");
    }

    @Test
    void pegoutFromDelegatedSender_emitsTheSameEventAsAPlainEoaPegout() {
        assertArrayEquals(topic0(onlyBridgeLog("txPegoutFromPlainSender")),
                topic0(onlyBridgeLog("txPegoutFromDelegatedSender")),
                "a delegated EOA and a plain EOA must reach the same peg-out outcome");
    }

    @Test
    void pegoutFromContract_isRejectedAsAContractCall() {
        LogInfo log = onlyBridgeLog("txPegoutFromContract");
        assertArrayEquals(PEGOUT_REJECTED_TOPIC, topic0(log));
        assertEquals(RejectedPegoutReason.CALLER_CONTRACT.getValue(), rejectionReasonOrZero(log),
                "control: the contract path is the one the Bridge rejects");
    }

    @Test
    void pegoutFromContract_emitsADifferentEventThanTheDelegatedSender() {
        assertFalse(java.util.Arrays.equals(
                        topic0(onlyBridgeLog("txPegoutFromContract")),
                        topic0(onlyBridgeLog("txPegoutFromDelegatedSender"))),
                "both sides measured: the contract peg-out and the delegated-EOA peg-out diverge");
    }

    // =========================================================================
    // Bridge / PowPeg: a Bridge-initiated credit must not execute delegate code
    // =========================================================================

    @Test
    void bridgeRefundToDelegatedAccount_isRejectedForAmountNotForSenderCode() {
        LogInfo log = onlyBridgeLog("txPegoutBelowMinimumFromDelegated");
        assertArrayEquals(PEGOUT_REJECTED_TOPIC, topic0(log));
        assertEquals(RejectedPegoutReason.LOW_AMOUNT.getValue(), rejectionReasonOrZero(log),
                "a below-minimum peg-out from a delegated EOA is rejected on amount, not as a contract");
    }

    @Test
    void bridgeRefundToDelegatedAccount_returnsTheValueWithoutRunningTheDelegateCode() {
        RskAddress acc4 = address("acc4");
        assertTrue(DelegationCodeResolver.isDelegatedCode(stateAt("b04").getCode(acc4)),
                "fixture precondition: the refunded account is delegated");

        Transaction tx = world.getTransactionByName("txPegoutBelowMinimumFromDelegated");
        Coin fee = tx.getGasPrice().multiply(BigInteger.valueOf(gasUsedInBlock("b04", tx)));
        assertEquals(stateAt("b03").getBalance(acc4).subtract(fee), stateAt("b04").getBalance(acc4),
                "the rejected peg-out value must come back: only gas is spent");

        DataWord marker = stateAt("b04").getStorageValue(acc4, DataWord.ZERO);
        assertTrue(marker == null || marker.isZero(),
                "the Bridge credits value through the repository; it must not execute the delegate code");
    }

    // =========================================================================
    // Bridge / PowPeg: delegation *to* the Bridge address
    // =========================================================================

    @Test
    void delegationToBridgeAddress_isInstalledLikeAnyOtherDesignator() {
        byte[] code = stateAt("b05").getCode(address("acc5"));
        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertEquals(PrecompiledContracts.BRIDGE_ADDR,
                DelegationCodeResolver.extractDelegatedAddress(code),
                "RSKIP-545 does not exclude the Bridge address from the authorization tuple");
    }

    @Test
    void callToAccountDelegatedToBridge_doesNotDispatchTheBridge() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCallAccountDelegatedToBridge");
        assertTrue(bridgeLogs(receipt).isEmpty(),
                "resolution short-circuits on a precompile target, so the Bridge is never dispatched");
        assertEquals(21_000L,
                gasUsedInBlock("b06", world.getTransactionByName("txCallAccountDelegatedToBridge")),
                "the call degenerates into a plain value transfer");
    }

    @Test
    void callToAccountDelegatedToBridge_transfersValueToTheAuthorityAndLeavesTheBridgeUntouched() {
        assertEquals(stateAt("b05").getBalance(address("acc5")).add(Coin.valueOf(7)),
                stateAt("b06").getBalance(address("acc5")),
                "value goes to the authority, not to the Bridge it points at");
        assertEquals(stateAt("b05").getBalance(PrecompiledContracts.BRIDGE_ADDR),
                stateAt("b06").getBalance(PrecompiledContracts.BRIDGE_ADDR),
                "the Bridge must be left out of a call to an account that merely points at it");
    }

    // =========================================================================
    // REMASC: fee accounting for a block carrying a Type 4 transaction
    // =========================================================================

    @Test
    void remasc_receivesExactlyTheBlockPaidFeesOfABlockWithAType4Transaction() {
        Block b07 = world.getBlockByName("b07");
        Coin before = stateAt("b06").getBalance(PrecompiledContracts.REMASC_ADDR);
        Coin after = stateAt("b07").getBalance(PrecompiledContracts.REMASC_ADDR);

        assertEquals(b07.getFeesPaidToMiner(), after.subtract(before),
                "REMASC must be credited exactly the block's paidFees when the block carries a Type 4 tx");
        assertTrue(after.compareTo(before) > 0);
    }

    @Test
    void remasc_blockPaidFeesEqualsSumOfGasUsedTimesEffectiveGasPrice() {
        Block b07 = world.getBlockByName("b07");
        Coin expected = Coin.ZERO;
        for (Transaction tx : b07.getTransactionsList()) {
            long gasUsed = gasUsedInBlock("b07", tx);
            expected = expected.add(tx.getGasPrice().multiply(BigInteger.valueOf(gasUsed)));
        }
        assertEquals(expected, b07.getFeesPaidToMiner(),
                "Type 4 effective gas price must feed block fee accounting like any other type");
    }

    @Test
    void remasc_type4FeeShareUsesTheEffectiveGasPrice() {
        Transaction type4 = world.getTransactionByName("txRemascType4");
        Transaction legacy = world.getTransactionByName("txRemascLegacy");
        Coin legacyFee = legacy.getGasPrice()
                .multiply(BigInteger.valueOf(gasUsedInBlock("b07", legacy)));
        Coin remascDelta = stateAt("b07").getBalance(PrecompiledContracts.REMASC_ADDR)
                .subtract(stateAt("b06").getBalance(PrecompiledContracts.REMASC_ADDR));

        Coin expectedType4Share = Coin.valueOf(1_000_000_000L)
                .multiply(BigInteger.valueOf(gasUsedInBlock("b07", type4)));

        assertEquals(expectedType4Share, remascDelta.subtract(legacyFee),
                "the Type 4 share of the REMASC credit is gasUsed x min(maxPriorityFeePerGas, maxFeePerGas)");
    }

    // =========================================================================
    // Nonce semantics: the self-sponsored off-by-one
    // =========================================================================

    @Test
    void selfSponsoredStaleAuthNonce_tupleIsSkipped() {
        byte[] code = stateAt("b08").getCode(address("acc9"));
        assertTrue(code == null || code.length == 0,
                "the sender nonce is bumped before the tuple runs, so an auth nonce equal to it is stale");
    }

    @Test
    void selfSponsoredStaleAuthNonce_outerTransactionStillExecutes() {
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS,
                world.getTransactionReceiptByName("txSelfSponsoredStaleAuthNonce").getStatus());
        assertEquals(BigInteger.ONE, stateAt("b08").getNonce(address("acc9")),
                "only the sender increment applies when the tuple is skipped");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static RskAddress address(String accountName) {
        return world.getAccountByName(accountName).getAddress();
    }

    private static RepositorySnapshot stateAt(String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
    }

    /** Per-transaction gas as the chain records it: the delta between cumulative receipt values. */
    private static long gasUsedInBlock(String blockName, Transaction target) {
        Block block = world.getBlockByName(blockName);
        long previousCumulative = 0;
        for (Transaction tx : block.getTransactionsList()) {
            long cumulative = world.getReceiptStore()
                    .get(tx.getHash().getBytes(), block.getHash().getBytes())
                    .orElseThrow(() -> new IllegalStateException("Missing receipt for " + tx.getHash()))
                    .getReceipt().getCumulativeGasLong();
            if (tx.getHash().equals(target.getHash())) {
                return cumulative - previousCumulative;
            }
            previousCumulative = cumulative;
        }
        throw new IllegalStateException("Transaction not found in block " + blockName);
    }

    private static List<LogInfo> bridgeLogs(TransactionReceipt receipt) {
        return receipt.getLogInfoList().stream()
                .filter(log -> PrecompiledContracts.BRIDGE_ADDR.equals(new RskAddress(log.getAddress())))
                .toList();
    }

    private static LogInfo onlyBridgeLog(String transactionName) {
        List<LogInfo> logs = bridgeLogs(world.getTransactionReceiptByName(transactionName));
        assertEquals(1, logs.size(), "expected exactly one Bridge log for " + transactionName);
        return logs.get(0);
    }

    private static byte[] topic0(LogInfo log) {
        return log.getTopics().get(0).getData();
    }

    /** Last 32-byte word of a release_request_rejected payload, or 0 for any other event. */
    private static int rejectionReasonOrZero(LogInfo log) {
        if (!java.util.Arrays.equals(PEGOUT_REJECTED_TOPIC, topic0(log))) {
            return 0;
        }
        byte[] data = log.getData();
        return DataWord.valueOf(java.util.Arrays.copyOfRange(data, data.length - 32, data.length)).intValue();
    }
}
