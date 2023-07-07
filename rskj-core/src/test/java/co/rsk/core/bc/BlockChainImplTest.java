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
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BlockChainImplTest {

    private final ECKey cowKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes()));
    private final ECKey catKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cat".getBytes()));

    @TempDir
    public Path tempDir;

    private RskTestFactory objects;
    private RskSystemProperties config;
    private Blockchain blockChain;
    private BlockExecutor blockExecutor;
    private BlockExecutorTest.SimpleEthereumListener listener;
    private BlockStore blockStore;

    @BeforeEach
    void setup() {
        objects = new RskTestFactory(tempDir) {
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
    void addGenesisBlock() {
        Block genesis = blockChain.getBestBlock();

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(0, bestBlock.getNumber());
        Assertions.assertEquals(genesis.getHash(), bestBlock.getHash());
        Assertions.assertEquals(genesis.getCumulativeDifficulty(), status.getTotalDifficulty());
    }

    @Test
    void onBestBlockTest() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis, 0, 2L);
        Block block1b = blockGenerator.createChildBlock(genesis, 0, 1L);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assertions.assertEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assertions.assertNotEquals(listener.getBestBlock().getHash(), listener.getLatestBlock().getHash());
        Assertions.assertEquals(block1.getHash(), listener.getBestBlock().getHash());
        Assertions.assertEquals(block1b.getHash(), listener.getLatestBlock().getHash());
    }

    @Test
    void addBlockOne() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assertions.assertEquals(2, blockChain.getSize());
        Assertions.assertTrue(blockStore.isBlockExist(genesis.getHash().getBytes()));
        Assertions.assertTrue(blockStore.isBlockExist(block1.getHash().getBytes()));

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(1, bestBlock.getNumber());
        Assertions.assertEquals(block1.getHash(), bestBlock.getHash());
        Assertions.assertEquals(genesis.getCumulativeDifficulty().add(block1.getCumulativeDifficulty()), status.getTotalDifficulty());
    }

    @Test
    void nullBlockAsInvalidBlock() {
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(null));
    }

    @Test
    void rejectBlockOneUsingBlockHeaderValidator() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        ((BlockChainImpl) blockChain).setBlockValidator(new RejectValidator());

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockOneBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setStateRoot(cloneAlterBytes(block1.getHeader().getStateRoot()));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockOneBadReceiptsRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setReceiptsRoot(cloneAlterBytes(block1.getHeader().getReceiptsRoot()));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockOneBadLogsBloom() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setLogsBloom(cloneAlterBytes(block1.getHeader().getLogsBloom()));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockOneBadGasUsed() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setGasUsed(block1.getHeader().getGasUsed() - 1);

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockOneBadPaidFees() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setPaidFees(block1.getHeader().getPaidFees().subtract(Coin.valueOf(1L)));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void importNotBest() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = SelectionRule.isThisBlockHashSmaller(block1.getHash().getBytes(), block1b.getHash().getBytes());

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1bBigger ? block1 : block1b));

        Assertions.assertNotNull(listener.getLatestBlock());
        Assertions.assertEquals(block1bBigger ? block1.getHash() : block1b.getHash(),
                listener.getLatestBlock().getHash());

        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1bBigger ? block1b : block1));

        Assertions.assertNotNull(listener.getLatestBlock());
        Assertions.assertEquals(block1bBigger ? block1b.getHash() : block1.getHash(),
                listener.getLatestBlock().getHash());

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(1, bestBlock.getNumber());
        Assertions.assertEquals(block1bBigger ? block1.getHash() : block1b.getHash(), bestBlock.getHash());
    }

    @Test
    void getBlocksByNumber() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis, 0, 2);
        Block block1b = blockGenerator.createChildBlock(genesis, 0, 1);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        List<Block> blocks = blockChain.getBlocksByNumber(1);

        Assertions.assertNotNull(blocks);
        Assertions.assertFalse(blocks.isEmpty());
        Assertions.assertEquals(2, blocks.size());
        Assertions.assertEquals(blocks.get(0).getHash(), block1.getHash());
        Assertions.assertEquals(blocks.get(1).getHash(), block1b.getHash());

        blocks = blockChain.getBlocksByNumber(42);

        Assertions.assertNotNull(blocks);
        Assertions.assertTrue(blocks.isEmpty());
    }

    @Test
    void getBlockByNumber() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        Block block = blockChain.getBlockByNumber(0);

        Assertions.assertNotNull(block);
        Assertions.assertEquals(0, block.getNumber());
        Assertions.assertEquals(genesis.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(1);

        Assertions.assertNotNull(block);
        Assertions.assertEquals(1, block.getNumber());
        Assertions.assertEquals(block1.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(2);

        Assertions.assertNotNull(block);
        Assertions.assertEquals(2, block.getNumber());
        Assertions.assertEquals(block2.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(3);

        Assertions.assertNotNull(block);
        Assertions.assertEquals(3, block.getNumber());
        Assertions.assertEquals(block3.getHash(), block.getHash());

        block = blockChain.getBlockByNumber(4);

        Assertions.assertNull(block);
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void switchToOtherChain() throws InterruptedException {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis, 0, 2L);
        Block block1b = blockGenerator.createChildBlock(genesis, 0, 1L);
        Block block2b = blockGenerator.createChildBlock(block1b, 0, 2L);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2b));

        // Improve using awaitibility
        Thread.sleep(1000);

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(2, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(2, bestBlock.getNumber());
        Assertions.assertEquals(block2b.getHash(), bestBlock.getHash());
    }

    @Test
    void rejectSwitchToOtherChainUsingBlockHeaderValidation() throws InterruptedException {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        Block block2b = blockGenerator.createChildBlock(block1b);
        boolean block1bBigger = FastByteComparisons.compareTo(block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger ? block1 : block1b));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger ? block1b : block1));

        ((BlockChainImpl) blockChain).setBlockValidator(new RejectValidator());

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    void switchToOtherChainInvalidBadBlockBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis, 0, 2L);
        Block block1b = blockGenerator.createChildBlock(genesis, 0, 1L);
        Block block2b = blockGenerator.createChildBlock(block1b, 0, 2L);

        block2b.getHeader().setStateRoot(cloneAlterBytes(block2b.getStateRoot()));

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    private void switchToOtherChainInvalidBadBlockBadReceiptsRootHelper(
            Blockchain blockChain, Block genesis,
            Block firstBlock,
            Block secondBlock) {
        Block thirdBlock = new BlockGenerator().createChildBlock(firstBlock);
        thirdBlock.getHeader().setReceiptsRoot(cloneAlterBytes(thirdBlock.getReceiptsRoot()));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(firstBlock));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(secondBlock));
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(thirdBlock));
    }

    @Test
    void switchToOtherChainInvalidBadBlockBadReceiptsRoot() {
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
    void switchToOtherChainInvalidBadBlockBadLogsBloom() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);

        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;

        Block block2b = blockGenerator.createChildBlock(block1bBigger ? block1 : block1b);

        block2b.getHeader().setLogsBloom(cloneAlterBytes(block2b.getLogBloom()));

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger ? block1 : block1b));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger ? block1b : block1));
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    void switchToOtherChainInvalidBadGasUsed() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis, 0, 2);
        Block block1b = blockGenerator.createChildBlock(genesis, 0, 1);
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setGasUsed(block2b.getGasUsed() + 1);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(block1b));
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    void switchToOtherChainInvalidBadPaidFees() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block1b = blockGenerator.createChildBlock(genesis);
        boolean block1bBigger = FastByteComparisons.compareTo(
                block1.getHash().getBytes(), 0, 32,
                block1b.getHash().getBytes(), 0, 32) < 0;
        Block block2b = blockGenerator.createChildBlock(block1b);

        block2b.getHeader().setPaidFees(block2b.getHeader().getPaidFees().add(Coin.valueOf(1L)));

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(
                block1bBigger ? block1 : block1b));
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(
                block1bBigger ? block1b : block1));
        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block2b));
    }

    @Test
    void switchToOtherChainByDifficulty() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        long difficulty = genesis.getDifficulty().asBigInteger().longValue() + 1;
        Block block1b = blockGenerator.createChildBlock(genesis, 0, difficulty);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1b));

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(1, bestBlock.getNumber());
        Assertions.assertEquals(block1b.getHash(), bestBlock.getHash());
    }

    @Test
    void rejectBlockWithoutParent() {
        Block genesis = blockChain.getBestBlock();
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);

        Assertions.assertEquals(ImportResult.NO_PARENT, blockChain.tryToConnect(block2));

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(0, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(0, bestBlock.getNumber());
        Assertions.assertEquals(genesis.getHash(), bestBlock.getHash());
    }

    @Test
    void addAlreadyInChainBlock() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.EXIST, blockChain.tryToConnect(genesis));

        BlockChainStatus status = blockChain.getStatus();

        Assertions.assertNotNull(status);

        Assertions.assertEquals(1, status.getBestBlockNumber());

        Block bestBlock = status.getBestBlock();

        Assertions.assertNotNull(bestBlock);
        Assertions.assertEquals(1, bestBlock.getNumber());
        Assertions.assertEquals(block1.getHash(), bestBlock.getHash());
    }

    @Test
    void getUnknownBlockByHash() {

        Assertions.assertNull(blockChain.getBlockByHash(new BlockGenerator().getBlock(1).getHash().getBytes()));
    }

    @Test
    void getKnownBlocksByHash() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(block1);

        Block result = blockChain.getBlockByHash(genesis.getHash().getBytes());

        Assertions.assertNotNull(result);
        Assertions.assertEquals(genesis.getHash(), result.getHash());

        result = blockChain.getBlockByHash(block1.getHash().getBytes());

        Assertions.assertNotNull(result);
        Assertions.assertEquals(block1.getHash(), result.getHash());
    }

    @Test
    void validateMinedBlockOne() {
        Block genesis = blockChain.getBestBlock();
        Block block = new BlockGenerator().createChildBlock(genesis);

        Assertions.assertTrue(blockExecutor.executeAndValidate(block, genesis.getHeader()));
    }

    @Test
    void validateMinedBlockSeven() {
        Block genesis = blockChain.getBestBlock();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

        Assertions.assertTrue(blockExecutor.executeAndValidate(block1, genesis.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block2, block1.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block3, block2.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block4, block3.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block5, block4.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block6, block5.getHeader()));
        Assertions.assertTrue(blockExecutor.executeAndValidate(block7, block6.getHeader()));
    }

    @Test
    void addSevenMinedBlocks() {
        Block genesis = blockChain.getBestBlock();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);
        Block block6 = blockGenerator.createChildBlock(block5);
        Block block7 = blockGenerator.createChildBlock(block6);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block4));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block5));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block6));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block7));
    }

    @Test
    void getUnknownTransactionInfoAsNull() {
        Assertions.assertNull(blockChain.getTransactionInfo(new byte[]{0x01}));
    }

    @Test
    void getTransactionInfo() {
        Block block = getBlockWithOneTransaction();

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));

        Transaction tx = block.getTransactionsList().get(0);
        Assertions.assertNotNull(blockChain.getTransactionInfo(tx.getHash().getBytes()));
    }

    @Test
    void listenOnBlockWhenAddingBlock() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Assertions.assertNotNull(listener.getLatestBlock());
        Assertions.assertEquals(block1.getHash(), listener.getLatestBlock().getHash());
    }

    @Test
    void addValidMGPBlock() {
        Repository track = objects.getRepositoryLocator().startTrackingAt(blockChain.getBestBlock().getHeader());

        Account account = BlockExecutorTest.createAccount("acctest1", track, Coin.valueOf(100000));
        Assertions.assertTrue(account.getEcKey().hasPrivKey());
        track.commit();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(22000L))
                .destination(Hex.decode("0000000000000000000000000000000000000100"))
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.ZERO)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        txs.add(tx);

        Block genesis = blockChain.getBestBlock();

        Block block = new BlockBuilder(null, null, null)
                .minGasPrice(BigInteger.ZERO).transactions(txs).parent(genesis).build();

        blockExecutor.executeAndFill(block, genesis.getHeader());

        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));
    }

    @Deprecated
    public static Block getGenesisBlock(final TrieStore trieStore) {
        return new TestGenesisLoader(trieStore, "rsk-unittests.json", BigInteger.ZERO, true, true, true).load();
    }

    private Block getBlockWithOneTransaction() {
        Block bestBlock = blockChain.getBestBlock();
        RepositorySnapshot repository = objects.getRepositoryLocator().snapshotAt(bestBlock.getHeader());

        String toAddress = ByteUtil.toHexString(catKey.getAddress());
        BigInteger nonce = repository.getNonce(new RskAddress(cowKey.getAddress()));
        Transaction tx = Transaction
                .builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(Hex.decode(toAddress))
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
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
            return new byte[]{0x01};

        cloned[0] = (byte) ((cloned[0] + 1) % 256);
        return cloned;
    }

    public static class RejectValidator implements BlockValidator {
        @Override
        public boolean isValid(Block block) {
            return false;
        }
    }
}
