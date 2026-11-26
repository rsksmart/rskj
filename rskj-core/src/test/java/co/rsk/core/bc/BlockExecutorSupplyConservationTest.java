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
package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.supply.SupplyBug;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieStore;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Supply conservation end to end: a block that creates native currency is rejected, and a block
 * that does not is untouched.
 *
 * <p>Real blocks are not expected to create rBTC, so the only way to exercise the rejection path is
 * to introduce a bug deliberately. {@code --add-supply-bug} does exactly that, and these tests turn
 * it on to prove the check fires -- and leave it off to prove it does not fire otherwise.
 */
class BlockExecutorSupplyConservationTest {

    private static final boolean RSKIP_126_IS_ACTIVE = true;

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig activationConfig = spy(config.getActivationConfig());
    private final BlockFactory blockFactory = new BlockFactory(activationConfig);

    @TempDir
    public Path tempDir;

    private Blockchain blockchain;
    private TrieStore trieStore;
    private RepositorySnapshot repository;

    @BeforeEach
    void setUp() {
        RskTestFactory objects = new RskTestFactory(tempDir, config);
        blockchain = objects.getBlockchain();
        trieStore = objects.getTrieStore();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
    }

    // ---------------------------------------------------------------------------------------
    // The check does not fire on ordinary blocks
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void anOrdinaryTransferIsAccepted(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();
        BlockExecutor executor = buildBlockExecutor(false, activeRskip144);

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);

        assertNotSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);
        assertEquals(1, result.getTransactionReceipts().size());
    }

    /**
     * An empty block still runs the block scope, over whatever the precompiled-contract setup
     * touched. None of that moves value, so it must not be reported.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aBlockWithNoTransactionsIsAccepted(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block parent = blockchain.getBestBlock();
        Block block = new co.rsk.blockchain.utils.BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(parent);
        BlockExecutor executor = buildBlockExecutor(false, activeRskip144);

        BlockResult result = executor.executeForMining(block, parent.getHeader(), false, false, true);

        assertNotSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);
        assertTrue(result.getTransactionReceipts().isEmpty());
    }

    /**
     * Fees must not appear as burns. Gas is debited from the sender and credited to the fee
     * recipient, so an ordinary transaction conserves supply even though the sender ends up poorer.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void feePayingTransactionsDoNotLookLikeBurns(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();
        BlockExecutor executor = buildBlockExecutor(false, activeRskip144);

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);

        // The transaction paid a non-zero fee, so this really did exercise the fee path.
        assertTrue(result.getPaidFees().compareTo(Coin.ZERO) > 0, "expected the transaction to pay a fee");
        assertNotSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);
    }

    // ---------------------------------------------------------------------------------------
    // The check fires when currency is created
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aBlockThatCreatesCurrencyIsRejected(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();
        BlockExecutor executor = buildBlockExecutor(true, activeRskip144);

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);

        assertSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result,
                "a block that creates rBTC must be rejected");
    }

    /**
     * A supply violation makes validation fail on its own account, not incidentally.
     *
     * <p>Asserted against the sentinel directly: a block that actually minted would also fail state
     * root validation, so running one end to end would not show which rule rejected it.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aSupplyViolationResultDoesNotValidate(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        BlockExecutor executor = buildBlockExecutor(false, activeRskip144);

        assertFalse(executor.validate(block, BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aRejectedBlockDoesNotValidate(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();
        BlockExecutor executor = buildBlockExecutor(true, activeRskip144);

        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);

        assertSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);
        assertFalse(executor.validate(block, result));
    }

    /**
     * Rejection must happen before the block's state changes are persisted, so a rejected block
     * leaves no trace in the state database.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aRejectedBlockLeavesNoTraceInTheState(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();

        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
        RepositoryLocator locator = new RepositoryLocator(trieStore, stateRootHandler);

        byte[] rootBefore = locator.snapshotAt(parent.getHeader()).getRoot();

        BlockExecutor executor = buildBlockExecutor(true, activeRskip144, locator);
        BlockResult result = executor.execute(null, 0, block, parent.getHeader(), false, false, true);
        assertSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);

        RepositorySnapshot afterRejection = locator.snapshotAt(parent.getHeader());

        org.junit.jupiter.api.Assertions.assertArrayEquals(rootBefore, afterRejection.getRoot(),
                "the parent state must be unchanged by a rejected block");
        assertEquals(Coin.ZERO, afterRejection.getBalance(SupplyBug.BENEFICIARY),
                "the conjured currency must not be visible in the state");
    }

    // ---------------------------------------------------------------------------------------
    // The bug is inert unless it was asked for
    // ---------------------------------------------------------------------------------------

    /**
     * The same block, executed by an executor built from the default configuration, is accepted.
     * Nothing but the flag distinguishes this case from the rejection above.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void withoutTheFlagTheSameBlockIsAccepted(boolean activeRskip144) {
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());

        Block block = blockWithOneTransfer();
        Block parent = blockchain.getBestBlock();

        BlockResult result = buildBlockExecutor(false, activeRskip144)
                .execute(null, 0, block, parent.getHeader(), false, false, true);

        assertNotSame(BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT, result);
    }

    @Test
    void theBugIsOffInTheDefaultConfiguration() {
        assertFalse(new TestSystemProperties().isSupplyBugEnabled());
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private static Account createAccount(String seed, Repository repository, Coin balance) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        Account account = new Account(ECKey.fromPrivate(privateKeyBytes));
        repository.createAccount(account.getAddress());
        repository.addBalance(account.getAddress(), balance);
        return account;
    }

    /** A block carrying a single plain value transfer between two funded accounts. */
    private Block blockWithOneTransfer() {
        Repository track = repository.startTracking();
        Account sender = createAccount("supply-sender", track, Coin.valueOf(30000));
        Account receiver = createAccount("supply-receiver", track, Coin.valueOf(10L));
        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        Transaction tx = Transaction.builder()
                .nonce(repository.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(receiver.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        List<Transaction> txs = Collections.singletonList(tx);
        List<BlockHeader> uncles = new ArrayList<>();

        return new co.rsk.blockchain.utils.BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private BlockExecutor buildBlockExecutor(boolean withSupplyBug, boolean activeRskip144) {
        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
        return buildBlockExecutor(withSupplyBug, activeRskip144, new RepositoryLocator(trieStore, stateRootHandler));
    }

    private BlockExecutor buildBlockExecutor(boolean withSupplyBug, boolean activeRskip144, RepositoryLocator locator) {
        RskSystemProperties cfg = spy(config);
        doReturn(activationConfig).when(cfg).getActivationConfig();
        doReturn(activeRskip144).when(activationConfig).isActive(eq(ConsensusRule.RSKIP144), anyLong());
        doReturn(RSKIP_126_IS_ACTIVE).when(activationConfig).isActive(eq(ConsensusRule.RSKIP126), anyLong());
        doReturn(withSupplyBug).when(cfg).isSupplyBugEnabled();

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                cfg.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, cfg.getNetworkConstants().getBridgeConstants(), cfg.getActivationConfig(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        return new BlockExecutor(
                locator,
                new TransactionExecutorFactory(
                        cfg,
                        null,
                        null,
                        blockFactory,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(cfg, bridgeSupportFactory, signatureCache),
                        signatureCache),
                cfg);
    }
}
