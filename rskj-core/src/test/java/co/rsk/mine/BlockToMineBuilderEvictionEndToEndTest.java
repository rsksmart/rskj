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
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.db.RepositoryLocator;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionPool;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression test for the block-building path. It wires a <em>real</em>
 * {@link BlockToMineBuilder} around a <em>real</em> {@link BlockExecutor} whose
 * {@link TransactionExecutorFactory} produces an executor that throws a {@link Throwable}
 * (here an {@link OutOfMemoryError}) while executing a transaction.
 *
 * <p>This is the cross-layer counterpart of the unit tests in {@code BlockExecutorTest}: those
 * prove the executor catches the {@code Throwable}; this proves the failure flows correctly all
 * the way through to the miner. It asserts that {@code build()} does not crash, the offending tx
 * is evicted from the pending pool, and -modelling the reported "permanent mining halt"- that a
 * subsequent build produces a block normally instead of repeatedly failing on the same tx.
 *
 * <p>The failing transaction is simulated via a throwing executor rather than a real heap-busting
 * payload, so the test is fast and deterministic.
 */
class BlockToMineBuilderEvictionEndToEndTest {

    private final TestSystemProperties config = new TestSystemProperties();

    private BlockToMineBuilder blockBuilder;
    private TransactionPool transactionPool;
    private MinerUtils minerUtils;
    private Block genesis;
    private Transaction includedTx;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        RskTestFactory objects = new RskTestFactory(tempDir, config);
        Blockchain blockchain = objects.getBlockchain();
        genesis = blockchain.getBestBlock();

        ActivationConfig activationConfig = config.getActivationConfig();

        // A real BlockExecutor, but with a factory whose executor throws while executing a tx.
        TransactionExecutor throwingExecutor = mock(TransactionExecutor.class);
        when(throwingExecutor.executeTransaction())
                .thenThrow(new OutOfMemoryError("simulated error during tx execution"));

        TransactionExecutorFactory executorFactory = mock(TransactionExecutorFactory.class);
        // pre-RSKIP144 path (executeInternal) uses the 9-arg newInstance(...)
        when(executorFactory.newInstance(any(), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), any()))
                .thenReturn(throwingExecutor);
        // post-RSKIP144 path (executeForMiningAfterRSKIP144) uses the 11-arg newInstance(...)
        when(executorFactory.newInstance(any(), anyInt(), any(), any(), any(), anyLong(), anyBoolean(), anyInt(), any(), anyBoolean(), anyLong()))
                .thenReturn(throwingExecutor);

        RepositoryLocator repositoryLocator = objects.getRepositoryLocator();
        BlockExecutor blockExecutor = new BlockExecutor(repositoryLocator, executorFactory, config);

        MiningConfig miningConfig = mock(MiningConfig.class);
        when(miningConfig.getGasLimit()).thenReturn(new GasLimitConfig(3_000_000, 6_800_000, true));
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getUncleGenerationLimit()).thenReturn(7);
        when(miningConfig.getCoinbaseAddress()).thenReturn(TestUtils.generateAddress("coinbase"));

        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(BlockDifficulty.ONE);

        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(Coin.ZERO);

        MinerClock clock = mock(MinerClock.class);
        when(clock.calculateTimestampForChild(any())).thenReturn(genesis.getHeader().getTimestamp() + 1);

        BlockValidationRule validationRules = mock(BlockValidationRule.class);
        when(validationRules.isValid(any())).thenReturn(true);

        transactionPool = mock(TransactionPool.class);
        minerUtils = mock(MinerUtils.class);
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        blockBuilder = new BlockToMineBuilder(
                activationConfig,
                miningConfig,
                repositoryLocator,
                objects.getBlockStore(),
                transactionPool,
                difficultyCalculator,
                new GasLimitCalculator(Constants.regtest()),
                new ForkDetectionDataCalculator(RegTestParams.get()),
                validationRules,
                clock,
                new BlockFactory(activationConfig),
                blockExecutor,
                minimumGasPriceCalculator,
                minerUtils,
                signatureCache
        );

        // The included tx's execution is intercepted by the throwing factory, so it need not be funded.
        includedTx = buildSignedTransaction();
        when(minerUtils.getAllTransactions(any(), any(), any())).thenReturn(new ArrayList<>());
    }

    @Test
    void build_whenTxExecutionThrowsError_doesNotPropagateAndEvictsTxFromPool() {
        // given the pool serves the failing tx for this build.
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(includedTx)));

        List<BlockHeader> mainchainHeaders = Collections.singletonList(genesis.getHeader());

        // when
        BlockResult result = Assertions.assertDoesNotThrow(
                () -> blockBuilder.build(mainchainHeaders, new byte[0]));

        //then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getInvalidTransactions().contains(includedTx),
                "the transaction whose execution threw must be collected as invalid");

        // The builder must evict the offending transaction from the pending pool.
        verify(transactionPool).removeTransactions(argThat((List<Transaction> txs) -> txs.contains(includedTx)));
    }

    @Test
    void build_afterErrorTxEvicted_subsequentBuildProducesBlockAndIsNotRePoisoned() {
        // given the pool serves the failing tx on the first build; once evicted it no longer serves it.
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(includedTx)))
                .thenReturn(new ArrayList<>());

        List<BlockHeader> mainchainHeaders = Collections.singletonList(genesis.getHeader());

        // when
        // Build #1: the failing tx is evicted (the miner does not abort).
        BlockResult first = Assertions.assertDoesNotThrow(
                () -> blockBuilder.build(mainchainHeaders, new byte[0]));
        Assertions.assertTrue(first.getInvalidTransactions().contains(includedTx));
        verify(transactionPool).removeTransactions(argThat((List<Transaction> txs) -> txs.contains(includedTx)));

        // Build #2: with the tx gone from the pool the miner keeps producing - it is NOT permanent blocked by the invalid tx
        BlockResult second = Assertions.assertDoesNotThrow(
                () -> blockBuilder.build(mainchainHeaders, new byte[0]));
        Assertions.assertNotNull(second);
        Assertions.assertTrue(second.getInvalidTransactions().isEmpty(),
                "after eviction the miner must build cleanly and not re-encounter the failing tx");
    }

    private Transaction buildSignedTransaction() {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(TestUtils.generateAddress("destination").getBytes())
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.ZERO)
                .build();
        tx.sign(new ECKey().getPrivKeyBytes());
        return tx;
    }
}
