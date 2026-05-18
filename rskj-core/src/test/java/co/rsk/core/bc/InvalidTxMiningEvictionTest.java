/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.trie.TrieStore;
import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.db.MutableRepository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Functional integration tests for the mining safety net against transactions
 * that throw unexpected exceptions during execution.
 *
 * Uses real in-memory blockchain components with a spy TransactionExecutorFactory
 * that simulates an exception during execution of a designated "invalid" transaction.
 * This approach is independent of any specific production bug
 *
 * Expected behaviour with the fix:
 * - executeForMining() must NOT propagate the exception
 * - The offending tx must appear in BlockResult.getInvalidTransactions()
 * - Other valid transactions in the same block must execute normally
 */
class InvalidTxMiningEvictionTest {

    @TempDir
    public Path tempDir;

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig activationConfig = spy(config.getActivationConfig());
    // Shared reference so the spy factory can identify which tx to intercept.
    // Set before building the block in each test.
    private final Transaction[] invalidTxRef = new Transaction[1];

    private RskTestFactory objects;
    private Blockchain blockchain;
    private RepositorySnapshot repository;
    private BlockExecutor blockExecutor;

    @BeforeEach
    void setUp() {
        // RSKIP126 and RSKIP153 are both active at block 0 in regtest by default.
        // Spy ensures they are treated as active regardless of block number.
        doReturn(true).when(activationConfig).isActive(eq(ConsensusRule.RSKIP126), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(ConsensusRule.RSKIP153), anyLong());

        objects = new RskTestFactory(tempDir, config);
        blockchain = objects.getBlockchain();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
        blockExecutor = buildBlockExecutor(objects.getTrieStore());
    }

    /**
     * A block containing only an exception-throwing tx must not throw,
     * must not return INTERRUPTED_EXECUTION_BLOCK_RESULT, and must expose the tx
     * in getInvalidTransactions().
     */
    @Test
    void executeForMining_withExceptionThrowingTx_shouldNotThrowAndReturnValidResult() {
        Account sender = BlockExecutorTest.createAccount("sender1");
        Account receiver = BlockExecutorTest.createAccount("receiver1");
        Repository track = repository.startTracking();
        track.createAccount(sender.getAddress());
        track.addBalance(sender.getAddress(), Coin.valueOf(200_000));
        track.commit();
        blockchain.getBestBlock().setStateRoot(repository.getRoot());

        Transaction invalidTx = buildTransfer(sender, receiver, BigInteger.ZERO);
        invalidTxRef[0] = invalidTx;

        Block parent = blockchain.getBestBlock();
        Block block = buildBlockWithTxs(parent, List.of(invalidTx));

        BlockResult result = Assertions.assertDoesNotThrow(
                () -> blockExecutor.executeForMining(block, parent.getHeader(), true, false, false),
                "executeForMining must not propagate the RuntimeException from the failing tx"
        );

        Assertions.assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result,
                "Mining must not be interrupted by a tx that throws an unexpected exception");

        Assertions.assertEquals(1, result.getInvalidTransactions().size(),
                "The exception-throwing tx should be tracked as invalid");
        Assertions.assertEquals(invalidTx.getHash(), result.getInvalidTransactions().get(0).getHash());

        Assertions.assertFalse(
                result.getExecutedTransactions().stream().anyMatch(t -> t.getHash().equals(invalidTx.getHash())),
                "The invalid tx must not appear among executed transactions");
    }

    /**
     * When an exception-throwing tx is followed by a valid value-transfer tx,
     * the exception is absorbed, the throwing tx is flagged as invalid, and the
     * following tx executes normally with its state changes applied.
     *
     * Note: separate senders are used because the invalid tx is rolled back (its
     * nonce is not consumed), so a second tx from the same sender at nonce 1 would
     * fail the nonce check and also be discarded.
     */
    @Test
    void executeForMining_withExceptionThrowingTxFollowedByValidTx_shouldExecuteValidTxNormally() {
        Account invalidSender = BlockExecutorTest.createAccount("invalid-sender");
        Account validSender = BlockExecutorTest.createAccount("valid-sender");
        Account recipient = BlockExecutorTest.createAccount("recipient2");

        Repository track = repository.startTracking();
        track.createAccount(invalidSender.getAddress());
        track.addBalance(invalidSender.getAddress(), Coin.valueOf(200_000));
        track.createAccount(validSender.getAddress());
        track.addBalance(validSender.getAddress(), Coin.valueOf(200_000));
        track.createAccount(recipient.getAddress());
        track.commit();
        blockchain.getBestBlock().setStateRoot(repository.getRoot());

        Transaction invalidTx = buildTransfer(invalidSender, recipient, BigInteger.ZERO);
        invalidTxRef[0] = invalidTx;

        Transaction validTx = buildTransfer(validSender, recipient, BigInteger.ZERO);

        Block parent = blockchain.getBestBlock();
        Block block = buildBlockWithTxs(parent, Arrays.asList(invalidTx, validTx));

        BlockResult result = Assertions.assertDoesNotThrow(
                () -> blockExecutor.executeForMining(block, parent.getHeader(), true, false, false)
        );

        Assertions.assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);

        // The exception-throwing tx is flagged as invalid
        Assertions.assertEquals(1, result.getInvalidTransactions().size());
        Assertions.assertEquals(invalidTx.getHash(), result.getInvalidTransactions().get(0).getHash());

        // The valid tx was executed and appears in the executed list
        Assertions.assertTrue(
                result.getExecutedTransactions().stream().anyMatch(t -> t.getHash().equals(validTx.getHash())),
                "The valid tx must have been executed despite the preceding exception");

        // The state reflects the valid tx: recipient balance must be updated
        RepositorySnapshot finalState = new MutableRepository(objects.getTrieStore(), result.getFinalState());
        Assertions.assertEquals(Coin.valueOf(100), finalState.getBalance(recipient.getAddress()),
                "Recipient balance should reflect the value transferred by the valid tx");
    }

    // ---- helpers ----

    private Transaction buildTransfer(Account sender, Account recipient, BigInteger nonce) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(recipient.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.valueOf(100))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private Block buildBlockWithTxs(Block parent, List<Transaction> txs) {
        return new BlockGenerator(Constants.regtest(), activationConfig)
                .createChildBlock(parent, txs, new ArrayList<>(), 1, null);
    }

    private BlockExecutor buildBlockExecutor(TrieStore store) {
        TestSystemProperties cfg = spy(config);
        doReturn(activationConfig).when(cfg).getActivationConfig();

        StateRootHandler stateRootHandler = new StateRootHandler(
                cfg.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                cfg.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, cfg.getNetworkConstants().getBridgeConstants(),
                cfg.getActivationConfig(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        TransactionExecutorFactory realFactory = new TransactionExecutorFactory(
                cfg,
                null,
                null,
                new BlockFactory(activationConfig),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(cfg, bridgeSupportFactory, signatureCache),
                signatureCache);

        TransactionExecutorFactory spyFactory = spy(realFactory);

        // For the designated invalid tx, return a mock executor that throws on executeTransaction().
        // All other txs use the real factory and executor.
        // Note: the thrown exception is caught by the try-catch in executeForMiningAfterRSKIP144,
        // which then does `continue` (no further executor methods are called), so no additional
        // stubbing is needed on the mock executor.
        doAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            if (invalidTxRef[0] != null && tx.getHash().equals(invalidTxRef[0].getHash())) {
                TransactionExecutor throwingExecutor = mock(TransactionExecutor.class);
                doThrow(new RuntimeException("Simulated exception for testing"))
                        .when(throwingExecutor).executeTransaction();
                return throwingExecutor;
            }
            return inv.callRealMethod();
        }).when(spyFactory).newInstance(
                any(), anyInt(), any(), any(), any(),
                anyLong(), anyBoolean(), anyInt(), any(), anyBoolean(), anyLong()
        );

        return new BlockExecutor(
                new RepositoryLocator(store, stateRootHandler),
                spyFactory,
                cfg);
    }
}
