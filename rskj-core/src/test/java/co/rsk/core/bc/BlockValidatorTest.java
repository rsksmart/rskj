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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 04/08/2016.
 */
public class BlockValidatorTest {

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    public void validEmptyUnclesHash() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(null)
                .build();

        Assert.assertTrue(validator.isValid(block1));
    }

    @Test
    public void invalidUnclesHash() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Whitebox.setInternalState(block1.getHeader(), "unclesHash", new byte[]{0x01});

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(null)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void getBlockOneAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        Block block = new BlockGenerator().createChildBlock(genesis);

        Set<Keccak256> ancestors = FamilyUtils.getAncestors(store, block, 6);
        Assert.assertFalse(ancestors.isEmpty());
        Assert.assertTrue(ancestors.contains(genesis.getHash()));
        Assert.assertFalse(ancestors.contains(block.getHash()));
    }

    @Test
    public void getThreeAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis, TEST_DIFFICULTY, true);

        Block block1 = blockGenerator.createChildBlock(genesis);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        Block block2 = blockGenerator.createChildBlock(block1);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        Block block3 = blockGenerator.createChildBlock(block2);
        store.saveBlock(block3, TEST_DIFFICULTY, true);
        Block block4 = blockGenerator.createChildBlock(block3);
        store.saveBlock(block4, TEST_DIFFICULTY, true);
        Block block5 = blockGenerator.createChildBlock(block4);
        store.saveBlock(block5, TEST_DIFFICULTY, true);

        Set<Keccak256> ancestors = FamilyUtils.getAncestors(store, block5, 3);
        Assert.assertFalse(ancestors.isEmpty());
        Assert.assertEquals(3, ancestors.size());
        Assert.assertFalse(ancestors.contains(genesis.getHash()));
        Assert.assertFalse(ancestors.contains(block1.getHash()));
        Assert.assertTrue(ancestors.contains(block2.getHash()));
        Assert.assertTrue(ancestors.contains(block3.getHash()));
        Assert.assertTrue(ancestors.contains(block4.getHash()));
        Assert.assertFalse(ancestors.contains(block5.getHash()));
    }

    @Test
    public void getUsedUncles() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());

        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        Block uncle2a = blockGenerator.createChildBlock(genesis);
        Block uncle2b = blockGenerator.createChildBlock(genesis);

        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());

        Block block2 = blockGenerator.createChildBlock(block1, null, uncles2, 1, null);
        Block block3 = blockGenerator.createChildBlock(block2, null, uncles2, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle1b, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle2a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle2b, TEST_DIFFICULTY, false);
        store.saveBlock(block2, TEST_DIFFICULTY, true);

        Set<Keccak256> used = FamilyUtils.getUsedUncles(store, block3, 6);

        Assert.assertFalse(used.isEmpty());
        Assert.assertFalse(used.contains(block3.getHash()));
        Assert.assertFalse(used.contains(block2.getHash()));
        Assert.assertTrue(used.contains(uncle2a.getHash()));
        Assert.assertTrue(used.contains(uncle2b.getHash()));
        Assert.assertFalse(used.contains(block1.getHash()));
        Assert.assertTrue(used.contains(uncle1a.getHash()));
        Assert.assertTrue(used.contains(uncle1b.getHash()));
        Assert.assertFalse(used.contains(genesis.getHash()));
    }

    @Test
    public void invalidUncleTransactionsRoot() {
        Block block = new BlockGenerator().createBlock(2, 10);

        block.getHeader().setTransactionsRoot(new byte[]{0x01});

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockRootValidationRule()
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void validUncles() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(block2, TEST_DIFFICULTY, true);

        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle1b, TEST_DIFFICULTY, false);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertTrue(validator.isValid(block1));
    }

    @Test
    public void invalidSiblingUncles() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle1b, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUnclesUncleIncludedMultipeTimes () {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidPOWUncles() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Blockchain blockchain = new BlockChainBuilder().ofSize(30, true);

        Block genesis = blockchain.getBlockByNumber(0);
        Block uncle1a = blockchain.getBlockByNumber(1);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);

        BlockHeaderParentDependantValidationRule parentValidationRule = mock(BlockHeaderParentDependantValidationRule.class);
        when(parentValidationRule.isValid(Mockito.any(), Mockito.any())).thenReturn(true);
      
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), parentValidationRule)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleIsAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(genesis.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasNoSavedParent() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(new BlockGenerator().createChildBlock(genesis));
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasNoCommonAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.randomAddress())
                .setDifficulty(TEST_DIFFICULTY)
                .setEmptyMergedMiningForkDetectionData()
                .setMinimumGasPrice(Coin.valueOf(10))
                .build();

        Block uncle1a = blockGenerator.createChildBlock(blockFactory.newBlock(
                newHeader,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasParentThatIsNotAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle2a = blockGenerator.createChildBlock(uncle1a);

        List<BlockHeader> uncles3 = new ArrayList<>();
        uncles3.add(uncle2a.getHeader());
        uncles3.add(uncle1a.getHeader());

        Block block1 = blockGenerator.createChildBlock(genesis, null, null, 1, null);
        Block block2 = blockGenerator.createChildBlock(block1, null, null, 1, null);
        Block block3 = blockGenerator.createChildBlock(block2, null, uncles3, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle2a, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(block2, TEST_DIFFICULTY, true);
        store.saveBlock(block3, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void invalidUncleAlreadyUsed() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        Block uncle2a = blockGenerator.createChildBlock(genesis);
        Block uncle2b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());

        Block block2 = blockGenerator.createChildBlock(block1, null, uncles2, 1, null);
        Block block3 = blockGenerator.createChildBlock(block2, null, uncles2, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle1b, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle2a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle2b, TEST_DIFFICULTY, false);
        store.saveBlock(block2, TEST_DIFFICULTY, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void tooManyUncles() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        Block block1 =  blockGenerator.createChildBlock(genesis, null, null, 1, null);

        Block uncle2a = blockGenerator.createChildBlock(genesis);
        Block uncle2b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());
        uncles2.add(uncle1a.getHeader());
        uncles2.add(uncle1b.getHeader());

        for(int i = 0; i < 10; i++) {
            uncles2.add(blockGenerator.createChildBlock(genesis).getHeader());
        }

        Block block2 = blockGenerator.createChildBlock(block1, null, uncles2, 1, null);

        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        store.saveBlock(uncle1a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle1b, TEST_DIFFICULTY, false);
        store.saveBlock(block1, TEST_DIFFICULTY, true);
        store.saveBlock(uncle2a, TEST_DIFFICULTY, false);
        store.saveBlock(uncle2b, TEST_DIFFICULTY, false);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block2));
    }

    @Test
    public void processBlockWithInvalidMGPTxs() {
        BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        Repository repository = mock(Repository.class);

        when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder(null, null, null).minGasPrice(BigInteger.ZERO)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN, config.getNetworkConstants().getChainId());
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);
        Block block = new BlockBuilder(null, null, null).minGasPrice(BigInteger.TEN).transactions(txs)
                .parent(parent).build();

        when(blockStore.getBlockByHash(block.getParentHash().getBytes())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addTxsMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processBlockWithInvalidPrevMGP() {
        BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        Repository repository = mock(Repository.class);

        when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder(null, null, null).minGasPrice(BigInteger.ZERO)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        Block block = new BlockBuilder(null, null, null).minGasPrice(BigInteger.TEN)
                .parent(parent).build();

        when(blockStore.getBlockByHash(block.getParentHash().getBytes())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addPrevMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processValidMGPBlock() {
        BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        Repository repository = mock(Repository.class);

        when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder(null, null, null).minGasPrice(BigInteger.TEN)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN, config.getNetworkConstants().getChainId());
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);

        Block block = new BlockBuilder(null, null,null)
                .transactions(txs).minGasPrice(BigInteger.valueOf(11L)).parent(parent).build();

        when(blockStore.getBlockByHash(block.getParentHash().getBytes())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addPrevMinGasPriceRule()
                .addTxsMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void noRemascTx() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN, config.getNetworkConstants().getChainId());
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder(null, null, null).parent(genesis).transactions(null).build();
        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder(null, null, null).parent(genesis).transactions(new ArrayList<>()).build();
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void remascTxNotInLastPosition() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN, config.getNetworkConstants().getChainId());

        tx.sign(new byte[]{});
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        txs.add(tx);

        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void remascTx() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = new Transaction("0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN, config.getNetworkConstants().getChainId());
        tx.sign(new byte[]{});
        txs.add(tx);
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void blockInTheFuture() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        int validPeriod = 6000;

        BlockHeader header = mock(BlockHeader.class);
        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2*validPeriod);

        when(header.getParentHash()).thenReturn(genesis.getHash());

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockTimeStampValidationRule(validPeriod)
                .build();

        Assert.assertFalse(validator.isValid(block));

        when(header.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + validPeriod);

        Assert.assertTrue(validator.isValid(block));
    }


    @Test
    public void blockInTheFutureIsAcceptedWhenValidPeriodIsZero() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        int validPeriod = 0;

        Block block = mock(Block.class);
        when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2000);

        when(block.getParentHash()).thenReturn(genesis.getHash());

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockTimeStampValidationRule(validPeriod)
                .build();

        Assert.assertTrue(validator.isValid(block));

        when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2000);

        Assert.assertTrue(validator.isValid(block));
    }
}


