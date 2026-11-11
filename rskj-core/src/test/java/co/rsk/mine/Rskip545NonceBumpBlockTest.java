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
package co.rsk.mine;

import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockResult;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.validators.BlockTxsValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ImportResult;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Rskip545NonceBumpBlockTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final Coin BALANCE = Coin.valueOf(1_000_000_000_000_000L);

    // PUSH1 0 (size), PUSH1 0 (offset), PUSH1 0 (value), CREATE, STOP
    private static final byte[] CREATE_ONCE = Hex.decode("600060006000f000");
    // PUSH1 0 (salt), PUSH1 0 (size), PUSH1 0 (offset), PUSH1 0 (value), CREATE2, STOP
    private static final byte[] CREATE2_ONCE = Hex.decode("6000600060006000f500");
    // CREATE, POP, CREATE, STOP
    private static final byte[] CREATE_TWICE = Hex.decode("600060006000f050600060006000f000");
    // STOP
    private static final byte[] NOOP = Hex.decode("00");

    private static final RskAddress RECEIVER = new RskAddress("0000000000000000000000000000000000001234");
    // Distinct destinations for the RSKIP-144 sublist tests, so the txs share no account other than the authority.
    private static final RskAddress ADDRESS_A = new RskAddress("000000000000000000000000000000000000aaaa");
    private static final RskAddress ADDRESS_B = new RskAddress("000000000000000000000000000000000000bbbb");
    private static final RskAddress ADDRESS_C = new RskAddress("000000000000000000000000000000000000cccc");


    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Txs:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 2.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorTupleBumpsAuthorityNonce_authorityTxAtNPlusOneIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        network.connect(type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n)), transfer(network.authority, n + 1));
        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Txs:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuples (Authority, nonce 0, delegate noop), (Authority, nonce 1, delegate noop)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorCarriesTwoAuthorityTuples_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n), tuple(network.authority, network.noop, n + 1)),
                transfer(network.authority, n + 2));

        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0, Sponsor2 with nonce 0.
     * Txs:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop)
     *   2. Type4 from Sponsor2 (nonce 0) to RECEIVER with Tuple (Authority, nonce 1, delegate noop)
     *   3. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_twoSponsorsCarryAuthorityTuples_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n)),
                type4(network.sponsor2, network.nonceOf(network.sponsor2), RECEIVER, tuple(network.authority, network.noop, n + 1)),
                transfer(network.authority, n + 2));

        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 0.
     * Txs:
     *   1. Type4 from Authority (nonce 0) to RECEIVER with Tuple (Authority, nonce 1, delegate noop)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_authorityCarriesOwnTupleAtNPlusOne_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.authority, n, RECEIVER, tuple(network.authority, network.noop, n + 1)),
                transfer(network.authority, n + 2));

        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 1 delegated to createOnce, Sponsor with nonce 1.
     * Txs:
     *   1. Type0 from Sponsor (nonce 1) to Authority (its delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorCallsDelegatedAuthorityRunningCreate_authorityTxAtNPlusOneIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.createOnce);
        long n = network.nonceOf(network.authority);

        network.connect(call(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress()),
                transfer(network.authority, n + 1));

        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 1 delegated to createOnce, Sponsor with nonce 1.
     * Txs:
     *   1. Type0 from Authority (nonce 1) to Authority (its delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 3) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 4.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_authoritySelfCallRunsCreate_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.createOnce);
        long n = network.nonceOf(network.authority);

        network.connect(call(network.authority, n, network.authority.getAddress()), transfer(network.authority, n + 2));
        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Txs:
     *   1. Type4 from Sponsor (nonce 0) to Authority with Tuple (Authority, nonce 0, delegate createOnce) (the new delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorTupleAndCallToAuthorityRunsCreate_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress(), tuple(network.authority, network.createOnce, n)),
                transfer(network.authority, n + 2));

        network.assertNonce(network.authority, n + 3);
    }


    /**
     * Setup: Authority with nonce 1 delegated to create2Once, Sponsor with nonce 1.
     * Txs:
     *   1. Type0 from Sponsor (nonce 1) to Authority (its delegate runs CREATE2 once)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorCallsDelegatedAuthorityRunningCreate2_authorityTxAtNPlusOneIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.create2Once);
        long n = network.nonceOf(network.authority);

        network.connect(
                call(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress()),
                transfer(network.authority, n + 1));

        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 1 delegated to createTwice, Sponsor with nonce 1.
     * Txs:
     *   1. Type0 from Sponsor (nonce 1) to Authority (its delegate runs CREATE twice)
     *   2. Type0 from Authority (nonce 3) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 4.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_sponsorCallsDelegatedAuthorityRunningTwoCreates_authorityTxAtNPlusTwoIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.createTwice);
        long n = network.nonceOf(network.authority);

        network.connect(call(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress()), transfer(network.authority, n + 2));

        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 0.
     * Txs:
     *   1. Type4 from Authority (nonce 0) to Authority with Tuple (Authority, nonce 1, delegate createOnce) (the new delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 3) to RECEIVER
     * Expected: both nodes import the block, Authority ends with nonce 4.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_authorityOwnTupleAndSelfCallRunsCreate_authorityTxAtNPlusThreeIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.authority, n, network.authority.getAddress(), tuple(network.authority, network.createOnce, n + 1)),
                transfer(network.authority, n + 3));

        network.assertNonce(network.authority, n + 4);
    }


    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Pending txs:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     * Expected: the miner includes both and builds [tx 1, tx 2] both nodes import the block, Authority ends with nonce 2.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_sponsorTupleAndAuthorityTxAtNPlusOnePending_includesBothAndBlockIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        Transaction sponsorTx = type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n));
        Transaction authorityTx = transfer(network.authority, n + 1);

        Block block = network.mine(sponsorTx, authorityTx);

        assertEquals(Arrays.asList(sponsorTx, authorityTx), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 0.
     * Pending txs:
     *   1. Type4 from Authority (nonce 0) to RECEIVER with Tuple (Authority, nonce 1, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     *   3. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: the miner drops tx 2 (nonce 1 already consumed by the tuple) and builds [tx 1, tx 3].
     * Both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_authorityOwnTupleAtNPlusOne_dropsConsumedNonceAndBlockIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        Transaction ownTuple = type4(network.authority, n, RECEIVER, tuple(network.authority, network.noop, n + 1));
        Transaction consumed = transfer(network.authority, n + 1);
        Transaction next = transfer(network.authority, n + 2);

        Block block = network.mine(ownTuple, consumed, next);

        assertEquals(Arrays.asList(ownTuple, next), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 1 delegated to createOnce, Sponsor with nonce 1.
     * Pending txs:
     *   1. Type0 from Sponsor (nonce 1) to Authority, gas price 10 so it is ordered first (its delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     *   3. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: the miner drops tx 2 (nonce 1 already consumed by the CREATE) and builds [tx 1, tx 3];
     *   both nodes import the block, Authority ends with nonce 3.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_sponsorCallRunsDelegatedCreate_dropsConsumedNonceAndBlockIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.createOnce);
        long n = network.nonceOf(network.authority);
        Transaction sponsorCall = callWithPrice(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress(), 10);
        Transaction consumed = transfer(network.authority, n);
        Transaction next = transfer(network.authority, n + 1);

        Block block = network.mine(sponsorCall, consumed, next);

        assertEquals(Arrays.asList(sponsorCall, next), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Pending txs:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop), fee 10 so it is ordered first
     *   2. Type0 from Authority (nonce 0) to RECEIVER
     *   3. Type0 from Authority (nonce 1) to RECEIVER
     * Expected: the miner drops tx 2 (nonce 0 already consumed by the sponsor's tuple) and builds [tx 1, tx 3];
     *   both nodes import the block, Authority ends with nonce 2.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_sponsorTupleConsumesAuthorityNonce_dropsConsumedNonceAndBlockIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        Transaction sponsorType4 = type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n));
        Transaction consumed = transfer(network.authority, n);
        Transaction next = transfer(network.authority, n + 1);

        Block block = network.mine(sponsorType4, consumed, next);

        assertEquals(Arrays.asList(sponsorType4, next), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 1 delegated to createOnce, Sponsor with nonce 1.
     * Pending txs:
     *   1. Type0 from Authority (nonce 1) to Authority (its delegate runs CREATE once)
     *   2. Type0 from Authority (nonce 2) to RECEIVER
     *   3. Type0 from Authority (nonce 3) to RECEIVER
     * Expected: the miner drops tx 2 (nonce 2 already consumed by the CREATE) and builds [tx 1, tx 3];
     *   both nodes import the block, Authority ends with nonce 4.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_authoritySelfCallRunsDelegatedCreate_dropsConsumedNonceAndBlockIsAccepted(int rskip144) {
        Network network = new Network(rskip144);
        network.delegate(network.authority, network.createOnce);
        long n = network.nonceOf(network.authority);
        Transaction selfCall = call(network.authority, n, network.authority.getAddress());
        Transaction consumed = transfer(network.authority, n + 1);
        Transaction next = transfer(network.authority, n + 2);

        Block block = network.mine(selfCall, consumed, next);

        assertEquals(Arrays.asList(selfCall, next), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 3);
    }

    /**
     * Setup: Authority with nonce 0.
     * Block N, pending txs:
     *   1. Type4 from Authority (nonce 0) to RECEIVER with Tuple (Authority, nonce 1, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     *   3. Type0 from Authority (nonce 2) to RECEIVER
     * Block N+1, pending txs (the dropped tx 2 is still pending, as if the pool had not evicted it yet):
     *   1. Type0 from Authority (nonce 1) to RECEIVER
     *   2. Type0 from Authority (nonce 3) to RECEIVER
     * Expected: block N is [tx 1, tx 3]; block N+1 is built on top of it with only the nonce 3 tx (the stale nonce 1 tx
     *   passes the filter and is dropped by execution); both nodes import both blocks, Authority ends with nonce 4.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_afterHaltShape_nextBlockIsStillMinedWithStaleTxPending(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        Transaction ownTuple = type4(network.authority, n, RECEIVER, tuple(network.authority, network.noop, n + 1));
        Transaction consumed = transfer(network.authority, n + 1);
        Transaction next = transfer(network.authority, n + 2);

        Block blockN = network.mine(ownTuple, consumed, next);

        assertEquals(Arrays.asList(ownTuple, next), userTxs(blockN));
        network.importIntoBothNodes(blockN);
        network.assertNonce(network.authority, n + 3);

        Transaction newTx = transfer(network.authority, n + 3);

        Block blockN1 = network.mine(consumed, newTx);

        assertEquals(blockN.getHash(), blockN1.getParentHash(), "block N+1 must be built on top of block N");
        assertEquals(blockN.getNumber() + 1, blockN1.getNumber());
        assertEquals(Collections.singletonList(newTx), userTxs(blockN1), "the stale tx must be dropped, the new one included");
        network.importIntoBothNodes(blockN1);
        network.assertNonce(network.authority, n + 4);
    }



    /**
     * Setup: Authority with nonce 0.
     * Txs sent to the real pool (all three are admitted as pending):
     *   1. Type4 from Authority (nonce 0) to RECEIVER with Tuple (Authority, nonce 1, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     *   3. Type0 from Authority (nonce 2) to RECEIVER
     * Expected: block N is [tx 1, tx 3] (tx 2's nonce was consumed by the tuple) and both nodes import it;
     *   the attacker pays fees (before the fix nothing was mined, so the attack was free);
     *   the pool drops tx 2 when block N arrives, so it is not served again;
     *   block N+1 is mined on top of N and both nodes import it.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlockFromRealPool_ownTupleAtNPlusOneExploit_blockIsMinedPoolIsCleanedAndAttackerPaysFees(int rskip144) {
        Network network = new Network(rskip144);
        TransactionPool realPool = network.minerPool();
        long n = network.nonceOf(network.authority);
        Transaction ownTuple = type4(network.authority, n, RECEIVER, tuple(network.authority, network.noop, n + 1));
        Transaction consumed = transfer(network.authority, n + 1);
        Transaction next = transfer(network.authority, n + 2);

        for (Transaction tx : Arrays.asList(ownTuple, consumed, next)) {
            assertTrue(realPool.addTransaction(tx).pendingTransactionsWereAdded(), "the pool admits every attack tx as pending");
        }
        Coin balanceBefore = network.balanceOf(network.authority);

        Block blockN = network.mineFromNodePool();

        assertEquals(Arrays.asList(ownTuple, next), userTxs(blockN));
        network.importIntoBothNodes(blockN);
        network.assertNonce(network.authority, n + 3);

        Coin spent = balanceBefore.subtract(network.balanceOf(network.authority));
        assertTrue(spent.compareTo(Coin.valueOf(1)) > 0, "the authority must pay fees for the mined txs");
        assertTrue(blockN.getFeesPaidToMiner().compareTo(Coin.ZERO) > 0, "block N must pay fees to the miner");

        // The dropped tx is not served again.
        assertTrue(realPool.getPendingTransactions().isEmpty(), "pending txs after block N");
        assertTrue(realPool.getQueuedTransactions().isEmpty(), "queued txs after block N");

        Block blockN1 = network.mineFromNodePool();

        assertEquals(blockN.getHash(), blockN1.getParentHash(), "block N+1 must be built on top of block N");
        assertTrue(userTxs(blockN1).isEmpty());
        network.importIntoBothNodes(blockN1);
    }

    /**
     * Setup: RSKIP-545 active from block 1 (genesis is before activation);
     * Authority with nonce 0, Sponsor with nonce 0.
     * Pending txs for block 1:
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop), fee 10 so it is ordered first
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     * Expected: block 1 includes both (asking with the parent's number, the static filter would skip tx 2);
     *   both nodes import it, Authority ends with nonce 2.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void mineBlock_atActivationBlock_minerUsesTheNewBlockNumber_includesAuthorityTx(int rskip144) {
        Network network = new Network(rskip144, 1);
        long n = network.nonceOf(network.authority);
        Transaction sponsorType4 = type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n));
        Transaction authorityTx = transfer(network.authority, n + 1);

        Block block = network.mine(sponsorType4, authorityTx);

        assertEquals(1, block.getNumber(), "the first block after genesis is the activation block");
        assertEquals(Arrays.asList(sponsorType4, authorityTx), userTxs(block));
        network.importIntoBothNodes(block);
        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: RSKIP-545 active from block 1 (genesis is before activation); Authority with nonce 0, Sponsor with nonce 0.
     * Txs (exact block 1):
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop)
     *   2. Type0 from Authority (nonce 1) to RECEIVER
     * Expected: both nodes import block 1 (asking with the parent's number, the rule would expect the authority's tx
     *   at nonce 0 and reject it), Authority ends with nonce 2.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_atActivationBlock_ruleUsesTheBlockNumber_acceptsAuthorityTx(int rskip144) {
        Network network = new Network(rskip144, 1);
        long n = network.nonceOf(network.authority);

        network.connect(
                type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n)),
                transfer(network.authority, n + 1));

        network.assertNonce(network.authority, n + 2);
    }

    /**
     * Setup: Authority with nonce 0, Sponsor with nonce 0.
     * Txs (crafted, not executed by a miner, which would discard the stale tx):
     *   1. Type4 from Sponsor (nonce 0) to RECEIVER with Tuple (Authority, nonce 0, delegate noop)
     *   2. Type0 from Authority (nonce 0) to RECEIVER, a nonce the tuple already consumed
     * Expected: the static rule no longer checks nonces (and the old one would have accepted nonce 0 here, since it
     *   matches the parent state), so execution must reject it: both nodes return INVALID_BLOCK, the best block
     *   and Authority's nonce are unchanged.
     */
    @ParameterizedTest(name = "rskip144 = {0}")
    @ValueSource(ints = {0, -1})
    void validateBlock_authorityTxReusesNonceConsumedByEarlierTuple_blockIsRejected(int rskip144) {
        Network network = new Network(rskip144);
        long n = network.nonceOf(network.authority);
        Block parent = network.miner.getBlockChain().getBestBlock();

        Block malformed = network.unexecutedBlock(
                type4(network.sponsor, network.nonceOf(network.sponsor), RECEIVER, tuple(network.authority, network.noop, n)),
                transfer(network.authority, n));

        BlockTxsValidationRule staticRule = new BlockTxsValidationRule(
                network.follower.getRepositoryLocator(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()),
                network.follower.getConfig().getActivationConfig());
        assertTrue(staticRule.isValid(malformed, parent), "the static rule must let the block through, so the rejection comes from execution");
        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, network.executeOnFollower(malformed), "execution must reject the reused nonce");

        network.assertRejectedByBothNodes(malformed, parent);
        network.assertNonce(network.authority, n);
    }

    /**
     * Setup: RSKIP-144 active; Authority with nonce 0, Sponsor with nonce 0, Sponsor2 with nonce 0.
     * Txs (crafted, not executed by a miner), sublist edges [1, 2]:
     *   sublist 1: Type4 from Sponsor (nonce 0) to ADDRESS_B with Tuple (Authority, nonce 0, delegate noop)
     *   sublist 2: Type0 from Authority (nonce 0) to ADDRESS_A
     * Expected: the follower's parallel execution detects the collision and interrupts the block.
     * Control: the same layout with an independent tx in sublist 2 (Sponsor2 to ADDRESS_C) executes normally.
     */
    @Test
    void validateBlock_parallelSublists_tupleAndAuthorityTxInDifferentSublists_collisionIsDetected() {
        Network network = new Network(0);
        Transaction sponsorType4 = type4(network.sponsor, 0, ADDRESS_B, tuple(network.authority, network.noop, 0));
        Transaction authorityTx = call(network.authority, 0, ADDRESS_A);
        Transaction independentTx = call(network.sponsor2, 0, ADDRESS_C);

        Block conflicting = network.unexecutedBlockWithSublists(new short[]{1, 2}, sponsorType4, authorityTx);
        Block control = network.unexecutedBlockWithSublists(new short[]{1, 2}, sponsorType4, independentTx);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, network.executeOnFollower(conflicting), "the tuple and the authority's tx touch the same account, so parallel execution must interrupt");
        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, network.executeOnFollower(control), "control: independent txs in the same sublist layout must execute");
    }

    /**
     * Setup: RSKIP-144 active; Authority with nonce 1 delegated to createOnce, Sponsor with nonce 1, Sponsor2 with nonce 0.
     * Txs (crafted, not executed by a miner), sublist edges [1, 2]:
     *   sublist 1: Type0 from Sponsor (nonce 1) to Authority (its delegate runs CREATE once)
     *   sublist 2: Type0 from Authority (nonce 1) to ADDRESS_A
     * Expected: the follower's parallel execution detects the collision and interrupts the block.
     * Control: the same layout with an independent tx in sublist 2 (Sponsor2 to ADDRESS_C) executes normally.
     */
    @Test
    void validateBlock_parallelSublists_delegatedCreateAndAuthorityTxInDifferentSublists_collisionIsDetected() {
        Network network = new Network(0);
        network.delegate(network.authority, network.createOnce);
        long n = network.nonceOf(network.authority);
        Transaction sponsorCall = call(network.sponsor, network.nonceOf(network.sponsor), network.authority.getAddress());
        Transaction authorityTx = call(network.authority, n, ADDRESS_A);
        Transaction independentTx = call(network.sponsor2, 0, ADDRESS_C);

        Block conflicting = network.unexecutedBlockWithSublists(new short[]{1, 2}, sponsorCall, authorityTx);
        Block control = network.unexecutedBlockWithSublists(new short[]{1, 2}, sponsorCall, independentTx);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, network.executeOnFollower(conflicting), "the delegated CREATE and the authority's tx touch the same account, so parallel execution must interrupt");
        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, network.executeOnFollower(control), "control: independent txs in the same sublist layout must execute");
    }

    private static class Network {
        private final World miner;
        private final World follower;
        private final BlockToMineBuilder blockToMineBuilder;
        private final TransactionPool pool = mock(TransactionPool.class);
        private final BlockToMineBuilder nodePoolBlockToMineBuilder;

        final Account authority;
        final Account sponsor;
        final Account sponsor2;
        final RskAddress noop;
        final RskAddress createOnce;
        final RskAddress create2Once;
        final RskAddress createTwice;

        Network(int rskip144) {
            this(rskip144, 0);
        }

        Network(int rskip144, long rskip545Activation) {
            miner = newWorld(rskip144, rskip545Activation);
            follower = newWorld(rskip144, rskip545Activation);

            authority = miner.getAccountByName("authority");
            sponsor = miner.getAccountByName("sponsor");
            sponsor2 = miner.getAccountByName("sponsor2");
            noop = miner.getAccountByName("noop").getAddress();
            createOnce = miner.getAccountByName("createOnce").getAddress();
            create2Once = miner.getAccountByName("create2Once").getAddress();
            createTwice = miner.getAccountByName("createTwice").getAddress();

            assertEquals(miner.getBlockChain().getBestBlock().getHash(), follower.getBlockChain().getBestBlock().getHash(), "both nodes must start from the same genesis");

            blockToMineBuilder = newBlockToMineBuilder(miner, pool);
            nodePoolBlockToMineBuilder = newBlockToMineBuilder(miner, miner.getTransactionPool());
        }

        /** The miner node's real transaction pool, synced to its best block. */
        TransactionPool minerPool() {
            TransactionPool realPool = miner.getTransactionPool();
            realPool.processBest(miner.getBlockChain().getBestBlock());
            return realPool;
        }

        Coin balanceOf(Account account) {
            return Rskip545TestSupport.snapshotAtBest(miner).getBalance(account.getAddress());
        }

        /** Builds a block template with the real miner reading the miner node's real transaction pool. */
        Block mineFromNodePool() {
            return nodePoolBlockToMineBuilder
                    .build(Collections.singletonList(miner.getBlockChain().getBestBlock().getHeader()), new byte[0])
                    .getBlock();
        }

        long nonceOf(Account account) {
            return Rskip545TestSupport.snapshotAtBest(miner).getNonce(account.getAddress()).longValue();
        }

        void assertNonce(Account account, long expected) {
            assertEquals(BigInteger.valueOf(expected), Rskip545TestSupport.snapshotAtBest(miner).getNonce(account.getAddress()));
            assertEquals(BigInteger.valueOf(expected), Rskip545TestSupport.snapshotAtBest(follower).getNonce(account.getAddress()));
        }

        /** Delegates {@code authority} to {@code delegate} with a sponsor's type-4, in its own block. */
        void delegate(Account authority, RskAddress delegate) {
            connect(type4(sponsor, nonceOf(sponsor), RECEIVER, tuple(authority, delegate, nonceOf(authority))));
        }

        /** Builds a block with exactly these txs (real execution) and imports it into both nodes. */
        void connect(Transaction... txs) {
            Block parent = miner.getBlockChain().getBestBlock();
            Block block = new BlockBuilder(miner.getBlockChain(), miner.getBridgeSupportFactory(), miner.getBlockStore())
                    .trieStore(miner.getTrieStore())
                    .parent(parent)
                    .transactions(Arrays.asList(txs))
                    .build(miner.getConfig());

            assertEquals(Arrays.asList(txs), block.getTransactionsList(), "execution must keep every transaction");

            assertEquals(ImportResult.IMPORTED_BEST, miner.getBlockChain().tryToConnect(block), "miner node must accept its block");
            assertEquals(ImportResult.IMPORTED_BEST, follower.getBlockChain().tryToConnect(block), "second node must accept the block");
        }

        /** Builds a block template with the real miner, serving {@code pending} as the pool's pending txs. */
        Block mine(Transaction... pending) {
            when(pool.getPendingTransactions()).thenReturn(new ArrayList<>(Arrays.asList(pending)));
            return blockToMineBuilder
                    .build(Collections.singletonList(miner.getBlockChain().getBestBlock().getHeader()), new byte[0])
                    .getBlock();
        }

        /**
         * Builds a block on the miner's best block with these txs WITHOUT executing them (default sublist edges).
         * Used to craft blocks an honest miner would never build.
         */
        Block unexecutedBlock(Transaction... txs) {
            return new BlockBuilder(miner.getBlockChain(), miner.getBridgeSupportFactory(), miner.getBlockStore())
                    .trieStore(miner.getTrieStore())
                    .parent(miner.getBlockChain().getBestBlock())
                    .transactions(Arrays.asList(txs))
                    .buildWithoutExecution();
        }

        void assertRejectedByBothNodes(Block block, Block expectedBest) {
            assertEquals(ImportResult.INVALID_BLOCK, miner.getBlockChain().tryToConnect(block), "miner node must reject the block");
            assertEquals(ImportResult.INVALID_BLOCK, follower.getBlockChain().tryToConnect(block), "second node must reject the block");
            assertEquals(expectedBest.getHash(), miner.getBlockChain().getBestBlock().getHash(), "miner node best block must not change");
            assertEquals(expectedBest.getHash(), follower.getBlockChain().getBestBlock().getHash(), "second node best block must not change");
        }

        /**
         * Builds a block on the miner's best block with these txs WITHOUT executing them, and sets its parallel
         * execution sublist edges (RSKIP-144). Used to craft blocks an honest miner would never build.
         */
        Block unexecutedBlockWithSublists(short[] edges, Transaction... txs) {
            Block block = new BlockBuilder(miner.getBlockChain(), miner.getBridgeSupportFactory(), miner.getBlockStore())
                    .trieStore(miner.getTrieStore())
                    .parent(miner.getBlockChain().getBestBlock())
                    .transactions(Arrays.asList(txs))
                    // Large enough that each parallel sublist gets a gas limit above the txs' gas (see BlockUtils.getSublistGasLimit).
                    .gasLimit(BigInteger.valueOf(40_000_000))
                    .buildWithoutExecution();
            block.getHeader().setTxExecutionSublistsEdges(edges);
            return block;
        }

        /** Executes the block on the follower as validation does (no discarding, no saving). */
        BlockResult executeOnFollower(Block block) {
            return follower.getBlockExecutor().execute(null, 0, block, follower.getBlockChain().getBestBlock().getHeader(), false, false, false);
        }

        void importIntoBothNodes(Block block) {
            assertEquals(ImportResult.IMPORTED_BEST, miner.getBlockChain().tryToConnect(block), "miner node must accept its block");
            assertEquals(ImportResult.IMPORTED_BEST, follower.getBlockChain().tryToConnect(block), "second node must accept the block");
        }

    }

    private static World newWorld(int rskip144, long rskip545Activation) {
        var config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(rskip545Activation))
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144)));
        World world = new World(config);

        saveAccount(world, "authority", BALANCE, null);
        saveAccount(world, "sponsor", BALANCE, null);
        saveAccount(world, "sponsor2", BALANCE, null);
        saveAccount(world, "noop", null, NOOP);
        saveAccount(world, "createOnce", null, CREATE_ONCE);
        saveAccount(world, "create2Once", null, CREATE2_ONCE);
        saveAccount(world, "createTwice", null, CREATE_TWICE);
        return world;
    }

    private static void saveAccount(World world, String name, Coin balance, byte[] code) {
        AccountBuilder builder = new AccountBuilder(world).name(name);
        if (balance != null) {
            builder.balance(balance);
        }
        if (code != null) {
            builder.code(code);
        }
        world.saveAccount(name, builder.build());
    }

    private static BlockToMineBuilder newBlockToMineBuilder(World world, TransactionPool pool) {
        MiningConfig miningConfig = mock(MiningConfig.class);
        when(miningConfig.getGasLimit()).thenReturn(new GasLimitConfig(6_800_000, 6_800_000, true));
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getUncleGenerationLimit()).thenReturn(7);
        when(miningConfig.getCoinbaseAddress()).thenReturn(new RskAddress("0000000000000000000000000000000000c01bba"));

        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(BlockDifficulty.ONE);

        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(Coin.ZERO);

        MinerClock clock = mock(MinerClock.class);
        when(clock.calculateTimestampForChild(any())).thenAnswer(inv ->
                ((org.ethereum.core.BlockHeader) inv.getArgument(0)).getTimestamp() + 1);

        BlockValidationRule validationRules = mock(BlockValidationRule.class);
        when(validationRules.isValid(any())).thenReturn(true);

        return new BlockToMineBuilder(
                world.getConfig().getActivationConfig(),
                miningConfig,
                world.getRepositoryLocator(),
                world.getBlockStore(),
                pool,
                difficultyCalculator,
                new GasLimitCalculator(world.getConfig().getNetworkConstants()),
                new ForkDetectionDataCalculator(RegTestParams.get()),
                validationRules,
                clock,
                new BlockFactory(world.getConfig().getActivationConfig()),
                world.getBlockExecutor(),
                minimumGasPriceCalculator,
                new MinerUtils(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    private static List<Transaction> userTxs(Block block) {
        List<Transaction> txs = new ArrayList<>(block.getTransactionsList());
        txs.removeIf(tx -> tx instanceof co.rsk.remasc.RemascTransaction);
        return txs;
    }

    private static SetCodeAuthorization tuple(Account authority, RskAddress delegate, long nonce) {
        return Rskip545TestSupport.createSignedAuthorization(authority.getEcKey(), delegate, BigInteger.valueOf(nonce), CHAIN_ID);
    }

    private static Transaction type4(Account sender, long nonce, RskAddress to, SetCodeAuthorization... tuples) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.valueOf(nonce))
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(10))
                .gasLimit(BigInteger.valueOf(500_000))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(Arrays.asList(tuples))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Transaction call(Account sender, long nonce, RskAddress to) {
        return callWithPrice(sender, nonce, to, 1);
    }

    private static Transaction callWithPrice(Account sender, long nonce, RskAddress to, long gasPrice) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .gasLimit(BigInteger.valueOf(300_000))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Transaction transfer(Account sender, long nonce) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(RECEIVER)
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }
}
