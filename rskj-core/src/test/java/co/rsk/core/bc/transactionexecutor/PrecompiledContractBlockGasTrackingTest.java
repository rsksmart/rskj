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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;

import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Verifies block gas tracking and the block logs bloom when a precompiled contract call fails.
 * Before RSKIP-692, underreported gas lets the next transaction execute.
 * After activation, full gas consumption prevents the next transaction from fitting.
 */
class PrecompiledContractBlockGasTrackingTest {

    /**
     * Block gas limit 250_000; tx1 is the case 1 Bridge call (gas limit 200_000), tx2 a value
     * transfer (gas limit 100_000). A transaction is dropped when the block gas used so far plus its gas limit exceeds
     * the block gas limit.
     * Before activation: tx1 reports 44_064, and 44_064 + 100_000 fits, so both are included. The transfer costs its
     * intrinsic 21_000, so the block gas used is 44_064 + 21_000 = 65_064.
     * After activation: tx1 reports 200_000, and 200_000 + 100_000 exceeds 250_000, so only tx1 is included and the
     * block gas used is 200_000.
     */
    // RSKIP-692 test case 2
    @ParameterizedTest(name = "RSKIP-692 active={0}, RSKIP-144 active={3}, expected executed transactions={1}")
    @CsvSource({
            "false, 2, 65064, false",
            "true, 1, 200000, false",
            "false, 2, 65064, true",
            "true, 1, 200000, true"
    })
    void failedPrecompileGasAffectsWhetherNextTransactionFitsInBlock(
            boolean rskip692Active, int expectedExecutedTransactions, long expectedBlockGasUsed, boolean rskip144Active) {
        TestSystemProperties baseConfig = new TestSystemProperties();

        ActivationConfig activationConfig = buildActivationConfig(baseConfig.getActivationConfig(), rskip692Active, rskip144Active);

        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        Repository track = repository.startTracking();

        Account sender = createAccount("acctest1", track, Coin.valueOf(10_000_000L));
        Account receiver = createAccount("acctest2", track, Coin.ZERO);
        track.commit();

        long blockGasLimit = 250_000L;
        long firstTransactionGasLimit = 200_000L;
        long secondTransactionGasLimit = 100_000L;

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock(blockGasLimit);
        genesis.setStateRoot(repository.getRoot());

        byte[] invalidBridgeCall = Hex.decode("deadbeef");

        Transaction tx1 = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(firstTransactionGasLimit))
                .receiveAddress(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(invalidBridgeCall)
                .build();

        tx1.sign(sender.getEcKey().getPrivKeyBytes());

        Transaction tx2 = Transaction.builder()
                .nonce(BigInteger.ONE)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(secondTransactionGasLimit))
                .receiveAddress(receiver.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.valueOf(1L))
                .build();

        tx2.sign(sender.getEcKey().getPrivKeyBytes());

        List<Transaction> transactions = Arrays.asList(tx1, tx2);

        Block block = blockGenerator.createChildBlock(genesis, transactions, new ArrayList<>(), 1, null);

        assertEquals(blockGasLimit, new BigInteger(1, block.getGasLimit()).longValueExact());

        BlockExecutor blockExecutor = buildBlockExecutor(config, trieStore, bridgePrecompiledContracts(config));
        BlockResult result = blockExecutor.executeAndFill(block, genesis.getHeader());

        assertEquals(expectedExecutedTransactions, result.getExecutedTransactions().size());
        assertEquals(tx1.getHash(), result.getExecutedTransactions().get(0).getHash());
        assertEquals(expectedBlockGasUsed, result.getGasUsed());
    }

    // RSKIP-692 test case 6, block logs bloom
    @ParameterizedTest(name = "RSKIP-692 active={0}")
    @ValueSource(booleans = {true, false})
    void logOfFailedPrecompileCallIsInBlockBloomOnlyBeforeActivation(boolean rskip692Active) {
        TestSystemProperties baseConfig = new TestSystemProperties();
        ActivationConfig activationConfig = buildActivationConfig(baseConfig.getActivationConfig(), rskip692Active, false);
        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        Repository track = repository.startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(10_000_000L));
        track.createAccount(FailingTestPrecompile.FAILING_PRECOMPILE_ADDR);
        track.initializeStorage(FailingTestPrecompile.FAILING_PRECOMPILE_ADDR);
        track.commit();

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(repository.getRoot());

        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(100_000L))
                .receiveAddress(FailingTestPrecompile.FAILING_PRECOMPILE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(new byte[]{FailingTestPrecompile.EMIT_LOG})
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block block = blockGenerator.createChildBlock(genesis, List.of(tx), new ArrayList<>(), 1, null);

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BlockExecutor blockExecutor = buildBlockExecutor(
                config, trieStore, new FailingTestPrecompile.PrecompiledContractsWithFailingContract(config, signatureCache));
        BlockResult result = blockExecutor.executeAndFill(block, genesis.getHeader());

        assertEquals(1, result.getExecutedTransactions().size());
        byte[] blockBloom = block.getHeader().getLogsBloom();
        if (rskip692Active) {
            assertArrayEquals(new byte[256], blockBloom);
        } else {
            assertFalse(Arrays.equals(new byte[256], blockBloom));
        }
    }

    private static ActivationConfig buildActivationConfig(
            ActivationConfig defaults,
            boolean rskip692Active,
            boolean rskip144Active
    ) {
        Map<ConsensusRule, Long> heights =
                new EnumMap<>(ConsensusRule.class);

        for (ConsensusRule rule : ConsensusRule.values()) {
            heights.put(
                    rule,
                    defaults.isActive(rule, 0L) ? 0L : -1L
            );
        }

        heights.put(ConsensusRule.RSKIP144, rskip144Active ? 0L : -1L);

        // Explicitly select the behavior under test.
        heights.put(
                ConsensusRule.RSKIP692,
                rskip692Active ? 0L : -1L
        );

        return new ActivationConfig(heights, new HashMap<>());
    }

    private static PrecompiledContracts bridgePrecompiledContracts(TestSystemProperties config) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory =
                new BridgeSupportFactory(
                        btcBlockStoreFactory,
                        config.getNetworkConstants().getBridgeConstants(),
                        config.getActivationConfig(),
                        signatureCache
                );

        return new PrecompiledContracts(
                config,
                bridgeSupportFactory,
                signatureCache
        );
    }

    private static BlockExecutor buildBlockExecutor(
            TestSystemProperties config, TrieStore trieStore, PrecompiledContracts precompiledContracts) {
        StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));

        RepositoryLocator repositoryLocator = new RepositoryLocator(trieStore, stateRootHandler);

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        TransactionExecutorFactory transactionExecutorFactory =
                new TransactionExecutorFactory(
                        config,
                        null,
                        null,
                        new BlockFactory(config.getActivationConfig()),
                        new ProgramInvokeFactoryImpl(),
                        precompiledContracts,
                        signatureCache
                );

        return new BlockExecutor(
                repositoryLocator,
                transactionExecutorFactory,
                config
        );
    }
}
