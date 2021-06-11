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

package org.ethereum.rpc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.ConfigUtils;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.SnapshotManager;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.mine.*;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.evm.EvmModuleImpl;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class Web3ImplSnapshotTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    private RskTestFactory factory;
    private Blockchain blockchain;
    private MiningMainchainView mainchainView;
    private BlockFactory blockFactory;

    @Before
    public void setUp() {
        factory = new RskTestFactory(config);
        blockchain = factory.getBlockchain();
        mainchainView = factory.getMiningMainchainView();
        blockFactory = factory.getBlockFactory();
    }

    @Test
    public void takeFirstSnapshot() {
        Web3Impl web3 = createWeb3();

        String result = web3.evm_snapshot();

        Assert.assertNotNull(result);
        Assert.assertEquals("0x1", result);
    }

    @Test
    public void takeSecondSnapshot() {
        Web3Impl web3 = createWeb3();

        web3.evm_snapshot();
        String result = web3.evm_snapshot();

        Assert.assertNotNull(result);
        Assert.assertEquals("0x2", result);
    }

    @Test
    public void revertToSnapshot() {
        Web3Impl web3 = createWeb3();

        Blockchain blockchain = this.blockchain;
        addBlocks(blockchain, 10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        BlockChainStatus status = blockchain.getStatus();

        String snapshotId = web3.evm_snapshot();

        addBlocks(blockchain, 10);

        Assert.assertEquals(20, blockchain.getBestBlock().getNumber());

        Assert.assertTrue(web3.evm_revert(snapshotId));

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
    }

    @Test
    public void resetSnapshots() {
        Web3Impl web3 = createWeb3();
        Blockchain blockchain = this.blockchain;
        BlockChainStatus status = blockchain.getStatus();

        addBlocks(blockchain, 10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        web3.evm_reset();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
    }

    @Test
    public void revertToUnknownSnapshot() {
        Web3Impl web3 = createWeb3();

        Assert.assertFalse(web3.evm_revert("0x2a"));
    }

    @Test
    public void mine() {
        SimpleEthereum ethereum = new SimpleEthereum();
        Web3Impl web3 = createWeb3(ethereum);

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        web3.evm_mine();

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
    }


    private Web3Impl createWeb3(SimpleEthereum ethereum) {
        MinerClock minerClock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = getMinerServerForTest(ethereum, minerClock);
        MinerClientImpl minerClient = new MinerClientImpl(null, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes());
        EvmModule evmModule = new EvmModuleImpl(minerServer, minerClient, minerClock,
                                                new SnapshotManager(
                                                        blockchain,
                                                        factory.getBlockStore(),
                                                        factory.getTransactionPool(),
                                                        minerServer
                                                )
        );
        PersonalModule pm = new PersonalModuleWalletDisabled();
        TxPoolModule tpm = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        DebugModule dm = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);

        ethereum.blockchain = blockchain;

        return new Web3Impl(
                ethereum,
                blockchain,
                factory.getBlockStore(),
                factory.getReceiptStore(),
                Web3Mocks.getMockProperties(),
                minerClient,
                minerServer,
                pm,
                null,
                evmModule,
                tpm,
                null,
                dm,
                null, null,
                Web3Mocks.getMockChannelManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private Web3Impl createWeb3() {
        SimpleEthereum ethereum = new SimpleEthereum();
        return createWeb3(ethereum);
    }

    private MinerServer getMinerServerForTest(SimpleEthereum ethereum, MinerClock clock) {
        BlockValidationRule rule = new MinerManagerTest.BlockValidationRuleDummy();
        DifficultyCalculator difficultyCalculator = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());
        MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
        return new MinerServerImpl(
                config,
                ethereum,
                mainchainView,
                factory.getNodeBlockProcessor(),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        miningConfig,
                        factory.getRepositoryLocator(),
                        factory.getBlockStore(),
                        factory.getTransactionPool(),
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        new ForkDetectionDataCalculator(),
                        rule,
                        clock,
                        blockFactory,
                        factory.getBlockExecutor(),
                        new MinimumGasPriceCalculator(Coin.valueOf(miningConfig.getMinGasPriceTarget())),
                        new MinerUtils()
                ),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                miningConfig
        );
    }

    private static void addBlocks(Blockchain blockchain, int size) {
        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size);

        for (Block block : blocks) {
            blockchain.tryToConnect(block);
            blockchain.getBestBlock().getNumber();
        }
    }
}
