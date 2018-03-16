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

package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockStore;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImportResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtilsTest {
    @Test
    public void blockInSomeBlockChain() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();

        Block genesis = new BlockGenerator().getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Block block1 = new BlockBuilder().parent(genesis).build();
        Block block1b = new BlockBuilder().parent(genesis).build();
        Block block2 = new BlockBuilder().parent(block1).build();
        Block block3 = new BlockBuilder().parent(block2).build();
        blockChain.getBlockStore().saveBlock(block3, new BlockDifficulty(BigInteger.ONE), false);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        blockChain.tryToConnect(block1);
        blockChain.tryToConnect(block1b);

        Assert.assertTrue(BlockUtils.blockInSomeBlockChain(genesis, blockChain));
        Assert.assertTrue(BlockUtils.blockInSomeBlockChain(block1, blockChain));
        Assert.assertTrue(BlockUtils.blockInSomeBlockChain(block1b, blockChain));
        Assert.assertFalse(BlockUtils.blockInSomeBlockChain(block2, blockChain));
        Assert.assertTrue(BlockUtils.blockInSomeBlockChain(block3, blockChain));
    }

    @Test
    public void unknowAncestorsHashes() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        BlockStore store = new BlockStore();

        Block genesis = new BlockGenerator().getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Block block1 = new BlockBuilder().difficulty(2l).parent(genesis).build();
        Block block1b = new BlockBuilder().difficulty(1l).parent(genesis).build();
        Block block2 = new BlockBuilder().parent(block1).build();
        Block block3 = new BlockBuilder().parent(block2).build();

        store.saveBlock(block3);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        Set<Keccak256> hashes = BlockUtils.unknownAncestorsHashes(genesis.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1b.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block2.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertFalse(hashes.isEmpty());
        Assert.assertEquals(1, hashes.size());
        Assert.assertTrue(hashes.contains(block2.getHash()));

        hashes = BlockUtils.unknownAncestorsHashes(block3.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertFalse(hashes.isEmpty());
        Assert.assertEquals(1, hashes.size());
        Assert.assertTrue(hashes.contains(block2.getHash()));
    }

    @Test
    public void unknowAncestorsHashesUsingUncles() {
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        BlockGenerator blockGenerator = new BlockGenerator();
        Genesis genesis = blockGenerator.getGenesisBlock();
        BlockChainImpl blockChain = blockChainBuilder.setGenesis(genesis).build();
        BlockStore store = new BlockStore();

        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        BlockBuilder blockBuilder = new BlockBuilder(blockChain, blockGenerator);
        Block block1 = blockBuilder.parent(genesis).build();
        Block block1b = blockBuilder.parent(genesis).build();
        Block block2 = blockBuilder.parent(block1).build();
        Block uncle1 = blockBuilder.parent(block1).build();
        Block uncle2 = blockBuilder.parent(block1).build();
        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle1.getHeader());
        uncles.add(uncle2.getHeader());
        Block block3 = blockBuilder.parent(block2).uncles(uncles).build();

        store.saveBlock(block3);

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(block1);
        blockChain.tryToConnect(block1b);

        Set<Keccak256> hashes = BlockUtils.unknownAncestorsHashes(genesis.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block1b.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);

        Assert.assertTrue(hashes.isEmpty());

        hashes = BlockUtils.unknownAncestorsHashes(block2.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertFalse(hashes.isEmpty());
        Assert.assertEquals(1, hashes.size());
        Assert.assertTrue(hashes.contains(block2.getHash()));

        hashes = BlockUtils.unknownAncestorsHashes(block3.getHash(), blockChain, store);

        Assert.assertNotNull(hashes);
        Assert.assertFalse(hashes.isEmpty());
        Assert.assertEquals(3, hashes.size());
        Assert.assertTrue(hashes.contains(block2.getHash()));
        Assert.assertTrue(hashes.contains(uncle1.getHash()));
        Assert.assertTrue(hashes.contains(uncle2.getHash()));
    }
}
