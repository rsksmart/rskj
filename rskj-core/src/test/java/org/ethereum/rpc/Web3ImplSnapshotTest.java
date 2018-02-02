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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.mine.*;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.test.World;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class Web3ImplSnapshotTest {

    private static final RskSystemProperties config = new RskSystemProperties();

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
    public void increaseTimeUsingHexadecimalValue() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        String result = web3.evm_increaseTime("0x10");

        Assert.assertEquals("0x10", result);
        Assert.assertEquals(16, minerServer.increaseTime(0));
    }

    @Test
    public void increaseTimeUsingDecimalValue() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        String result = web3.evm_increaseTime("16");

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

    @Test
    public void increaseTimeTwiceUsingDecimalValues() {
        World world = new World();
        SimpleEthereum ethereum = new SimpleEthereum();
        MinerServer minerServer = getMinerServerForTest(world, ethereum);
        Web3Impl web3 = createWeb3(world, ethereum, minerServer);

        web3.evm_increaseTime("16");
        String result = web3.evm_increaseTime("16");

        Assert.assertEquals("0x20", result);
        Assert.assertEquals(32, minerServer.increaseTime(0));
    }

    private static Web3Impl createWeb3(World world, SimpleEthereum ethereum, MinerServer minerServer) {
        MinerClientImpl minerClient = new MinerClientImpl(null, minerServer, config);
        PersonalModule pm = new PersonalModuleWalletDisabled();

        ethereum.repository = world.getRepository();
        ethereum.blockchain = world.getBlockChain();

        return new Web3Impl(ethereum, world.getBlockChain(), null, world.getBlockChain().getBlockStore(), Web3Mocks.getMockProperties(), minerClient, minerServer, pm, null, Web3Mocks.getMockChannelManager(), ethereum.repository, null, null, null, null, null);
    }

    private static Web3Impl createWeb3(World world) {
        SimpleEthereum ethereum = new SimpleEthereum();
        return createWeb3(world, ethereum, getMinerServerForTest(world, ethereum));
    }

    static MinerServer getMinerServerForTest(World world, SimpleEthereum ethereum) {
        BlockValidationRule rule = new MinerManagerTest.BlockValidationRuleDummy();
        return new MinerServerImpl(config, ethereum, world.getBlockChain(), world.getBlockChain().getBlockStore(),
                world.getBlockChain().getPendingState(), world.getBlockChain().getRepository(), ConfigUtils.getDefaultMiningConfig(), rule, world.getBlockProcessor(), new DifficultyCalculator(config), new GasLimitCalculator(config),
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false));
    }

    private static void addBlocks(Blockchain blockchain, int size) {
        List<Block> blocks = new BlockGenerator().getBlockChain(blockchain.getBestBlock(), size);

        for (Block block : blocks)
            blockchain.tryToConnect(block);
    }
}
