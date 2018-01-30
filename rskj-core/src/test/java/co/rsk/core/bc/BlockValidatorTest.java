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
import co.rsk.core.commons.Keccak256;
import co.rsk.config.RskSystemProperties;
import co.rsk.peg.simples.SimpleBlock;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 04/08/2016.
 */
public class BlockValidatorTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void validateGenesisBlock() {
        BlockValidatorImpl validator = new BlockValidatorBuilder().build();
        Block genesis = new BlockGenerator().getGenesisBlock();

        Assert.assertTrue(validator.isValid(genesis));
    }

    @Test
    public void validateEmptyBlock() {
        IndexedBlockStore blockStore = new IndexedBlockStore(config);
        blockStore.init(new HashMap<>(), new HashMapDB(), null);
        Block genesis = new BlockGenerator().getGenesisBlock();
        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        Block block = new BlockBuilder().parent(genesis).build();
        BlockValidator validator = createValidator(blockStore);

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void validateChildBlock() {
        IndexedBlockStore blockStore = new IndexedBlockStore(config);
        blockStore.init(new HashMap<>(), new HashMapDB(), null);
        Block genesis = new BlockGenerator().getGenesisBlock();
        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        Block block = new BlockGenerator().createChildBlock(genesis);

        BlockValidator validator = createValidator(blockStore);

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void validateBlockWithTransaction() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        genesis.seal();

        Block parent = new BlockBuilder().parent(genesis).build();
        parent.seal();

        List<Transaction> txs = new ArrayList<>();
        txs.add(BlockExecutorTest.generateBlockWithOneTransaction().getTransaction());
        Block block = new BlockBuilder().parent(parent).transactions(txs).build();;
        block.seal();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(parent));

        BlockValidator validator = createValidator(blockChain.getBlockStore());

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void invalidChildBlockBadDifficulty() {
        Block genesis = new BlockGenerator().getGenesisBlock();
        Block block = new BlockGenerator().createChildBlock(genesis);
        block.getHeader().setDifficulty(new byte[]{0x00});

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addDifficultyRule()
                .blockStore(new SimpleBlockStore(block))
                .build();

        // If the parent difficulty is zero, the child difficulty will always be zero
        // because the child  difficulty is always the parent diff multiplied by a factor.
        // However, the calcDifficulty will put the minimum configured difficulty, so that the child
        // difficulty can never be zero.
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidChildBlockBadGasLimit() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block parent = blockGenerator.createChildBlock(block2);

        parent.getHeader().setGasLimit(new byte[]{0x00});
        Block block = blockGenerator.createChildBlock(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addParentGasLimitRule()
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidBlockWithoutParent() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addParentBlockHeaderValidator()
                .build();

        Assert.assertFalse(validator.isValid(block2));
    }

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
        block1.getHeader().setUnclesHash(new Keccak256(new byte[]{0x01}));

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(null)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void validateHeader() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block parent = new BlockBuilder().parent(genesis).build();
        Block block = new BlockBuilder().parent(parent).build();

        store.saveBlock(parent, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addParentBlockHeaderValidator()
                .addBlockRootValidationRule()
                .addBlockUnclesValidationRule(null)
                .blockStore(store)
                .build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void getGenesisEmptyAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Assert.assertTrue(FamilyUtils.getAncestors(store, genesis, 6).isEmpty());
    }

    @Test
    public void getBlockOneAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis, BigInteger.ONE, true);
        Block block = new BlockGenerator().createChildBlock(genesis);

        Set<Keccak256> ancestors = FamilyUtils.getAncestors(store, block, 6);
        Assert.assertFalse(ancestors.isEmpty());
        Assert.assertTrue(ancestors.contains(genesis.getHash()));
        Assert.assertFalse(ancestors.contains(block.getHash()));
    }

    @Test
    public void getThreeAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis, BigInteger.ONE, true);

        Block block1 = blockGenerator.createChildBlock(genesis);
        store.saveBlock(block1, BigInteger.ONE, true);
        Block block2 = blockGenerator.createChildBlock(block1);
        store.saveBlock(block2, BigInteger.ONE, true);
        Block block3 = blockGenerator.createChildBlock(block2);
        store.saveBlock(block3, BigInteger.ONE, true);
        Block block4 = blockGenerator.createChildBlock(block3);
        store.saveBlock(block4, BigInteger.ONE, true);
        Block block5 = blockGenerator.createChildBlock(block4);
        store.saveBlock(block5, BigInteger.ONE, true);

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
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);
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

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);

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
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(block2, BigInteger.ONE, true);

        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertTrue(validator.isValid(block1));
    }

    @Test
    public void invalidSiblingUncles() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        Block uncle1b = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUnclesUncleIncludedMultipeTimes () {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidPOWUncles() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Blockchain blockchain = BlockChainBuilder.ofSize(30, true);

        Block genesis = blockchain.getBlockByNumber(0);
        Block uncle1a = blockchain.getBlockByNumber(1);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);

        BlockParentDependantValidationRule parentValidationRule = Mockito.mock(BlockParentDependantValidationRule.class);
        Mockito.when(parentValidationRule.isValid(Mockito.any(), Mockito.any())).thenReturn(true);
      
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store, new ProofOfWorkRule(config).setFallbackMiningEnabled(false), parentValidationRule)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleIsAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(genesis.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasNoSavedParent() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(new BlockGenerator().createChildBlock(genesis));
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasNoCommonAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block uncle1a = blockGenerator.createChildBlock(new SimpleBlock(null, null, TestUtils.randomAddress().getBytes(), null, BigInteger.ONE.toByteArray(),
                0, null, 0L, 0L, new byte[]{}, null, null, null, Block.getTxTrie(null).getHash(), null, null, null));

        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = blockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasParentThatIsNotAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

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

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(block3, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void invalidUncleAlreadyUsed() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

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

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void tooManyUncles() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

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

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(store)
                .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block2));
    }

    @Test
    public void twoTransactionsSameNonce() {
        BlockExecutorTest.TestObjects objects = BlockExecutorTest.generateBlockWithOneTransaction();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = objects.getTransaction();
        txs.add(tx);
        txs.add(tx);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);
        block.setTransactionsList(txs);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockTxsValidationRule(objects.getRepository())
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processBlockWithInvalidMGPTxs() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder().minGasPrice(BigInteger.ZERO)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN);
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);
        Block block = new BlockBuilder().minGasPrice(BigInteger.TEN).transactions(txs)
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addTxsMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processBlockWithInvalidPrevMGP() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder().minGasPrice(BigInteger.ZERO)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        Block block = new BlockBuilder().minGasPrice(BigInteger.TEN)
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addPrevMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processValidMGPBlock() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder().minGasPrice(BigInteger.TEN)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);

        Block block = new BlockBuilder().transactions(txs).minGasPrice(BigInteger.valueOf(11L))
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addPrevMinGasPriceRule()
                .addTxsMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void parentInvalidNumber() {
        IndexedBlockStore store = new IndexedBlockStore(config);
        store.init(new HashMap<>(), new HashMapDB(), null);

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();

        Block block = new BlockBuilder().parent(genesis).build();
        block.getHeader().setNumber(25L);
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addParentNumberRule()
                .blockStore(store)
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidTxNonce() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.TEN, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        block.getHeader().setNumber(25L);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockTxsValidationRule(blockChain.getRepository())
                .build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void noRemascTx() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder().parent(genesis).transactions(null).build();
        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder().parent(genesis).transactions(new ArrayList<>()).build();
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void remascTxNotInLastPosition() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        txs.add(tx);

        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();

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
        Transaction tx = Transaction.create(config, "0000000000000000000000000000000000000006", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assert.assertTrue(validator.isValid(block));
    }


    @Test
    public void blockInTheFuture() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        int validPeriod = config.getBlockchainConfig().getCommonConstants().getNewBlockMaxSecondsInTheFuture();

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2*validPeriod);

        Mockito.when(block.getParentHash()).thenReturn(genesis.getHash());

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockTimeStampValidationRule(validPeriod)
                .build();

        Assert.assertFalse(validator.isValid(block));

        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + validPeriod);

        Assert.assertTrue(validator.isValid(block));
    }

    private static BlockValidator createValidator(BlockStore blockStore) {
        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder
                .addBlockRootValidationRule()
                .addParentGasLimitRule()
                .addBlockRootValidationRule()
                .addBlockUnclesValidationRule(blockStore)
                .blockStore(blockStore);

        return validatorBuilder.build();
    }

    private static class SimpleBlockStore implements BlockStore {
        private Block block;

        public SimpleBlockStore(Block block) {
            this.block = block;
        }

        @Override
        public Keccak256 getBlockHashByNumber(long blockNumber) {
            return new Keccak256(new byte[0]);
        }

        @Override
        public Keccak256 getBlockHashByNumber(long blockNumber, Keccak256 branchBlockHash) {
            return new Keccak256(new byte[0]);
        }

        @Override
        public Block getChainBlockByNumber(long blockNumber) {
            return null;
        }

        @Override
        public List<Block> getChainBlocksByNumber(long blockNumber) {
            return new ArrayList<>();
        }

        @Override
        public Block getBlockByHash(Keccak256 hash) {
            return block;
        }

        @Override
        public Block getBlockByHashAndDepth(Keccak256 hash, long depth) {
            return null;
        }

        @Override
        public boolean isBlockExist(Keccak256 hash) {
            return false;
        }

        @Override
        public List<Keccak256> getListHashesEndWith(Keccak256 hash, long qty) {
            return null;
        }

        @Override
        public List<BlockHeader> getListHeadersEndWith(Keccak256 hash, long qty) {
            return null;
        }

        @Override
        public List<Block> getListBlocksEndWith(Keccak256 hash, long qty) {
            return null;
        }

        @Override
        public void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain) {

        }

        @Override
        public BigInteger getTotalDifficultyForHash(Keccak256 hash) {
            return null;
        }

        @Override
        public Block getBestBlock() {
            return null;
        }

        @Override
        public long getMaxNumber() {
            return 0;
        }

        @Override
        public void flush() {

        }

        @Override
        public void reBranch(Block forkBlock) {

        }

        @Override
        public void load() {

        }

        @Override
        public void removeBlock(Block block) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BlockInformation> getBlocksInformationByNumber(long blockNumber) { return null; }
    }
}


