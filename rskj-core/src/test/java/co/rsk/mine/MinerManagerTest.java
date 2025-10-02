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
package co.rsk.mine;

import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ConfigUtils;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.SnapshotManager;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.db.RepositoryLocator;
import co.rsk.mine.gas.provider.FixedMinGasPriceProvider;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.awaitility.Awaitility;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 15/04/2017.
 */
@SuppressWarnings("squid:S5786") // called from another test
public class MinerManagerTest {

    @TempDir
    public Path tempDir;

    private static final TestSystemProperties config = new TestSystemProperties();
    private Blockchain blockchain;
    private MiningMainchainView miningMainchainView;
    private TransactionPool transactionPool;
    private RepositoryLocator repositoryLocator;
    private BlockStore blockStore;
    private BlockFactory blockFactory;
    private BlockExecutor blockExecutor;

    @BeforeEach
    void setup() {
        RskTestFactory factory = new RskTestFactory(tempDir, config);
        blockchain = factory.getBlockchain();
        miningMainchainView = factory.getMiningMainchainView();
        transactionPool = factory.getTransactionPool();
        repositoryLocator = factory.getRepositoryLocator();
        blockStore = factory.getBlockStore();
        blockFactory = factory.getBlockFactory();
        blockExecutor = factory.getBlockExecutor();
    }

    @Test
    void refreshWorkRunOnce() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        MinerClientImpl.RefreshWork refreshWork = minerClient.createRefreshWork();

        Assertions.assertNotNull(refreshWork);
        try {
            minerServer.buildBlockToMine(false);
            refreshWork.run();
            Assertions.assertTrue(minerClient.mineBlock());

            Assertions.assertEquals(1, blockchain.getBestBlock().getNumber());
        } finally {
            refreshWork.cancel();
        }
    }

    @Test
    void refreshWorkRunTwice() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        MinerClientImpl.RefreshWork refreshWork = minerClient.createRefreshWork();

        Assertions.assertNotNull(refreshWork);
        try {
            minerServer.buildBlockToMine( false);
            refreshWork.run();

            Assertions.assertTrue(minerClient.mineBlock());

            // miningMainchainView new best block update is done by a listener on miner server.
            // This test does not have that listener so add the new best block manually.
            miningMainchainView.addBest(blockchain.getBestBlock().getHeader());

            minerServer.buildBlockToMine( false);
            refreshWork.run();
            Assertions.assertTrue(minerClient.mineBlock());

            Assertions.assertEquals(2, blockchain.getBestBlock().getNumber());
        } finally {
            refreshWork.cancel();
        }
    }

    @Test
    void mineBlockTwiceReusingTheSameWork() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine( false);

        MinerWork minerWork = minerServer.getWork();

        Assertions.assertNotNull(minerWork);

        Assertions.assertTrue(minerClient.mineBlock());

        Block bestBlock = blockchain.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(1, bestBlock.getNumber());

        // reuse the same work
        Assertions.assertNotNull(minerServer.getWork());

        Assertions.assertTrue(minerClient.mineBlock());

        List<Block> blocks = blockchain.getBlocksByNumber(1);

        Assertions.assertNotNull(blocks);
        Assertions.assertEquals(2, blocks.size());
        Assertions.assertNotEquals(blocks.get(0).getHash(), blocks.get(1).getHash());
    }

    @Test
    void mineBlockWhileSyncingBlocks() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);
        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(nodeBlockProcessor, minerServer);

        minerServer.buildBlockToMine( false);

        Assertions.assertFalse(minerClient.mineBlock());

        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());
    }

    @Test
    void doWork() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine( false);
        minerClient.doWork();

        Assertions.assertEquals(1, blockchain.getBestBlock().getNumber());
    }

    @Test
    void doWorkEvenWithoutMinerServer() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(null);

        minerServer.buildBlockToMine(false);
        minerClient.doWork();

        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());
    }

    @Test
    void doWorkInThread() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine(false);
        Thread thread = minerClient.createDoWorkThread();
        thread.start();
        try {

            Awaitility.await().timeout(Duration.ofSeconds(5)).until(minerClient::isMining);

            Assertions.assertTrue(minerClient.isMining());
        } finally {
            thread.interrupt(); // enought ?
            minerClient.stop();
        }
    }

    @Test
    void mineBlock() {
        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerManager manager = new MinerManager();

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        manager.mineBlock(minerClient, minerServer);

        Assertions.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assertions.assertFalse(blockchain.getBestBlock().getTransactionsList().isEmpty());

        SnapshotManager snapshotManager = new SnapshotManager(blockchain, blockStore, transactionPool, minerServer);
        snapshotManager.resetSnapshots();

        Assertions.assertEquals(0, blockchain.getBestBlock().getNumber());

        manager.mineBlock(minerClient, minerServer);
        // miningMainchainView new best block update is done by a listener on miner server.
        // This test does not have that listener so add the new best block manually.
        miningMainchainView.addBest(blockchain.getBestBlock().getHeader());
        manager.mineBlock(minerClient, minerServer);
        Assertions.assertEquals(2, blockchain.getBestBlock().getNumber());

        snapshotManager.resetSnapshots();
        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        manager.mineBlock(minerClient, minerServer);

        Assertions.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    private static MinerClientImpl getMinerClient(MinerServerImpl minerServer) {
        return getMinerClient(null, minerServer);
    }

    private static MinerClientImpl getMinerClient(NodeBlockProcessor nodeBlockProcessor, MinerServerImpl minerServer) {
        return new MinerClientImpl(nodeBlockProcessor, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes(), config.getBitcoinjNetworkConstants());
    }

    private MinerServerImpl getMinerServer() {
        SimpleEthereum ethereum = new SimpleEthereum();
        ethereum.blockchain = blockchain;
        DifficultyCalculator difficultyCalculator = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
        return new MinerServerImpl(
                config,
                ethereum,
                miningMainchainView,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        miningConfig,
                        repositoryLocator,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        new ForkDetectionDataCalculator(RegTestParams.get()),
                        new BlockValidationRuleDummy(),
                        clock,
                        blockFactory,
                        blockExecutor,
                        new MinimumGasPriceCalculator(new FixedMinGasPriceProvider(config.minerMinGasPrice())),
                        new MinerUtils(),
                        new BlockTxSignatureCache(new ReceivedTxSignatureCache()) // TODO -> should it be ReceivedTxSignatureCache? See Miner Server Test for reference
                ),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                miningConfig
        );
    }

    public static class BlockValidationRuleDummy implements BlockValidationRule {
        @Override
        public boolean isValid(Block block) {
            return true;
        }
    }
}
