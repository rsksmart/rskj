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
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hardfork safety tests for RSKIP-545 (requires RSKIP-543 and RSKIP-546).
 */
class Rskip545HardforkTest {

    private static final int ACTIVATION = 5;
    private static final RskAddress DELEGATE_03 =
            new RskAddress("0x0000000000000000000000000000000000000003");

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
                "dsl/transaction/rskip545/rskip545HardforkTest.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    void preActivation_legacyTransactionExecutes() {
        assertArrayEquals(new byte[]{1}, world.getTransactionReceiptByName("txPreLegacy").getStatus());
    }

    @Test
    void preActivation_type4Attempt_isDroppedFromBlock() {
        Block b03 = world.getBlockByName("b03");
        assertTrue(b03.getTransactionsList().isEmpty(),
                "Type 4 tx before RSKIP-545 activation must be dropped from executed block");
    }

    @Test
    void preActivation_type4Attempt_notIncludedInBlock() {
        Transaction dropped = world.getTransactionByName("txPreType4Attempt");
        Block b03 = world.getBlockByName("b03");
        assertTrue(b03.getTransactionsList().stream()
                        .noneMatch(tx -> tx.getHash().equals(dropped.getHash())),
                "Pre-activation Type 4 tx must not appear in the connected block");
    }

    @Test
    void poolRejection_type4_blockedBeforeActivation() {
        Transaction tx = world.getTransactionByName("txTransitionType4");
        assertTrue(tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1)));
    }

    @Test
    void poolRejection_type4_allowedAtActivation() {
        Transaction tx = world.getTransactionByName("txTransitionType4");
        assertFalse(tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION)));
    }

    @Test
    void poolRejection_type4_blockedWhenOnlyRskip545Inactive() {
        Transaction tx = world.getTransactionByName("txTransitionType4");
        var activations = org.mockito.Mockito.mock(
                org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock.class);
        org.mockito.Mockito.when(activations.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        org.mockito.Mockito.when(activations.isActive(ConsensusRule.RSKIP546)).thenReturn(true);
        org.mockito.Mockito.when(activations.isActive(ConsensusRule.RSKIP545)).thenReturn(false);
        assertTrue(tx.isTypedTransactionNotAllowed(activations));
    }

    @Test
    void transitionBlock_acceptsType4AndLegacy() {
        Block b05 = world.getBlockByName("b05");
        assertEquals(ACTIVATION, b05.getNumber());
        assertEquals(2, b05.getTransactionsList().size());
        assertEquals(TransactionType.TYPE_4, b05.getTransactionsList().get(0).getType());
        assertEquals(TransactionType.LEGACY, b05.getTransactionsList().get(1).getType());
    }

    @Test
    void transitionBlock_type4Receipt_hasPrefixByte() {
        byte[] encoded = world.getTransactionReceiptByName("txTransitionType4").getEncoded();
        assertEquals((byte) 0x04, encoded[0]);
    }

    @Test
    void transitionBlock_legacyReceipt_hasNoTypedPrefix() {
        byte[] encoded = world.getTransactionReceiptByName("txTransitionLegacy").getEncoded();
        assertTrue((encoded[0] & 0xFF) >= 0xc0);
    }

    @Test
    void postActivation_type4StillWorks() {
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txPostType4").getStatus());
    }

    @Test
    void postActivation_legacyStillWorks() {
        assertArrayEquals(new byte[]{1},
                world.getTransactionReceiptByName("txPostLegacy").getStatus());
    }

    @Test
    void historicalIntegrity_preForkBlockHash_isStable() {
        Block b01 = world.getBlockByName("b01");
        BlockFactory factory = new BlockFactory(world.getConfig().getActivationConfig());
        assertEquals(b01.getHash(), factory.decodeBlock(b01.getEncoded()).getHash());
    }

    @Test
    void historicalIntegrity_preForkReceipt_decodable() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacy");
        TransactionReceipt decoded = new TransactionReceipt(receipt.getEncoded());
        assertArrayEquals(receipt.getStatus(), decoded.getStatus());
    }

    @Test
    void activationBoundary_blockAtActivationMinus1_type4Blocked() {
        Transaction tx = world.getTransactionByName("txTransitionType4");
        assertTrue(tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1)));
    }

    @Test
    void activationBoundary_blockAtActivation_type4Allowed() {
        Transaction tx = world.getTransactionByName("txTransitionType4");
        assertFalse(tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION)));
    }

    @Test
    void networkSafety_nodeWithoutRskip545_blocksType4() {
        TestSystemProperties no545 = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(-1))
        );
        Transaction tx = world.getTransactionByName("txTransitionType4");
        assertTrue(tx.isTypedTransactionNotAllowed(no545.getActivationConfig().forBlock(100)));
    }

    @Test
    void blockBeforeActivation_type4BlockedByActivationRules() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
        );
        World w = new World(config);
        Account sender = new AccountBuilder(w).name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L)).build();
        Account receiver = new AccountBuilder(w).name("receiver").build();

        var auth = Rskip545TestSupport.createSignedAuthorization(
                sender.getEcKey(), DELEGATE_03, BigInteger.ONE, Constants.REGTEST_CHAIN_ID);
        Transaction type4 = Rskip545TestSupport.unsignedType4WithAuthorizations(
                receiver.getAddress(), BigInteger.valueOf(100_000), List.of(auth));
        type4.sign(sender.getEcKey().getPrivKeyBytes());

        assertTrue(type4.isTypedTransactionNotAllowed(config.getActivationConfig().forBlock(1)),
                "Type 4 must be blocked at block 1 when activation height is " + ACTIVATION);
        assertFalse(type4.isTypedTransactionNotAllowed(config.getActivationConfig().forBlock(ACTIVATION)),
                "Type 4 must be allowed at activation height");
    }

    @Test
    void blockAtActivation_withType4_passesValidation() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(1))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(1))
                        .withValue("blockchain.config.consensusRules.rskip545",
                                ConfigValueFactory.fromAnyRef(1))
        );
        World w = new World(config);
        Account sender = new AccountBuilder(w).name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L)).build();
        Account receiver = new AccountBuilder(w).name("receiver").build();

        var auth = Rskip545TestSupport.createSignedAuthorization(
                sender.getEcKey(), DELEGATE_03, BigInteger.ONE, Constants.REGTEST_CHAIN_ID);
        Transaction type4 = Rskip545TestSupport.unsignedType4WithAuthorizations(
                receiver.getAddress(), BigInteger.valueOf(100_000), List.of(auth));
        type4.sign(sender.getEcKey().getPrivKeyBytes());

        Block genesis = w.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(w.getBlockChain(), null, w.getBlockStore())
                .trieStore(w.getTrieStore())
                .parent(genesis)
                .transactions(Collections.singletonList(type4))
                .build();

        assertEquals(1, block.getTransactionsList().size());
        assertTrue(w.getBlockExecutor().executeAndValidate(block, genesis.getHeader()));
    }

    @Test
    void chainContinuity_finalHeight_isSeven() {
        assertEquals(7, world.getBlockChain().getBestBlock().getNumber());
    }
}
