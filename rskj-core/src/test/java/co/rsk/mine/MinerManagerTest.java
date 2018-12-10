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

import co.rsk.config.ConfigUtils;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskImpl;
import co.rsk.core.SnapshotManager;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class MinerManagerTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    private BlockChainImpl blockchain;
    private TransactionPool transactionPool;
    private Repository repository;
    private BlockStore blockStore;

    @Before
    public void setup() {
        RskTestFactory factory = new RskTestFactory();
        blockchain = factory.getBlockchain();
        transactionPool = factory.getTransactionPool();
        repository = factory.getRepository();
        blockStore = factory.getBlockStore();
    }

    @Test
    public void refreshWorkRunOnce() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        MinerClientImpl.RefreshWork refreshWork = minerClient.createRefreshWork();

        Assert.assertNotNull(refreshWork);
        try {
            minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
            refreshWork.run();
            Assert.assertTrue(minerClient.mineBlock());

            Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        } finally {
            refreshWork.cancel();
        }
    }

    @Test
    public void refreshWorkRunTwice() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        MinerClientImpl.RefreshWork refreshWork = minerClient.createRefreshWork();

        Assert.assertNotNull(refreshWork);
        try {
            minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
            refreshWork.run();

            Assert.assertTrue(minerClient.mineBlock());

            minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
            refreshWork.run();
            Assert.assertTrue(minerClient.mineBlock());

            Assert.assertEquals(2, blockchain.getBestBlock().getNumber());
        } finally {
            refreshWork.cancel();
        }
    }

    @Test
    public void mineBlockTwiceReusingTheSameWork() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        MinerWork minerWork = minerServer.getWork();

        Assert.assertNotNull(minerWork);

        Assert.assertTrue(minerClient.mineBlock());

        Block bestBlock = blockchain.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(1, bestBlock.getNumber());

        // reuse the same work
        Assert.assertNotNull(minerServer.getWork());

        Assert.assertTrue(minerClient.mineBlock());

        List<Block> blocks = blockchain.getBlocksByNumber(1);

        Assert.assertNotNull(blocks);
        Assert.assertEquals(2, blocks.size());
        Assert.assertFalse(blocks.get(0).getHash().equals(blocks.get(1).getHash()));
    }

    @Test
    public void mineBlockWhileSyncingBlocks() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        RskImplForTest rsk = new RskImplForTest() {
            @Override
            public boolean hasBetterBlockToSync() {
                return true;
            }
        };
        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(rsk, minerServer);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        Assert.assertFalse(minerClient.mineBlock());

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());
    }

    @Test
    public void mineBlockWhilePlayingBlocks() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        RskImplForTest rsk = new RskImplForTest() {
            @Override
            public boolean hasBetterBlockToSync() {
                return false;
            }

            @Override
            public boolean isPlayingBlocks() {
                return true;
            }
        };
        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(rsk, minerServer);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        Assert.assertFalse(minerClient.mineBlock());

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());
    }

    @Test
    public void doWork() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        minerClient.doWork();

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
    }

    @Test
    public void doWorkEvenWithoutMinerServer() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(null);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        minerClient.doWork();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());
    }

    @Test
    public void doWorkInThread() throws Exception {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        Thread thread = minerClient.createDoWorkThread();
        thread.start();
        try {

            Awaitility.await().timeout(Duration.FIVE_SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return minerClient.isMining();
                }
            });

            Assert.assertTrue(minerClient.isMining());
        } finally {
            thread.interrupt(); // enought ?
            minerClient.stop();
        }
    }

    @Test
    public void mineBlock() {
        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        MinerManager manager = new MinerManager();

        MinerServerImpl minerServer = getMinerServer();
        MinerClientImpl minerClient = getMinerClient(minerServer);

        manager.mineBlock(blockchain, minerClient, minerServer);

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertFalse(blockchain.getBestBlock().getTransactionsList().isEmpty());

        SnapshotManager snapshotManager = new SnapshotManager(blockchain, transactionPool, minerServer);
        snapshotManager.resetSnapshots();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        manager.mineBlock(blockchain, minerClient, minerServer);
        manager.mineBlock(blockchain, minerClient, minerServer);
        Assert.assertEquals(2, blockchain.getBestBlock().getNumber());

        snapshotManager.resetSnapshots();
        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());

        manager.mineBlock(blockchain, minerClient, minerServer);

        Assert.assertTrue(transactionPool.getPendingTransactions().isEmpty());
    }

    private static MinerClientImpl getMinerClient(MinerServerImpl minerServer) {
        return getMinerClient(new RskImplForTest() {
            @Override
            public boolean hasBetterBlockToSync() {
                return false;
            }

            @Override
            public boolean isPlayingBlocks() {
                return false;
            }
        }, minerServer);
    }

    private static MinerClientImpl getMinerClient(RskImplForTest rsk, MinerServerImpl minerServer) {
        return new MinerClientImpl(rsk, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes());
    }

    private MinerServerImpl getMinerServer() {
        SimpleEthereum ethereum = new SimpleEthereum();
        ethereum.repository = repository;
        ethereum.blockchain = blockchain;
        DifficultyCalculator difficultyCalculator = new DifficultyCalculator(config);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        return new MinerServerImpl(
                config,
                ethereum,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config),
                        new BlockValidationRuleDummy(),
                        config,
                        null,
                        clock
                ),
                clock,
                ConfigUtils.getDefaultMiningConfig()
        );
    }

    public static class BlockValidationRuleDummy implements BlockValidationRule {
        @Override
        public boolean isValid(Block block) {
            return true;
        }
    }

    private static class RskImplForTest extends RskImpl {
        public RskImplForTest() {
            super(null, null, new TestCompositeEthereumListener(), null, null);
        }
    }
}
