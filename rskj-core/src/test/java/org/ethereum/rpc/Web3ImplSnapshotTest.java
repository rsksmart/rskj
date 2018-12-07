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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.mine.*;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class Web3ImplSnapshotTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    private RskTestFactory factory;
    private BlockChainImpl blockchain;

    @Before
    public void setUp() {
        factory = new RskTestFactory();
        blockchain = factory.getBlockchain();
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
        MinerServer minerServer = getMinerServerForTest(ethereum);
        Web3Impl web3 = createWeb3(ethereum, minerServer);

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        web3.evm_mine();

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
    }

    @Test
    public void increaseTimeUsingHexadecimalValue() {
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(ethereum);
        Web3Impl web3 = createWeb3(ethereum, minerServer);

        String result = web3.evm_increaseTime("0x10");

        Assert.assertEquals("0x10", result);
        Assert.assertEquals(16, minerServer.increaseTime(0));
    }

    @Test
    public void increaseTimeUsingDecimalValue() {
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(ethereum);
        Web3Impl web3 = createWeb3(ethereum, minerServer);

        String result = web3.evm_increaseTime("16");

        Assert.assertEquals("0x10", result);
        Assert.assertEquals(16, minerServer.increaseTime(0));
    }

    @Test
    public void increaseTimeTwice() {
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(ethereum);
        Web3Impl web3 = createWeb3(ethereum, minerServer);

        web3.evm_increaseTime("0x10");
        String result = web3.evm_increaseTime("0x10");

        Assert.assertEquals("0x20", result);
        Assert.assertEquals(32, minerServer.increaseTime(0));
    }

    @Test
    public void increaseTimeTwiceUsingDecimalValues() {
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(ethereum);
        Web3Impl web3 = createWeb3(ethereum, minerServer);

        web3.evm_increaseTime("16");
        String result = web3.evm_increaseTime("16");

        Assert.assertEquals("0x20", result);
        Assert.assertEquals(32, minerServer.increaseTime(0));
    }

    private Web3Impl createWeb3(SimpleEthereum ethereum, MinerServer minerServer) {
        MinerClientImpl minerClient = new MinerClientImpl(null, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes());
        PersonalModule pm = new PersonalModuleWalletDisabled();
        TxPoolModule tpm = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        DebugModule dm = new DebugModuleImpl(Web3Mocks.getMockMessageHandler());

        ethereum.repository = factory.getRepository();
        ethereum.blockchain = blockchain;

        return new Web3Impl(
                ethereum,
                blockchain,
                factory.getTransactionPool(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                Web3Mocks.getMockProperties(),
                minerClient,
                minerServer,
                pm,
                null,
                tpm,
                null,
                dm,
                Web3Mocks.getMockChannelManager(),
                ethereum.repository,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Web3Impl createWeb3() {
        SimpleEthereum ethereum = new SimpleEthereum();
        return createWeb3(ethereum, getMinerServerForTest(ethereum));
    }

    private MinerServer getMinerServerForTest(SimpleEthereum ethereum) {
        BlockValidationRule rule = new MinerManagerTest.BlockValidationRuleDummy();
        DifficultyCalculator difficultyCalculator = new DifficultyCalculator(config);
        return new MinerServerImpl(
                config,
                ethereum,
                blockchain,
                factory.getBlockProcessor(),
                difficultyCalculator,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        ConfigUtils.getDefaultMiningConfig(),
                        factory.getRepository(),
                        factory.getBlockStore(),
                        factory.getTransactionPool(),
                        difficultyCalculator,
                        new GasLimitCalculator(config),
                        rule,
                        config,
                        null
                ),
                ConfigUtils.getDefaultMiningConfig()
        );
    }

    private static void addBlocks(Blockchain blockchain, int size) {
        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size);

        for (Block block : blocks)
            blockchain.tryToConnect(block);
    }
}
