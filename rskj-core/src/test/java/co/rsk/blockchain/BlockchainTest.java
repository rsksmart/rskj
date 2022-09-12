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
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by ajlopez on 4/20/2016.
 */
class BlockchainTest {
    @Test
    void genesisTest() {
        Block genesis = new BlockGenerator().getGenesisBlock();
        Assertions.assertEquals(0, genesis.getNumber());
    }

    @Test
    void blockchainTest() {
        Blockchain blockchain = createBlockchain();

        Assertions.assertNotNull(blockchain);

        Block block = blockchain.getBestBlock();

        Assertions.assertNotNull(block);
        Assertions.assertEquals(0, block.getNumber());
    }

    @Test
    void childBlock() {
        Blockchain blockchain = createBlockchain();

        Block block = new BlockGenerator().createChildBlock(blockchain.getBestBlock());

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getNumber());
        Assertions.assertEquals(blockchain.getBestBlock().getHash(), block.getParentHash());
    }

    @Test
    void addFirstBlock() {
        Blockchain blockchain = createBlockchain();

        Block block = new BlockGenerator().createChildBlock(blockchain.getBestBlock());

        blockchain.tryToConnect(block);
        Assertions.assertEquals(blockchain.getBestBlock(), block);
    }

    @Test
    void addTwoBlocks() {
        Blockchain blockchain = createBlockchain();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2 = blockGenerator.createChildBlock(block1);

        blockchain.tryToConnect(block1);
        blockchain.tryToConnect(block2);

        Assertions.assertEquals(blockchain.getBestBlock(), block2);
        Assertions.assertEquals(2, block2.getNumber());
    }

    @Test
    void tryToConnect() {
        Blockchain blockchain = createBlockchain();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2 = blockGenerator.createChildBlock(block1);

        Assertions.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));

        Assertions.assertEquals(blockchain.getBestBlock(), block2);
        Assertions.assertEquals(2, block2.getNumber());
    }

    @Test
    void tryToConnectWithCompetingChain() {
        // Two competing blockchains of the same size (2)
        Blockchain blockchain = createBlockchain();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2 = blockGenerator.createChildBlock(block1, 0, 5);
        Block block1b = blockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2b = blockGenerator.createChildBlock(block1b,0,4);
        // genesis <- block1 <- block2
        // genesis <- block1b <- block2b

        Assertions.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block1b));
        Assertions.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1b));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block2b));

        Assertions.assertEquals(blockchain.getBestBlock(), block2);
        Assertions.assertEquals(2, block2.getNumber());
    }

    @Test
    void tryToConnectWithFork() {
        Blockchain blockchain = createBlockchain();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock(),0,1);
        Block block1b = blockGenerator.createChildBlock(blockchain.getBestBlock(),0,2);
        Block block2 = blockGenerator.createChildBlock(block1,0,3);
        Block block2b = blockGenerator.createChildBlock(block1b, 0, 1);
        Block block3b = blockGenerator.createChildBlock(block2b,0,4);

        Assertions.assertEquals(ImportResult.NO_PARENT, blockchain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block1b));
        Assertions.assertEquals(ImportResult.EXIST, blockchain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block2b));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block3b));

        Assertions.assertEquals(blockchain.getBestBlock(), block3b);
        Assertions.assertEquals(3, block3b.getNumber());
    }

    @Test
    void connectOneChainThenConnectAnotherChain() {
        Blockchain blockchain = createBlockchain();
        final int height = 200;
        final long chain1Diff = 2;
        final long chain2Diff = 1;
        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> chain1 = blockGenerator.getBlockChain(blockchain.getBestBlock(), height, chain1Diff);
        List<Block> chain2 = blockGenerator.getBlockChain(blockchain.getBestBlock(), height, chain2Diff);
        int i = 0;
        for (Block b : chain1)
            Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(b), "Block "+ i++ + " Not Equal.");
        for (Block b : chain2)
            Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(b));

        Block newblock = blockGenerator.createChildBlock(chain2.get(chain2.size() - 1), 0, 2*height*chain2Diff);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(newblock));

        Assertions.assertEquals(blockchain.getBestBlock(), newblock);
        Assertions.assertEquals(chain2.size() + 1, newblock.getNumber());
    }

    @Test
    void checkItDoesntAddAnInvalidBlock() {
        Blockchain blockchain = createBlockchain();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock());
        ImportResult importResult1 = blockchain.tryToConnect(block1);
        assertTrue(importResult1.isSuccessful());

        Block block2 = blockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2b = blockGenerator.createBlock(10, 5);

        BlockHeader header = block2.getHeader();
        List<Transaction> transactionsList = block2b.getTransactionsList();
        List<BlockHeader> uncleList = block2b.getUncleList();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Block(header, transactionsList, uncleList, true, true));
    }


    private static BlockChainImpl createBlockchain() {
        World world = new World();

        return world.getBlockChain();
    }
}
