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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositorySnapshot;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.validators.BlockValidator;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BlockChainImplTest {
    private ECKey cowKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes()));
    private ECKey catKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cat".getBytes()));

    private RskTestFactory objects;
    private RskSystemProperties config;
    private Blockchain blockChain;
    private BlockExecutor blockExecutor;
    private BlockExecutorTest.SimpleEthereumListener listener;
    private BlockStore blockStore;

    @Before
    public void setup() {
        objects = new RskTestFactory() {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }

            @Override
            protected CompositeEthereumListener buildCompositeEthereumListener() {
                return new BlockExecutorTest.SimpleEthereumListener();
            }
        };
        config = objects.getRskSystemProperties();
        blockChain = objects.getBlockchain();
        blockStore = objects.getBlockStore();
        blockExecutor = objects.getBlockExecutor();
        listener = (BlockExecutorTest.SimpleEthereumListener) objects.getCompositeEthereumListener();
    }

    @Test
    public void addGenesisBlock() {
        Block genesis = blockChain.getBestBlock();

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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assert.assertEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertNotEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assert.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assert.assertEquals(block1b.getHash(), listener.getLatestBlock().getHash());
    }

    @Test
    public void addBlockOne() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assert.assertEquals(2, blockChain.getSize());
        Assert.assertTrue(blockStore.isBlockExist(genesis.getHash().getBytes()));
        Assert.assertTrue(blockStore.isBlockExist(block1.getHash().getBytes()));

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
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(null));
    }

    @Test
    public void rejectBlockOneUsingBlockHeaderValidator() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        ((BlockChainImpl) blockChain).setBlockValidator(new RejectValidator());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setStateRoot(cloneAlterBytes(block1.getHeader().getStateRoot()));

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadReceiptsRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setReceiptsRoot(cloneAlterBytes(block1.getHeader().getReceiptsRoot()));

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadLogsBloom() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setLogsBloom(cloneAlterBytes(block1.getHeader().getLogsBloom()));

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadGasUsed() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setGasUsed(block1.getHeader().getGasUsed() - 1);

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockOneBadPaidFees() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setPaidFees(block1.getHeader().getPaidFees().subtract(Coin.valueOf(1L)));

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void importNotBest() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = SelectionRule.isThisBlockHashSmaller(block1.getHash().getBytes(), block1b.getHash().getBytes());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1bBigger?block1:block1b));

        Assert.assertNotNull(listener.getLatestBlock());
        Assert.assertEquals(block1bBigger?block1.getHash():block1b.getHash(),
                listener.getLatestBlock().getHash());

        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1bBigger?block1b:block1));

        Assert.assertNotNull(listener.getLatestBlock());
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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1);

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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);

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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);
        Block block2b = blockGenerator.createChildBlock(block1b,0,2l);

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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        Block block2b = blockGenerator.createChildBlock(block1b);
        boolean block1bBigger = FastByteComparisons.compareTo(block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));

        ((BlockChainImpl) blockChain).setBlockValidator(new RejectValidator());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadBlockBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2l);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1l);
        Block block2b = blockGenerator.createChildBlock(block1b,0,2l);

        block2b.getHeader().setStateRoot(cloneAlterBytes(block2b.getStateRoot()));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    private void switchToOtherChainInvalidBadBlockBadReceiptsRootHelper(
            Blockchain blockChain, Block genesis,
            Block firstBlock,
            Block secondBlock) {
        Block thirdBlock = new BlockGenerator().createChildBlock(firstBlock);
        thirdBlock.getHeader().setReceiptsRoot(cloneAlterBytes(thirdBlock.getReceiptsRoot()));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(firstBlock));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(secondBlock));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(thirdBlock));
    }

    @Test
    public void switchToOtherChainInvalidBadBlockBadReceiptsRoot() {
        Block genesis = blockChain.getBestBlock();
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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;

        Block block2b = blockGenerator.createChildBlock(block1bBigger ? block1 : block1b);

        block2b.getHeader().setLogsBloom(cloneAlterBytes(block2b.getLogBloom()));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadGasUsed() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis,0,2);
        Block block1b = blockGenerator.createChildBlock(genesis,0,1);
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setGasUsed(block2b.getGasUsed() + 1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainInvalidBadPaidFees() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setPaidFees(block2b.getHeader().getPaidFees().add(Coin.valueOf(1L)));

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger?block1:block1b));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger?block1b:block1));
        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    public void switchToOtherChainByDifficulty() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        long difficulty = genesis.getDifficulty().asBigInteger().longValue() + 1;
        Block block1b = blockGenerator.createChildBlock(genesis, 0, difficulty);

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
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);

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
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

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

        Assert.assertNull(blockChain.getBlockByHash(new BlockGenerator().getBlock(1).getHash().getBytes()));
    }

    @Test
    public void getKnownBlocksByHash() {
        Block genesis = blockChain.getBestBlock();
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
        Block genesis = blockChain.getBestBlock();
        Block block = new BlockGenerator().createChildBlock(genesis);

        Assert.assertTrue(blockExecutor.executeAndValidate(block, genesis.getHeader()));
    }

    @Test
    public void validateMinedBlockSeven() {
        Block genesis = blockChain.getBestBlock();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

        Assert.assertTrue(blockExecutor.executeAndValidate(block1, genesis.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block2, block1.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block3, block2.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block4, block3.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block5, block4.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block6, block5.getHeader()));
        Assert.assertTrue(blockExecutor.executeAndValidate(block7, block6.getHeader()));
    }

    @Test
    public void addSevenMinedBlocks() {
        Block genesis = blockChain.getBestBlock();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

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
        Assert.assertNull(blockChain.getTransactionInfo(new byte[] { 0x01 }));
    }

    @Test
    public void getTransactionInfo() {
        Block block = getBlockWithOneTransaction();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));

        Transaction tx = block.getTransactionsList().get(0);
        Assert.assertNotNull(blockChain.getTransactionInfo(tx.getHash().getBytes()));
    }

    @Test
    public void listenOnBlockWhenAddingBlock() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assert.assertNotNull(listener.getLatestBlock());
        Assert.assertEquals(block1.getHash(), listener.getLatestBlock().getHash());
    }

    @Test
    public void addValidMGPBlock() {
        Repository track = objects.getRepositoryLocator().startTrackingAt(blockChain.getBestBlock().getHeader());

        Account account = BlockExecutorTest.createAccount("acctest1", track, Coin.valueOf(100000));
        Assert.assertTrue(account.getEcKey().hasPrivKey());
        track.commit();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000100", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(22000L), config.getNetworkConstants().getChainId());
        tx.sign(account.getEcKey().getPrivKeyBytes());
        txs.add(tx);

        Block genesis = blockChain.getBestBlock();

        Block block = new BlockBuilder(null, null,null)
                .minGasPrice(BigInteger.ZERO).transactions(txs).parent(genesis).build();

        blockExecutor.executeAndFill(block, genesis.getHeader());

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));
    }

    @Deprecated
    public static Block getGenesisBlock(final TrieStore trieStore) {
        return new TestGenesisLoader(trieStore, "rsk-unittests.json", BigInteger.ZERO, true, true, true).load();
    }

    private Block getBlockWithOneTransaction() {
        Block bestBlock = blockChain.getBestBlock();
        RepositorySnapshot repository = objects.getRepositoryLocator().snapshotAt(bestBlock.getHeader());

        String toAddress = Hex.toHexString(catKey.getAddress());
        BigInteger nonce = repository.getNonce(new RskAddress(cowKey.getAddress()));
        Transaction tx = new Transaction(toAddress, BigInteger.TEN, nonce, BigInteger.ONE, BigInteger.valueOf(21000), config.getNetworkConstants().getChainId());
        tx.sign(cowKey.getPrivKeyBytes());

        List<Transaction> txs = java.util.Arrays.asList(tx, new RemascTransaction(bestBlock.getNumber() + 1));

        List<BlockHeader> uncles = new ArrayList<>();
        Block block = new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, bestBlock.getMinimumGasPrice().asBigInteger());
        blockExecutor.executeAndFill(block, bestBlock.getHeader());
        return block;
    }

    private static byte[] cloneAlterBytes(byte[] bytes) {
        byte[] cloned = Arrays.clone(bytes);

        if (cloned == null)
            return new byte[] { 0x01 };

        cloned[0] = (byte)((cloned[0] + 1) % 256);
        return cloned;
    }

    public static class RejectValidator implements BlockValidator {
        @Override
        public boolean isValid(Block block) {
            return false;
        }
    }
}
