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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.WalletFactory;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.mine.MinerClientImpl;
import co.rsk.mine.MinerManagerTest;
import co.rsk.mine.MinerServer;
import co.rsk.mine.MinerServerImpl;
import co.rsk.test.World;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.facade.Ethereum;
import org.ethereum.manager.WorldManager;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.rpc.Simples.SimpleWorldManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class Web3ImplSnapshotTest {
    @Test
    public void takeFirstSnapshot() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        String result = web3.evm_snapshot();

        Assert.assertNotNull(result);
        Assert.assertEquals("0x1", result);
    }

    @Test
    public void takeSecondSnapshot() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        web3.evm_snapshot();
        String result = web3.evm_snapshot();

        Assert.assertNotNull(result);
        Assert.assertEquals("0x2", result);
    }

    @Test
    public void revertToSnapshot() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Blockchain blockchain = world.getBlockChain();
        addBlocks(blockchain, 10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        BlockChainStatus status = blockchain.getStatus();

        String snapshotId = web3.evm_snapshot();

        addBlocks(blockchain, 10);

        Assert.assertEquals(20, blockchain.getBestBlock().getNumber());

        Assert.assertTrue(web3.evm_revert(snapshotId));

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertArrayEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
    }

    @Test
    public void resetSnapshots() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);
        Blockchain blockchain = world.getBlockChain();
        BlockChainStatus status = blockchain.getStatus();

        addBlocks(blockchain, 10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        web3.evm_reset();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        BlockChainStatus newStatus = blockchain.getStatus();

        Assert.assertArrayEquals(status.getBestBlock().getHash(), newStatus.getBestBlock().getHash());
        Assert.assertEquals(status.getTotalDifficulty(), newStatus.getTotalDifficulty());
    }

    @Test
    public void revertToUnknownSnapshot() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Assert.assertFalse(web3.evm_revert("0x2a"));
    }

    @Test
    public void mine() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        Assert.assertEquals(0, world.getBlockChain().getBestBlock().getNumber());

        web3.evm_mine();

        Assert.assertEquals(1, world.getBlockChain().getBestBlock().getNumber());
    }

    @Test
    public void increaseTime() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        String result = web3.evm_increaseTime("0x10");

        Assert.assertEquals("0x10", result);
        Assert.assertEquals(16, minerServer.increaseTime(0));
    }

    @Test
    public void increaseTimeTwice() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        web3.evm_increaseTime("0x10");
        String result = web3.evm_increaseTime("0x10");

        Assert.assertEquals("0x20", result);
        Assert.assertEquals(32, minerServer.increaseTime(0));
    }

    private Web3Impl createWeb3(World world, SimpleEthereum ethereum, MinerServer minerServer) {
        MinerClientImpl minerClient = new MinerClientImpl();
        Web3Impl web3 = new Web3Impl(getMockEthereum(), getMockProperties(), WalletFactory.createWallet(), minerClient, minerServer);

        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        ethereum.repository = (org.ethereum.facade.Repository) world.getRepository();
        ethereum.worldManager = worldManager;

        BlockValidationRule rule = new MinerManagerTest.BlockValidationRuleDummy();
        minerClient.setMinerServer(minerServer);
        web3.worldManager = worldManager;
        return web3;
    }

    private Ethereum getMockEthereum() {
        WorldManager mockWorldManager = mock(WorldManager.class, RETURNS_DEEP_STUBS);
        when(mockWorldManager.getBlockchain().getBestBlock().getNumber()).thenReturn(0L);
        Ethereum ethMock = mock(Ethereum.class);
        when(ethMock.getWorldManager()).thenReturn(mockWorldManager);
        return ethMock;
    }

    private RskSystemProperties getMockProperties() {
        RskSystemProperties mockProperties = mock(RskSystemProperties.class);
        when(mockProperties.coinbaseSecret()).thenReturn("cow");
        return mockProperties;
    }

    private Web3Impl createWeb3(World world) {
        SimpleEthereum ethereum = new SimpleEthereum();
        return createWeb3(world, ethereum, getMinerServerForTest(world, ethereum));
    }

    private MinerServer getMinerServerForTest(World world, SimpleEthereum ethereum) {
        BlockValidationRule rule = new MinerManagerTest.BlockValidationRuleDummy();
        return new MinerServerImpl(ethereum, world.getBlockChain(), world.getBlockChain().getBlockStore(),
                world.getBlockChain().getPendingState(), world.getBlockChain().getRepository(), RskSystemProperties.CONFIG, rule);
    }

    private static void addBlocks(Blockchain blockchain, int size) {
        List<Block> blocks = BlockGenerator.getBlockChain(blockchain.getBestBlock(), size);

        for (Block block : blocks)
            blockchain.tryToConnect(block);
    }
}
