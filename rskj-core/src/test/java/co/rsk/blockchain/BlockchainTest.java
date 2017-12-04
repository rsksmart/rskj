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

package co.rsk.blockchain;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 4/20/2016.
 */
public class BlockchainTest {
    @Test
    public void genesisTest() {
        Block genesis = BlockGenerator.getInstance().getGenesisBlock();
        Assert.assertEquals(0, genesis.getNumber());
    }

    @Test
    public void blockchainTest() {
        Blockchain blockchain = createBlockchain();

        Assert.assertNotNull(blockchain);

        Block block = blockchain.getBestBlock();

        Assert.assertNotNull(block);
        Assert.assertEquals(0, block.getNumber());
    }

    @Test
    public void childBlock() {
        Blockchain blockchain = createBlockchain();

        Block block = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());

        Assert.assertNotNull(block);
        Assert.assertEquals(1, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlock().getHash(), block.getParentHash());
    }

    @Test
    public void addFirstBlock() {
        Blockchain blockchain = createBlockchain();

        Block block = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());

        blockchain.tryToConnect(block);
        Assert.assertEquals(blockchain.getBestBlock(), block);
    }

    @Test
    public void addTwoBlocks() {
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);

        blockchain.tryToConnect(block1);
        blockchain.tryToConnect(block2);

        Assert.assertEquals(blockchain.getBestBlock(), block2);
        Assert.assertEquals(2, block2.getNumber());
    }

    @Test
    public void tryToConnect() {
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1);

        Assert.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));

        Assert.assertEquals(blockchain.getBestBlock(), block2);
        Assert.assertEquals(2, block2.getNumber());
    }

    @Test
    public void tryToConnectWithCompetingChain() {
        // Two competing blockchains of the same size (2)
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1, 0, 5);
        Block block1b = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        Block block2b = BlockGenerator.getInstance().createChildBlock(block1b,0,4);
        // genesis <- block1 <- block2
        // genesis <- block1b <- block2b

        Assert.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block2b));

        Assert.assertEquals(blockchain.getBestBlock(), block2);
        Assert.assertEquals(2, block2.getNumber());
    }

    @Test
    public void tryToConnectWithFork() {
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock(),0,1);
        Block block1b = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock(),0,2);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1,0,3);
        Block block2b = BlockGenerator.getInstance().createChildBlock(block1b, 0, 1);
        Block block3b = BlockGenerator.getInstance().createChildBlock(block2b,0,4);

        Assert.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block2b));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block3b));

        Assert.assertEquals(blockchain.getBestBlock(), block3b);
        Assert.assertEquals(3, block3b.getNumber());
    }

    @Test
    public void connectOneChainThenConnectAnotherChain() {
        Blockchain blockchain = createBlockchain();
        final int height = 200;
        final long chain1Diff = 2;
        final long chain2Diff = 1;
        List<Block> chain1 = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), height, chain1Diff);
        List<Block> chain2 = BlockGenerator.getInstance().getBlockChain(blockchain.getBestBlock(), height, chain2Diff);

        for (Block b : chain1)
            Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(b));
        for (Block b : chain2)
            Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(b));

        Block newblock = BlockGenerator.getInstance().createChildBlock(chain2.get(chain2.size() - 1), 0, 2*height*chain2Diff);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(newblock));

        Assert.assertEquals(blockchain.getBestBlock(), newblock);
        Assert.assertEquals(chain2.size() + 1, newblock.getNumber());
    }

    @Test
    public void checkItDoesntAddAnInvalidBlock() {
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        ImportResult importResult1 = blockchain.tryToConnect(block1);
        Assert.assertTrue(importResult1.isSuccessful());

        Block block2 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());
        Block block2b = BlockGenerator.getInstance().createBlock(10, 5);
        Block block3 = Block.fromValidData(block2.getHeader(), block2b.getTransactionsList(), block2b.getUncleList());
        ImportResult importResult2 = blockchain.tryToConnect(block3);
        Assert.assertFalse(importResult2.isSuccessful());
    }

    private static BlockChainImpl createBlockchain() {
        World world = new World();

        return world.getBlockChain();
    }
}
