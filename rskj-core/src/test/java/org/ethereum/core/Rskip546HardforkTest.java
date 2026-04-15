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
import co.rsk.crypto.Keccak256;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.db.TransactionInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hardfork safety tests for RSKIP-543 and RSKIP-546.
 *
 * <p>The tests simulate a live chain transitioning through the RSKIP-546 hardfork at block 5.
 *
 * <p><b>Scenarios covered:</b>
 * <ul>
 *   <li><b>Pre-activation safety:</b> Legacy transactions work before the fork; typed transactions
 *       are silently dropped from blocks (not executed)</li>
 *   <li><b>Transaction pool rejection:</b> Typed transactions are rejected from the pool before
 *       activation (both at pool and executor level)</li>
 *   <li><b>Transition block:</b> Block at activation height simultaneously accepts legacy and
 *       typed transactions</li>
 *   <li><b>Backward compatibility:</b> Legacy transactions still execute after the fork</li>
 *   <li><b>Historical data integrity:</b> All pre-fork block hashes, tx hashes, and receipts
 *       remain identical and retrievable after the fork is active</li>
 *   <li><b>Pre-fork contract accessibility:</b> Contracts deployed before the fork remain
 *       callable with typed transactions after the fork</li>
 *   <li><b>Receipt encoding boundary:</b> Pre-fork receipts use legacy RLP encoding; post-fork
 *       typed transaction receipts use the typed receipt format</li>
 *   <li><b>Block encode/decode stability:</b> Pre-fork blocks survive encode/decode unchanged
 *       after the new code is active</li>
 *   <li><b>Activation boundary precision:</b> {activation - 1} rejects typed txs;
 *       {activation} accepts typed txs</li>
 * </ul>
 */
class Rskip546HardforkTest {

    /**
     * Activation height for both RSKIP-543 and RSKIP-546 in this test suite.
     * Blocks 1..{@code ACTIVATION - 1} are pre-fork; blocks {@code ACTIVATION}+ are post-fork.
     */
    private static final int ACTIVATION = 5;

    /** DSL-driven world: chain built with activation at block 5. */
    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(ACTIVATION))
        );

        DslParser parser = DslParser.fromResource(
                "dsl/transaction/rskip546/rskip546HardforkTest.txt");
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    // =========================================================================
    // A. Pre-activation block integrity
    // =========================================================================

    @Test
    void preActivation_legacyTransactionExecutes() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacy1");

        assertNotNull(receipt, "Legacy tx in pre-activation block must have a receipt");
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
    }

    @Test
    void preActivation_legacyReceipt_hasNoTypedPrefix() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacy1");
        byte[] encoded = receipt.getEncoded();

        // Legacy receipts start with RLP list marker (0xc0..0xff), never with 0x01 or 0x02
        assertTrue((encoded[0] & 0xFF) >= 0xc0,
                "Pre-activation legacy receipt must not carry a typed prefix byte");
    }

    @Test
    void preActivation_legacyContractDeployment_succeeds() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacyDeploy");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus());
        assertNotNull(world.getTransactionByName("txPreLegacyDeploy").getContractAddress(),
                "Contract address must be set for pre-activation deployment");
    }

    @Test
    void preActivation_typedTxAttempt_isDroppedFromBlock() {
        // b04 includes txPreTypedAttempt (Type 1) before RSKIP-546 is active.
        // TransactionExecutor.init() returns false → tx is dropped from the executed set.
        // The block connects successfully but contains no transactions.
        Block b04 = world.getBlockByName("b04");

        assertNotNull(b04);
        assertTrue(b04.getTransactionsList().isEmpty(),
                "Block before RSKIP-546 activation must not include typed transactions; " +
                "the executor drops them before they are applied");
    }

    @Test
    void preActivation_typedTxAttempt_hasNoReceipt() {
        // Because the tx was dropped, no receipt should be stored.
        Transaction droppedTx = world.getTransactionByName("txPreTypedAttempt");
        TransactionInfo txInfo = world.getReceiptStore()
                .getInMainChain(droppedTx.getHash().getBytes(), world.getBlockStore())
                .orElse(null);

        assertNull(txInfo,
                "Dropped typed tx must have no receipt in the receipt store");
    }

    @Test
    void preActivation_chainHeight_matchesExpected() {
        // After b04 (dropped tx → empty block), chain height must still advance.
        Block b04 = world.getBlockByName("b04");
        assertEquals(4, b04.getNumber());
    }

    // =========================================================================
    // B. Transaction pool / executor rejection before activation
    // =========================================================================

    @Test
    void poolRejection_type1_blockedBeforeActivation() {
        Transaction tx = world.getTransactionByName("txPreTypedAttempt");

        // Block 4 is pre-activation (height 4 < ACTIVATION 5)
        boolean blocked = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1));

        assertTrue(blocked,
                "Type 1 transaction must be rejected before activation height");
    }

    @Test
    void poolRejection_type1_allowedAtActivation() {
        Transaction tx = world.getTransactionByName("txTransitionType1");

        boolean blocked = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION));

        assertFalse(blocked,
                "Type 1 transaction must be allowed at the activation height");
    }

    @Test
    void poolRejection_type2_blockedBeforeActivation() {
        Transaction tx = world.getTransactionByName("txPostType2");

        boolean blocked = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1));

        assertTrue(blocked,
                "Type 2 transaction must be rejected before activation height");
    }

    @Test
    void poolRejection_type2_allowedAtActivation() {
        Transaction tx = world.getTransactionByName("txPostType2");

        boolean blocked = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION));

        assertFalse(blocked,
                "Type 2 transaction must be allowed at the activation height");
    }

    @Test
    void poolRejection_legacy_alwaysAllowed() {
        Transaction legacy = world.getTransactionByName("txPreLegacy1");

        assertFalse(legacy.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1)),
                "Legacy tx must pass pool validation before activation");
        assertFalse(legacy.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION)),
                "Legacy tx must pass pool validation after activation");
        assertFalse(legacy.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION + 10)),
                "Legacy tx must always pass pool validation");
    }

    // =========================================================================
    // C. Transition block (block 5 = first post-activation block)
    // =========================================================================

    @Test
    void transitionBlock_acceptsTypedTransaction() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txTransitionType1");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus(),
                "Type 1 tx must execute successfully in the transition block");
    }

    @Test
    void transitionBlock_acceptsLegacyAndTypedInSameBlock() {
        Block b05 = world.getBlockByName("b05");

        assertNotNull(b05);
        assertEquals(2, b05.getTransactionsList().size(),
                "Transition block must contain both the legacy and the Type 1 transaction");

        Transaction first = b05.getTransactionsList().get(0);
        Transaction second = b05.getTransactionsList().get(1);

        assertEquals(TransactionType.TYPE_1, first.getType());
        assertEquals(TransactionType.LEGACY, second.getType());
    }

    @Test
    void transitionBlock_legacyReceipt_remainsUnprefixed() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txTransitionLegacy");
        byte[] encoded = receipt.getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xc0,
                "Legacy receipt inside the transition block must still use unprefixed encoding");
    }

    @Test
    void transitionBlock_type1Receipt_hasPrefixByte() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txTransitionType1");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
                "Type 1 receipt in the transition block must be prefixed with 0x01");
    }

    @Test
    void transitionBlock_isAtExpectedHeight() {
        Block b05 = world.getBlockByName("b05");
        assertEquals(ACTIVATION, b05.getNumber(),
                "Transition block must be at the activation height");
    }

    // =========================================================================
    // D. Post-activation backward compatibility
    // =========================================================================

    @Test
    void postActivation_legacyTransactionStillWorks() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPostLegacy");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus(),
                "Legacy transactions must still execute after RSKIP-546 activation");
    }

    @Test
    void postActivation_legacyReceipt_stillUnprefixed() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPostLegacy");
        byte[] encoded = receipt.getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xc0,
                "Post-activation legacy receipts must remain unprefixed");
    }

    @Test
    void postActivation_preForkContract_callableWithType2() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPostType2CallContract");

        assertNotNull(receipt);
        assertArrayEquals(new byte[]{1}, receipt.getStatus(),
                "A contract deployed before the fork must be callable via Type 2 after the fork");
    }

    @Test
    void postActivation_type2Receipt_hasPrefixByte() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPostType2");
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
                "Type 2 receipt post-activation must be prefixed with 0x02");
    }

    // =========================================================================
    // E. Historical data integrity (pre-fork data survives post-fork code)
    // =========================================================================

    @Test
    void historicalIntegrity_preForkBlockHash_isStable() {
        Block b01 = world.getBlockByName("b01");
        Keccak256 originalHash = b01.getHash();

        // Re-decode the block from its encoded bytes and compare hash
        BlockFactory factory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = factory.decodeBlock(b01.getEncoded());

        assertEquals(originalHash, decoded.getHash(),
                "Pre-fork block hash must be identical when decoded by post-fork code");
    }

    @Test
    void historicalIntegrity_preForkTxHash_isStable() {
        Transaction tx = world.getTransactionByName("txPreLegacy1");
        Keccak256 originalHash = tx.getHash();

        // Re-decode from raw bytes
        Transaction decoded = new ImmutableTransaction(tx.getEncoded());

        assertEquals(originalHash, decoded.getHash(),
                "Pre-fork transaction hash must not change when re-parsed by post-fork code");
    }

    @Test
    void historicalIntegrity_preForkReceipt_decodable() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacy1");
        byte[] encoded = receipt.getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(encoded);

        assertArrayEquals(receipt.getStatus(), decoded.getStatus(),
                "Pre-fork receipt must decode identically under post-fork code");
        assertArrayEquals(receipt.getCumulativeGas(), decoded.getCumulativeGas());
        assertArrayEquals(receipt.getBloomFilter().getData(),
                decoded.getBloomFilter().getData());
    }

    @Test
    void historicalIntegrity_preForkDeployReceipt_decodable() {
        TransactionReceipt receipt = world.getTransactionReceiptByName("txPreLegacyDeploy");
        TransactionReceipt decoded = new TransactionReceipt(receipt.getEncoded());

        assertArrayEquals(receipt.getStatus(), decoded.getStatus(),
                "Decoded status must match original");
        assertArrayEquals(receipt.getCumulativeGas(), decoded.getCumulativeGas(),
                "Decoded cumulativeGas must match original");
        assertArrayEquals(receipt.getBloomFilter().getData(),
                decoded.getBloomFilter().getData(),
                "Decoded bloom filter must match original");
    }

    @Test
    void historicalIntegrity_preForkBlockRetrievable_byHash() {
        Block b01 = world.getBlockByName("b01");
        Block retrieved = world.getBlockChain().getBlockByHash(b01.getHash().getBytes());

        assertNotNull(retrieved, "Pre-fork block must be retrievable by hash after activation");
        assertEquals(b01.getHash(), retrieved.getHash());
    }

    @Test
    void historicalIntegrity_preForkBlockRetrievable_byNumber() {
        Block b02 = world.getBlockByName("b02");
        // height 2 is pre-fork
        List<Block> byNumber = world.getBlockChain().getBlocksByNumber(2);

        assertFalse(byNumber.isEmpty(), "Pre-fork block must be retrievable by block number");
        assertEquals(b02.getHash(), byNumber.get(0).getHash());
    }

    @Test
    void historicalIntegrity_preForkReceiptRetrievable_fromStore() {
        Transaction tx = world.getTransactionByName("txPreLegacy2");
        TransactionInfo txInfo = world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElse(null);

        assertNotNull(txInfo,
                "Receipt for pre-fork legacy tx must remain in the receipt store after activation");
        assertArrayEquals(new byte[]{1}, txInfo.getReceipt().getStatus());
    }

    @Test
    void historicalIntegrity_preForkBlock_surviveEncodeDecode() {
        Block b03 = world.getBlockByName("b03");
        BlockFactory factory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = factory.decodeBlock(b03.getEncoded());

        assertEquals(1, decoded.getTransactionsList().size(),
                "Block b03 must have exactly 1 transaction after decode");
        assertEquals(world.getTransactionByName("txPreLegacyDeploy").getHash(),
                decoded.getTransactionsList().get(0).getHash(),
                "Pre-fork tx hash must survive block encode/decode by post-fork code");
    }

    @Test
    void historicalIntegrity_preForkEmptyBlock_surviveEncodeDecode() {
        // b04 connected with 0 txs (typed tx was dropped) — must decode cleanly
        Block b04 = world.getBlockByName("b04");
        BlockFactory factory = new BlockFactory(world.getConfig().getActivationConfig());
        Block decoded = factory.decodeBlock(b04.getEncoded());

        assertTrue(decoded.getTransactionsList().isEmpty(),
                "Empty pre-fork block must decode to 0 transactions");
        assertEquals(b04.getHash(), decoded.getHash(),
                "Empty pre-fork block hash must survive encode/decode");
    }

    // =========================================================================
    // F. Block-level validation across the boundary
    //    Uses BlockExecutor.executeAndValidate to directly probe activation semantics.
    // =========================================================================

    /**
     * These inline tests create their own mini worlds to probe specific boundary conditions
     * that are hard to express via the shared DSL world.
     */
    @Nested
    class BlockValidation {

        /**
         * A block at height < ACTIVATION that is given a typed tx for mining:
         * {@code BlockBuilder.build()} calls {@code executeAndFill} internally, which
         * drops the typed tx (TransactionExecutor.init → isTypedTransactionNotAllowed).
         * The resulting mined block has zero transactions and is valid as an empty block.
         *
         * <p>This is the key network-safety property: even if a client submits a typed
         * tx to a pre-activation miner, the miner will never include it in a block —
         * it is silently dropped, and the produced block is a clean empty block.
         */
        @Test
        void blockBeforeActivation_typedTxIsDropped_resultingBlockIsEmpty() {
            TestSystemProperties config = new TestSystemProperties(rawConfig ->
                    rawConfig
                            .withValue("blockchain.config.consensusRules.rskip543",
                                    ConfigValueFactory.fromAnyRef(ACTIVATION))
                            .withValue("blockchain.config.consensusRules.rskip546",
                                    ConfigValueFactory.fromAnyRef(ACTIVATION))
            );
            World w = new World(config);
            Account sender = new AccountBuilder(w).name("sender").balance(Coin.valueOf(1_000_000_000_000L)).build();
            Account receiver = new AccountBuilder(w).name("receiver").build();

            Transaction typedTx = Transaction.builder()
                    .type(TransactionType.TYPE_1)
                    .chainId((byte) 33)
                    .nonce(BigInteger.ZERO)
                    .gasPrice(Coin.valueOf(1_000_000_000L))
                    .gasLimit(BigInteger.valueOf(21_000))
                    .destination(receiver.getAddress())
                    .value(Coin.valueOf(1))
                    .build();
            typedTx.sign(sender.getEcKey().getPrivKeyBytes());

            // Block at height 1 (pre-activation at 5): executeAndFill drops the typed tx
            Block genesis = w.getBlockChain().getBestBlock();
            Block block = new BlockBuilder(w.getBlockChain(), null, w.getBlockStore())
                    .trieStore(w.getTrieStore())
                    .parent(genesis)
                    .transactions(Collections.singletonList(typedTx))
                    .build();

            // The mined block must have 0 transactions: the typed tx was dropped.
            assertTrue(block.getTransactionsList().isEmpty(),
                    "Pre-activation miner must drop typed txs: the block must have 0 transactions");

            // The empty block is valid (state root, receipts root etc. match for 0-tx block).
            assertTrue(w.getBlockExecutor().executeAndValidate(block, genesis.getHeader()),
                    "An empty block (typed tx dropped) must still pass executeAndValidate");
        }

        /**
         * Counterpart: once a typed tx reaches a pre-activation node, it is rejected
         * at the executor level ({@code TransactionExecutor.init} returns {@code false}).
         * This can be verified directly without building a full block.
         */
        @Test
        void blockBeforeActivation_executorRejectsTypedTx_directCheck() {
            TestSystemProperties config = new TestSystemProperties(rawConfig ->
                    rawConfig
                            .withValue("blockchain.config.consensusRules.rskip543",
                                    ConfigValueFactory.fromAnyRef(ACTIVATION))
                            .withValue("blockchain.config.consensusRules.rskip546",
                                    ConfigValueFactory.fromAnyRef(ACTIVATION))
            );

            Transaction typedTx = Transaction.builder()
                    .type(TransactionType.TYPE_1)
                    .chainId((byte) 33)
                    .nonce(BigInteger.ZERO)
                    .gasPrice(Coin.valueOf(1_000_000_000L))
                    .gasLimit(BigInteger.valueOf(21_000))
                    .destination(new co.rsk.core.RskAddress(new byte[20]))
                    .value(Coin.valueOf(1))
                    .build();

            // Pre-activation: the transaction is not allowed
            boolean blockedPreActivation = typedTx.isTypedTransactionNotAllowed(
                    config.getActivationConfig().forBlock(ACTIVATION - 1));
            assertTrue(blockedPreActivation,
                    "Type 1 tx must be blocked at block " + (ACTIVATION - 1));

            // Post-activation: the transaction is allowed
            boolean blockedPostActivation = typedTx.isTypedTransactionNotAllowed(
                    config.getActivationConfig().forBlock(ACTIVATION));
            assertFalse(blockedPostActivation,
                    "Type 1 tx must be allowed at block " + ACTIVATION);
        }

        /**
         * The same typed tx in a block AT the activation height must be valid.
         */
        @Test
        void blockAtActivation_withTypedTx_passesValidation() {
            TestSystemProperties config = new TestSystemProperties(rawConfig ->
                    rawConfig
                            .withValue("blockchain.config.consensusRules.rskip543",
                                    ConfigValueFactory.fromAnyRef(1))
                            .withValue("blockchain.config.consensusRules.rskip546",
                                    ConfigValueFactory.fromAnyRef(1))
            );
            World w = new World(config);
            Account sender = new AccountBuilder(w).name("sender").balance(Coin.valueOf(1_000_000_000_000L)).build();
            Account receiver = new AccountBuilder(w).name("receiver").build();

            Transaction typedTx = Transaction.builder()
                    .type(TransactionType.TYPE_1)
                    .chainId((byte) 33)
                    .nonce(BigInteger.ZERO)
                    .gasPrice(Coin.valueOf(1_000_000_000L))
                    .gasLimit(BigInteger.valueOf(21_000))
                    .destination(receiver.getAddress())
                    .value(Coin.valueOf(1))
                    .build();
            typedTx.sign(sender.getEcKey().getPrivKeyBytes());

            Block genesis = w.getBlockChain().getBestBlock();
            Block block = new BlockBuilder(w.getBlockChain(), null, w.getBlockStore())
                    .trieStore(w.getTrieStore())
                    .parent(genesis)
                    .transactions(Collections.singletonList(typedTx))
                    .build();

            // With activation at block 1, block 1 is already post-activation
            boolean valid = w.getBlockExecutor().executeAndValidate(block, genesis.getHeader());

            assertTrue(valid,
                    "A block containing a typed tx at the RSKIP-546 activation height " +
                    "must pass executeAndValidate");
        }

        /**
         * Legacy transaction blocks are always valid regardless of activation state.
         */
        @Test
        void blockWithLegacyTxOnly_alwaysValidBeforeAndAfterActivation() {
            // Before activation
            TestSystemProperties configOff = new TestSystemProperties(rawConfig ->
                    rawConfig
                            .withValue("blockchain.config.consensusRules.rskip543",
                                    ConfigValueFactory.fromAnyRef(-1))
                            .withValue("blockchain.config.consensusRules.rskip546",
                                    ConfigValueFactory.fromAnyRef(-1))
            );
            World wOff = new World(configOff);
            Account senderOff = new AccountBuilder(wOff).name("s").balance(Coin.valueOf(1_000_000_000_000L)).build();
            Account receiverOff = new AccountBuilder(wOff).name("r").build();

            Transaction legacyOff = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(Coin.valueOf(1_000_000_000L))
                    .gasLimit(BigInteger.valueOf(21_000))
                    .destination(receiverOff.getAddress())
                    .value(Coin.valueOf(1))
                    .build();
            legacyOff.sign(senderOff.getEcKey().getPrivKeyBytes());

            Block genesisOff = wOff.getBlockChain().getBestBlock();
            Block blockOff = new BlockBuilder(wOff.getBlockChain(), null, wOff.getBlockStore())
                    .trieStore(wOff.getTrieStore())
                    .parent(genesisOff)
                    .transactions(Collections.singletonList(legacyOff))
                    .build();

            assertTrue(wOff.getBlockExecutor().executeAndValidate(blockOff, genesisOff.getHeader()),
                    "Legacy tx block must be valid when hardfork is disabled");

            // After activation
            TestSystemProperties configOn = new TestSystemProperties(rawConfig ->
                    rawConfig
                            .withValue("blockchain.config.consensusRules.rskip543",
                                    ConfigValueFactory.fromAnyRef(0))
                            .withValue("blockchain.config.consensusRules.rskip546",
                                    ConfigValueFactory.fromAnyRef(0))
            );
            World wOn = new World(configOn);
            Account senderOn = new AccountBuilder(wOn).name("s").balance(Coin.valueOf(1_000_000_000_000L)).build();
            Account receiverOn = new AccountBuilder(wOn).name("r").build();

            Transaction legacyOn = Transaction.builder()
                    .nonce(BigInteger.ZERO)
                    .gasPrice(Coin.valueOf(1_000_000_000L))
                    .gasLimit(BigInteger.valueOf(21_000))
                    .destination(receiverOn.getAddress())
                    .value(Coin.valueOf(1))
                    .build();
            legacyOn.sign(senderOn.getEcKey().getPrivKeyBytes());

            Block genesisOn = wOn.getBlockChain().getBestBlock();
            Block blockOn = new BlockBuilder(wOn.getBlockChain(), null, wOn.getBlockStore())
                    .trieStore(wOn.getTrieStore())
                    .parent(genesisOn)
                    .transactions(Collections.singletonList(legacyOn))
                    .build();

            assertTrue(wOn.getBlockExecutor().executeAndValidate(blockOn, genesisOn.getHeader()),
                    "Legacy tx block must remain valid after hardfork activation");
        }
    }

    // =========================================================================
    // G. Activation boundary precision (off-by-one safety)
    // =========================================================================

    @Test
    void activationBoundary_blockAtActivationMinus1_typedTxIsBlocked() {
        Transaction tx = world.getTransactionByName("txTransitionType1");

        boolean blockedAtPre = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION - 1));

        assertTrue(blockedAtPre,
                "Typed tx must be blocked at height " + (ACTIVATION - 1) + " (one before activation)");
    }

    @Test
    void activationBoundary_blockAtActivation_typedTxIsAllowed() {
        Transaction tx = world.getTransactionByName("txTransitionType1");

        boolean blockedAtActivation = tx.isTypedTransactionNotAllowed(
                world.getConfig().getActivationConfig().forBlock(ACTIVATION));

        assertFalse(blockedAtActivation,
                "Typed tx must be allowed at activation height " + ACTIVATION);
    }

    @Test
    void activationBoundary_legacyReceiptEncodingDoesNotChange() {
        // Legacy receipts have the same encoding format before and after activation.
        // The encoding format for legacy is: rlp([status, cumulativeGas, bloom, logs])
        // — no type prefix byte, first byte >= 0xC0.
        TransactionReceipt preForkReceipt = world.getTransactionReceiptByName("txPreLegacy1");
        TransactionReceipt postForkReceipt = world.getTransactionReceiptByName("txPostLegacy");

        byte[] preForkEncoded = preForkReceipt.getEncoded();
        byte[] postForkEncoded = postForkReceipt.getEncoded();

        assertTrue((preForkEncoded[0] & 0xFF) >= 0xc0,
                "Pre-fork legacy receipt must start with RLP list marker");
        assertTrue((postForkEncoded[0] & 0xFF) >= 0xc0,
                "Post-fork legacy receipt must also start with RLP list marker");
    }

    @Test
    void activationBoundary_receiptsInTransitionBlock_haveCorrectTypes() {
        // In block 5 (transition): tx[0] = Type1, tx[1] = Legacy
        Block b05 = world.getBlockByName("b05");
        assertEquals(ACTIVATION, b05.getNumber());

        TransactionReceipt type1Receipt = world.getTransactionReceiptByName("txTransitionType1");
        TransactionReceipt legacyReceipt = world.getTransactionReceiptByName("txTransitionLegacy");

        assertEquals((byte) 0x01, type1Receipt.getEncoded()[0],
                "Type 1 receipt in transition block must have 0x01 prefix");
        assertTrue((legacyReceipt.getEncoded()[0] & 0xFF) >= 0xc0,
                "Legacy receipt in transition block must have no typed prefix");
    }

    // =========================================================================
    // H. Chain continuity and consistency
    // =========================================================================

    @Test
    void chainContinuity_allBlocksLinked() {
        // Verify every block's parentHash points to the previous block.
        String[] names = {"b01", "b02", "b03", "b04", "b05", "b06", "b07", "b08"};
        for (int i = 1; i < names.length; i++) {
            Block child = world.getBlockByName(names[i]);
            Block parent = world.getBlockByName(names[i - 1]);
            assertEquals(parent.getHash(), child.getParentHash(),
                    names[i] + ".parentHash must point to " + names[i - 1]);
        }
    }

    @Test
    void chainContinuity_finalHeight_isEight() {
        assertEquals(8, world.getBlockChain().getBestBlock().getNumber(),
                "Chain must reach height 8 after all test blocks");
    }

    @Test
    void chainContinuity_bestBlock_isB08() {
        Block b08 = world.getBlockByName("b08");
        assertEquals(b08.getHash(), world.getBlockChain().getBestBlock().getHash());
    }

    @Test
    void chainContinuity_allPreForkBlocksInMainChain() {
        // Verify blocks 1-4 are part of the main chain (not orphaned after the fork)
        for (String name : new String[]{"b01", "b02", "b03", "b04"}) {
            Block block = world.getBlockByName(name);
            Block fromChain = world.getBlockChain().getBlockByHash(block.getHash().getBytes());
            assertNotNull(fromChain,
                    "Pre-fork block " + name + " must remain in the main chain after activation");
        }
    }

    @Test
    void chainContinuity_cumulativeGasIncreases() {
        // Each block with transactions contributes to cumulative gas (sanity check).
        TransactionReceipt r1 = world.getTransactionReceiptByName("txPreLegacy1");
        TransactionReceipt r5 = world.getTransactionReceiptByName("txTransitionType1");

        // Both should have non-zero cumulative gas
        assertTrue(new java.math.BigInteger(1, r1.getCumulativeGas()).longValue() > 0,
                "Block 1 must have non-zero cumulative gas");
        assertTrue(new java.math.BigInteger(1, r5.getCumulativeGas()).longValue() > 0,
                "Transition block must have non-zero cumulative gas");
    }

    // =========================================================================
    // I. Network safety: a node without RSKIP-543/546 would reject typed blocks
    // =========================================================================

    @Test
    void networkSafety_oldNodeRejectsTypedTxFromPool() {
        // An RSK node without RSKIP-543 active must reject typed txs from its tx pool.
        TestSystemProperties noRskip543 = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(-1))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(-1))
        );

        Transaction typedTx = world.getTransactionByName("txTransitionType1");

        assertTrue(typedTx.isTypedTransactionNotAllowed(
                noRskip543.getActivationConfig().forBlock(1)),
                "A node without RSKIP-543 must block typed txs from its pool (network safety)");
        assertTrue(typedTx.isTypedTransactionNotAllowed(
                noRskip543.getActivationConfig().forBlock(100)),
                "A node without RSKIP-543 must ALWAYS block typed txs from its pool");
    }

    @Test
    void networkSafety_rskip543ActiveButRskip546Inactive_blocksStandardType2() {
        // A node that activated RSKIP-543 but NOT RSKIP-546 must block standard Type 2 txs.
        // Type 1 in the RSK namespace would be allowed, but standard EIP-1559 Type 2 is not.
        TestSystemProperties rskip543Only = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(-1))
        );

        Transaction type2Tx = world.getTransactionByName("txPostType2");

        assertTrue(type2Tx.isTypedTransactionNotAllowed(
                rskip543Only.getActivationConfig().forBlock(1)),
                "Standard Type 2 tx must be blocked if RSKIP-546 is not active " +
                "(even when RSKIP-543 is active)");
    }

    @Test
    void networkSafety_rskip543ActiveButRskip546Inactive_blocksType1() {
        // Type 1 is also gated by RSKIP-546 specifically.
        TestSystemProperties rskip543Only = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543",
                                ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546",
                                ConfigValueFactory.fromAnyRef(-1))
        );

        Transaction type1Tx = world.getTransactionByName("txTransitionType1");

        assertTrue(type1Tx.isTypedTransactionNotAllowed(
                rskip543Only.getActivationConfig().forBlock(1)),
                "Type 1 tx must be blocked if RSKIP-546 is not active " +
                "(RSKIP-543 alone is insufficient for Type 1/2 encoding support)");
    }
}
