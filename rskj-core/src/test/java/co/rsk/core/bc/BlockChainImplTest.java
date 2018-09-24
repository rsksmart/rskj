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
import co.rsk.blocks.DummyBlockRecorder;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import org.bouncycastle.util.Arrays;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;
import org.mapdb.DB;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BlockChainImplTest {

    private static final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void addGenesisBlock() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(0, bestBlock.getNumber());
        Assert.assertEquals(genesis.getHash(), bestBlock.getHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty(), status.getTotalDifficulty());
    }

    @Test
    public void onBestBlockTest() {
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(listener);
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assert.assertEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertNotEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assert.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assert.assertEquals(block1b.getHash(), listener.getLatestBlock().getHash());
    }
    @Test
    public void addGenesisBlockUsingRepository() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(0, bestBlock.getNumber());
        Assert.assertEquals(genesis.getHash(), bestBlock.getHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty(), status.getTotalDifficulty());

        Repository repository = blockChain.getRepository();

        Assert.assertArrayEquals(genesis.getStateRoot(), repository.getRoot());

        Assert.assertEquals(new BigInteger("21000000000000000000000000"), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR).asBigInteger());
    }


    @Test
    public void setStatusUsingRskGenesis() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(0, bestBlock.getNumber());
        Assert.assertEquals(genesis.getHash(), bestBlock.getHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty(), status.getTotalDifficulty());

        Repository repository = blockChain.getRepository();

        Assert.assertArrayEquals(genesis.getStateRoot(), repository.getRoot());

        Assert.assertEquals(new BigInteger("21000000000000000000000000"), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR).asBigInteger());
    }

    @Test
    public void setStatusUsingRskGenesisAndOldSetMethods() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(0, bestBlock.getNumber());
        Assert.assertEquals(genesis.getHash(), bestBlock.getHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty(), status.getTotalDifficulty());

        Assert.assertEquals(bestBlock, blockChain.getBestBlock());
        Assert.assertArrayEquals(genesis.getHash().getBytes(), blockChain.getBestBlockHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty(), blockChain.getTotalDifficulty());

        Repository repository = blockChain.getRepository();

        Assert.assertArrayEquals(genesis.getStateRoot(), repository.getRoot());

        Assert.assertEquals(new BigInteger("21000000000000000000000000"), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR).asBigInteger());
    }

    @Test
    public void addBlockOne() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assert.assertEquals(2, blockChain.getSize());
        Assert.assertTrue(blockChain.getBlockStore().isBlockExist(genesis.getHash().getBytes()));
        Assert.assertTrue(blockChain.getBlockStore().isBlockExist(block1.getHash().getBytes()));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(1, bestBlock.getNumber());
        Assert.assertEquals(block1.getHash(), bestBlock.getHash());
        Assert.assertEquals(genesis.getCumulativeDifficulty().add(block1.getCumulativeDifficulty()), status.getTotalDifficulty());
    }

    @Test
    public void nullBlockAsInvalidBlock() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(null));
    }

    @Test
    public void rejectBlockOneUsingBlockHeaderValidator() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        blockChain.setBlockValidator(new RejectValidator());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadStateRoot() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        alterBytes(block1.getHeader().getStateRoot());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadReceiptsRoot() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        alterBytes(block1.getHeader().getReceiptsRoot());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadLogsBloom() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        alterBytes(block1.getHeader().getLogsBloom());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadGasUsed() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setGasUsed(block1.getHeader().getGasUsed() - 1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadPaidFees() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setPaidFees(block1.getHeader().getPaidFees().subtract(Coin.valueOf(1L)));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockBadStateRoot() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setTransactionsRoot(HashUtil.randomHash());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockBadUnclesHash() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setUnclesHash(HashUtil.randomHash());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void importNotBest() {
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(listener);
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = SelectionRule.isThisBlockHashSmaller(block1.getHash().getBytes(), block1b.getHash().getBytes());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1bBigger?block1:block1b));

        Assert.assertNotNull(listener.getLatestBlock());
        Assert.assertNotNull(listener.getLatestTrace());
        Assert.assertEquals(block1bBigger?block1.getHash():block1b.getHash(),
                listener.getLatestBlock().getHash());

        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1bBigger?block1b:block1));

        Assert.assertNotNull(listener.getLatestBlock());
        Assert.assertNotNull(listener.getLatestTrace());
        Assert.assertEquals(block1bBigger?block1b.getHash():block1.getHash(),
                listener.getLatestBlock().getHash());

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(1, bestBlock.getNumber());
        Assert.assertEquals(block1bBigger?block1.getHash():block1b.getHash(), bestBlock.getHash());
    }

    @Test
    public void getBlocksByNumber() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        List<Block> blocks = blockChain.getBlocksByNumber(1);

        Assert.assertNotNull(blocks);
        Assert.assertFalse(blocks.isEmpty());
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(blocks.get(0).getHash(), block1.getHash());
        Assert.assertEquals(blocks.get(1).getHash(), block1b.getHash());

        blocks = blockChain.getBlocksByNumber(42);

        Assert.assertNotNull(blocks);
        Assert.assertTrue(blocks.isEmpty());
    }

    @Test
    public void getBlockByNumber() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        Block block = blockChain.getBlockByNumber(0);

        Assert.assertNotNull(block);
        Assert.assertEquals(0, block.getNumber());
        Assert.assertEquals(genesis.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(1);

        Assert.assertNotNull(block);
        Assert.assertEquals(1, block.getNumber());
        Assert.assertEquals(block1.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(2);

        Assert.assertNotNull(block);
        Assert.assertEquals(2, block.getNumber());
        Assert.assertEquals(block2.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(3);

        Assert.assertNotNull(block);
        Assert.assertEquals(3, block.getNumber());
        Assert.assertEquals(block3.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(4);

        Assert.assertNull(block);
    }

    @Test
    public void switchToOtherChain() throws InterruptedException {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);
        Block block2b = blockGenerator.createChildBlock(block1b,0,2l);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2b));

        // Improve using awaitibility
        Thread.sleep(1000);

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(2, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(2, bestBlock.getNumber());
        Assert.assertEquals(block2b.getHash(), bestBlock.getHash());
    }

    @Test
    public void rejectSwitchToOtherChainUsingBlockHeaderValidation() throws InterruptedException {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        Block block2b = blockGenerator.createChildBlock(block1b);
        boolean block1bBigger = FastByteComparisons.compareTo(block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));

        blockChain.setBlockValidator(new RejectValidator());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadBlockBadStateRoot() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);
        Block block2b = blockGenerator.createChildBlock(block1b,0,2l);

        block2b.getHeader().setStateRoot(cloneAlterBytes(block2b.getStateRoot()));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    private void switchToOtherChainInvalidBadBlockBadReceiptsRootHelper(
            BlockChainImpl blockChain, Block genesis,
            Block firstBlock,
            Block secondBlock) {
        Block thirdBlock = new BlockGenerator().createChildBlock(firstBlock);
        thirdBlock.getHeader().setReceiptsRoot(cloneAlterBytes(thirdBlock.getReceiptsRoot()));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(firstBlock));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(secondBlock));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(thirdBlock));
    }

    @Test
    public void switchToOtherChainInvalidBadBlockBadReceiptsRoot() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        if (FastByteComparisons.compareTo(block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0) {
            switchToOtherChainInvalidBadBlockBadReceiptsRootHelper(blockChain,
                    genesis, block1, block1b);
        } else {
            switchToOtherChainInvalidBadBlockBadReceiptsRootHelper(blockChain,
                    genesis, block1b, block1);
        }
    }

    @Test
    public void switchToOtherChainInvalidBadBlockBadLogsBloom() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;

        Block block2b = blockGenerator.createChildBlock(block1bBigger ? block1 : block1b);

        block2b.getHeader().setLogsBloom(cloneAlterBytes(block2b.getLogBloom()));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadGasUsed() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1);
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setGasUsed(block2b.getGasUsed() + 1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadPaidFees() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setPaidFees(block2b.getHeader().getPaidFees().add(Coin.valueOf(1L)));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainByDifficulty() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        long difficulty = genesis.getDifficulty().asBigInteger().longValue() + 1;
        Block block1b = blockGenerator.createChildBlock(genesis, 0, difficulty);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1b));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(1, bestBlock.getNumber());
        Assert.assertEquals(block1b.getHash(), bestBlock.getHash());
    }

    @Test
    public void rejectBlockWithoutParent() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.NO_PARENT, blockChain.tryToConnect(block2));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(0, bestBlock.getNumber());
        Assert.assertEquals(genesis.getHash(), bestBlock.getHash());
    }

    @Test
    public void addAlreadyInChainBlock() {
        BlockChainImpl blockChain = createBlockChain();

        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.EXIST, blockChain.tryToConnect(genesis));

        BlockChainStatus status = blockChain.getStatus();

        Assert.assertNotNull(status);

        Assert.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assert.assertNotNull(bestBlock);
        Assert.assertEquals(1, bestBlock.getNumber());
        Assert.assertEquals(block1.getHash(), bestBlock.getHash());
    }

    @Test
    public void getUnknownBlockByHash() {
        BlockChainImpl blockChain = createBlockChain();

        Assert.assertNull(blockChain.getBlockByHash(new BlockGenerator().getBlock(1).getHash().getBytes()));
    }

    @Test
    public void getKnownBlocksByHash() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(block1);

        Block result = blockChain.getBlockByHash(genesis.getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertEquals(genesis.getHash(), result.getHash());

        result = blockChain.getBlockByHash(block1.getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertEquals(block1.getHash(), result.getHash());
    }

    @Test
    public void validateMinedBlockOne() {
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(listener);
        Block genesis = getGenesisBlock(blockChain);
        Block block = new BlockGenerator().createChildBlock(genesis);

        BlockExecutor executor = createExecutor(blockChain, listener);

        Assert.assertTrue(executor.executeAndValidate(block, genesis));
    }

    @Test
    public void validateMinedBlockSeven() {
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(listener);
        Block genesis = getGenesisBlock(blockChain);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

        BlockExecutor executor = createExecutor(blockChain, listener);

        Assert.assertTrue(executor.executeAndValidate(block1, genesis));
        Assert.assertTrue(executor.executeAndValidate(block2, block1));
        Assert.assertTrue(executor.executeAndValidate(block3, block2));
        Assert.assertTrue(executor.executeAndValidate(block4, block3));
        Assert.assertTrue(executor.executeAndValidate(block5, block4));
        Assert.assertTrue(executor.executeAndValidate(block6, block5));
        Assert.assertTrue(executor.executeAndValidate(block7, block6));
    }

    @Test
    public void addSevenMinedBlocks() {
        BlockChainImpl blockChain = createBlockChain();
        Block genesis = getGenesisBlock(blockChain);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block4));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block5));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block6));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block7));
    }

    @Test
    public void getUnknownTransactionInfoAsNull() {
        BlockChainImpl blockChain = createBlockChain();
        Assert.assertNull(blockChain.getTransactionInfo(new byte[] { 0x01 }));
    }

    @Test
    public void getTransactionInfo() {
        BlockExecutorTest.TestObjects objects = BlockExecutorTest.generateBlockWithOneTransaction();
        BlockChainImpl blockChain = createBlockChain(objects.getRepository(), new BlockExecutorTest.SimpleEthereumListener());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(objects.getParent()));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(objects.getBlock()));

        Assert.assertNotNull(blockChain.getTransactionInfo(objects.getTransaction().getHash().getBytes()));
    }

    @Test
    public void listenTransactionSummary() {
        BlockExecutorTest.TestObjects objects = BlockExecutorTest.generateBlockWithOneTransaction();
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(objects.getRepository(), listener);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(objects.getParent()));
        Assert.assertNull(listener.getLatestSummary());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(objects.getBlock()));
        Assert.assertNotNull(listener.getLatestSummary());
    }

    @Test
    public void listenOnBlockWhenAddingBlock() {
        BlockExecutorTest.SimpleEthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();
        BlockChainImpl blockChain = createBlockChain(listener);

        Block genesis = getGenesisBlock(blockChain);
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assert.assertNotNull(listener.getLatestBlock());
        Assert.assertNotNull(listener.getLatestTrace());
        Assert.assertEquals(block1.getHash(), listener.getLatestBlock().getHash());
    }

    @Test
    public void useBlockRecorder() {
        DummyBlockRecorder recorder = new DummyBlockRecorder();
        BlockChainImpl blockChain = createBlockChain();
        blockChain.setBlockRecorder(recorder);

        Block genesis = getGenesisBlock(blockChain);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        Assert.assertEquals(genesis, recorder.getLatestBlock());
    }

    @Test
    public void addInvalidMGPBlock() {
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB()),true)));

        IndexedBlockStore blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();
        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).addPrevMinGasPriceRule().addTxsMinGasPriceRule();

        BlockChainImpl blockChain = createBlockChain(repository, blockStore, validatorBuilder.build());

        Block genesis = getGenesisBlock(blockChain);

        Block block = new BlockBuilder().minGasPrice(BigInteger.ONE)
                .parent(genesis).build();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(1L), BigInteger.TEN);
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);

        block = new BlockBuilder().transactions(txs).minGasPrice(BigInteger.valueOf(11L))
                .parent(genesis).build();

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));
    }

    @Test
    public void addValidMGPBlock() {
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB()),true)));

        IndexedBlockStore blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), (DB) null);

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();
        validatorBuilder.blockStore(blockStore)
                .addPrevMinGasPriceRule().addTxsMinGasPriceRule();

        BlockChainImpl blockChain = createBlockChain(repository, blockStore, validatorBuilder.build());

        Repository track = repository.startTracking();

        Account account = BlockExecutorTest.createAccount("acctest1", track, Coin.valueOf(100000));
        Assert.assertTrue(account.getEcKey().hasPrivKey());
        track.commit();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction(config, "0000000000000000000000000000000000000100", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(22000L));
        tx.sign(account.getEcKey().getPrivKeyBytes());
        txs.add(tx);

        Block genesis = getGenesisBlock(blockChain);
        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        Block block = new BlockBuilder().minGasPrice(BigInteger.ZERO).transactions(txs)
                .parent(genesis).build();

        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        BlockExecutor executor = new BlockExecutor(repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                tx1,
                txindex1,
                block1.getCoinbase(),
                track1,
                null,
                null,
                programInvokeFactory,
                block1,
                null,
                totalGasUsed1,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        ));
        executor.executeAndFill(block, genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));
    }

    public static BlockChainImpl createBlockChain() {
        return createBlockChain(null);
    }

    public static BlockChainImpl createBlockChain(BlockExecutorTest.SimpleEthereumListener listener) {
        return new BlockChainBuilder().setListener(listener).build();
    }

    private static BlockChainImpl createBlockChain(Repository repository, BlockExecutorTest.SimpleEthereumListener listener) {
        IndexedBlockStore blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();
        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);

        BlockValidatorImpl blockValidator = validatorBuilder.build();

        return createBlockChain(repository, blockStore, blockValidator, listener);
    }

    private static BlockChainImpl createBlockChain(Repository repository, IndexedBlockStore blockStore, BlockValidatorImpl blockValidator) {
        return createBlockChain(repository, blockStore, blockValidator, null);
    }

    private static BlockChainImpl createBlockChain(Repository repository, IndexedBlockStore blockStore, BlockValidatorImpl blockValidator, BlockExecutorTest.SimpleEthereumListener listener) {
        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        ReceiptStore receiptStore = new ReceiptStoreImpl(ds);

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, repository, blockStore, receiptStore, null, listener, 10, 100);
        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        return new BlockChainImpl(repository, blockStore, receiptStore, transactionPool, listener, blockValidator, false, 1, new BlockExecutor(repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                tx1,
                txindex1,
                block1.getCoinbase(),
                track1,
                blockStore,
                receiptStore,
                programInvokeFactory,
                block1,
                listener,
                totalGasUsed1,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        )));
    }

    // This method modifies the repository! Not nice for a getter.
    public static Block getGenesisBlock(BlockChainImpl blockChain) {
        Repository repository = blockChain.getRepository();

        Genesis genesis = GenesisLoader.loadGenesis(config, "rsk-unittests.json",
                BigInteger.ZERO, true);

        for (RskAddress addr : genesis.getPremine().keySet()) {
            // Note that this preMine only updates balance, but not nonce, nor code, nor storage
            repository.createAccount(addr);
            repository.addBalance(addr, genesis.getPremine().get(addr).getAccountState().getBalance());
        }

        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        return genesis;
    }

    private static BlockExecutor createExecutor(BlockChainImpl blockChain, BlockExecutorTest.SimpleEthereumListener listener) {
        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        return new BlockExecutor(blockChain.getRepository(), (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                tx1,
                txindex1,
                block1.getCoinbase(),
                track1,
                blockChain.getBlockStore(),
                null,
                programInvokeFactory,
                block1,
                listener,
                totalGasUsed1,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        ));
    }

    private static void alterBytes(byte[] bytes) {
        bytes[0] = (byte)((bytes[0] + 1) % 256);
    }

    private static byte[] cloneAlterBytes(byte[] bytes) {
        byte[] cloned = Arrays.clone(bytes);

        if (cloned == null)
            return new byte[] { 0x01 };

        alterBytes(cloned);
        return cloned;
    }

    public static class RejectValidator implements BlockValidator {
        @Override
        public boolean isValid(Block block) {
            return false;
        }
    }
}
