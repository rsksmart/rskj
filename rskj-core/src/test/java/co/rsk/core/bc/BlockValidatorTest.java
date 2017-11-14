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
import co.rsk.peg.simples.SimpleBlock;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.validators.BlockParentDependantValidationRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import co.rsk.validators.ProofOfWorkRule;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 04/08/2016.
 */
public class BlockValidatorTest {
    @Test
    public void validateGenesisBlock() {
        BlockValidatorImpl validator = new BlockValidatorBuilder().build();
        Block genesis = BlockGenerator.getGenesisBlock();

        Assert.assertTrue(validator.isValid(genesis));
    }

    @Test
    public void validateEmptyBlock() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        Block block = new BlockBuilder().parent(genesis).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentBlockHeaderValidator().blockStore(blockChain.getBlockStore())
                .addBlockRootValidationRule().addBlockUnclesValidationRule(blockChain.getBlockStore()).build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void validateChildBlock() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        Block block = BlockGenerator.createChildBlock(genesis);
        blockChain.tryToConnect(genesis);

        BlockValidatorImpl validator = (BlockValidatorImpl) blockChain.getBlockValidator();
        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void validateBlockWithTransaction() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);

        Block parent = new BlockBuilder().parent(genesis).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(BlockExecutorTest.generateBlockWithOneTransaction().getTransaction());
        Block block = new BlockBuilder().parent(parent).transactions(txs).build();;

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentBlockHeaderValidator().blockStore(blockChain.getBlockStore())
                .addBlockRootValidationRule().addBlockUnclesValidationRule(blockChain.getBlockStore()).build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void invalidChildBlockBadDifficulty() {
        BlockChainImpl blockchain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        Block block = BlockGenerator.createChildBlock(genesis);

        block.getHeader().setDifficulty(new byte[]{0x00});

        BlockValidatorImpl validator = new BlockValidatorBuilder().addDifficultyRule().blockStore(new SimpleBlockStore(block)).build();
        // If the parent difficulty is zero, the child difficulty will always be zero
        // because the child  difficulty is always the parent diff multiplied by a factor.
        // However, the calcDifficulty will put the minimum configured difficulty, so that the child
        // difficulty can never be zero.
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidChildBlockBadGasLimit() {
        BlockChainImpl blockchain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        Block block1 = BlockGenerator.createChildBlock(genesis);
        Block block2 = BlockGenerator.createChildBlock(block1);
        Block parent = BlockGenerator.createChildBlock(block2);

        parent.getHeader().setGasLimit(new byte[]{0x00});
        Block block = BlockGenerator.createChildBlock(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentGasLimitRule().blockStore(blockchain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidBlockWithoutParent() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        Block block1 = BlockGenerator.createChildBlock(genesis);
        Block block2 = BlockGenerator.createChildBlock(block1);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentBlockHeaderValidator().blockStore(blockChain.getBlockStore())
                .addBlockRootValidationRule().addBlockUnclesValidationRule(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block2));
    }

    @Test
    public void validEmptyUnclesHash() {
        BlockChainImpl blockchain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        Block block1 = BlockGenerator.createChildBlock(genesis);

        BlockStore validatorStore = blockchain.getBlockStore();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(validatorStore).blockStore(validatorStore).build();

        Assert.assertTrue(validator.isValid(block1));
    }


    @Test
    public void invalidUnclesHash() {
        BlockChainImpl blockchain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockchain);
        Block block1 = BlockGenerator.createChildBlock(genesis);
        block1.getHeader().setUnclesHash(new byte[]{0x01});

        BlockStore validatorStore = blockchain.getBlockStore();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(validatorStore).blockStore(validatorStore).build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void validateHeader() {

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        Block parent = new BlockBuilder().parent(genesis).build();
        Block block = new BlockBuilder().parent(parent).build();

        blockChain.getBlockStore().saveBlock(parent, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentBlockHeaderValidator().blockStore(blockChain.getBlockStore())
                .addBlockRootValidationRule().addBlockUnclesValidationRule(blockChain.getBlockStore()).build();

        Assert.assertTrue(validator.isValid(block));
    }

    @Test
    public void getGenesisEmptyAncestorSet() {
        Block genesis = BlockGenerator.getGenesisBlock();
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Assert.assertTrue(FamilyUtils.getAncestors(blockChain.getBlockStore(), genesis, 6).isEmpty());
    }

    @Test
    public void getBlockOneAncestorSet() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.getBlockStore().saveBlock(genesis, BigInteger.ONE, true);
        Block block = BlockGenerator.createChildBlock(genesis);

        Set<ByteArrayWrapper> ancestors = FamilyUtils.getAncestors(blockChain.getBlockStore(), block, 6);
        Assert.assertFalse(ancestors.isEmpty());
        Assert.assertTrue(ancestors.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertFalse(ancestors.contains(new ByteArrayWrapper(block.getHash())));
    }

    @Test
    public void getThreeAncestorSet() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.getBlockStore().saveBlock(genesis, BigInteger.ONE, true);
        Block block1 = BlockGenerator.createChildBlock(genesis);
        blockChain.getBlockStore().saveBlock(block1, BigInteger.ONE, true);
        Block block2 = BlockGenerator.createChildBlock(block1);
        blockChain.getBlockStore().saveBlock(block2, BigInteger.ONE, true);
        Block block3 = BlockGenerator.createChildBlock(block2);
        blockChain.getBlockStore().saveBlock(block3, BigInteger.ONE, true);
        Block block4 = BlockGenerator.createChildBlock(block3);
        blockChain.getBlockStore().saveBlock(block4, BigInteger.ONE, true);
        Block block5 = BlockGenerator.createChildBlock(block4);
        blockChain.getBlockStore().saveBlock(block5, BigInteger.ONE, true);

        Set<ByteArrayWrapper> ancestors = FamilyUtils.getAncestors(blockChain.getBlockStore(), block5, 3);
        Assert.assertFalse(ancestors.isEmpty());
        Assert.assertEquals(3, ancestors.size());
        Assert.assertFalse(ancestors.contains(new ByteArrayWrapper(genesis.getHash())));
        Assert.assertFalse(ancestors.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertTrue(ancestors.contains(new ByteArrayWrapper(block2.getHash())));
        Assert.assertTrue(ancestors.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertTrue(ancestors.contains(new ByteArrayWrapper(block4.getHash())));
        Assert.assertFalse(ancestors.contains(new ByteArrayWrapper(block5.getHash())));
    }

    @Test
    public void getUsedUncles() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle1b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        Block uncle2a = BlockGenerator.createChildBlock(genesis);
        Block uncle2b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());
        Block block2 = BlockGenerator.createChildBlock(block1, null, uncles2, 1, null);

        Block block3 = BlockGenerator.createChildBlock(block2, null, uncles2, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);

        Set<ByteArrayWrapper> used = FamilyUtils.getUsedUncles(store, block3, 6);

        Assert.assertFalse(used.isEmpty());
        Assert.assertFalse(used.contains(new ByteArrayWrapper(block3.getHash())));
        Assert.assertFalse(used.contains(new ByteArrayWrapper(block2.getHash())));
        Assert.assertTrue(used.contains(new ByteArrayWrapper(uncle2a.getHash())));
        Assert.assertTrue(used.contains(new ByteArrayWrapper(uncle2b.getHash())));
        Assert.assertFalse(used.contains(new ByteArrayWrapper(block1.getHash())));
        Assert.assertTrue(used.contains(new ByteArrayWrapper(uncle1a.getHash())));
        Assert.assertTrue(used.contains(new ByteArrayWrapper(uncle1b.getHash())));
        Assert.assertFalse(used.contains(new ByteArrayWrapper(genesis.getHash())));
    }

    @Test
    public void invalidUncleTransactionsRoot() {
        Block block = BlockGenerator.createBlock(2, 10);

        block.getHeader().setTransactionsRoot(new byte[]{0x01});

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockRootValidationRule().build();
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void validUncles() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle1b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis);
        Block block2 = BlockGenerator.createChildBlock(block1, null, uncles1, 1, null);

        blockChain.tryToConnect(genesis);
        blockChain.tryToConnect(block1);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        blockChain.tryToConnect(block2);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertTrue(validator.isValid(block1));
    }

    @Test
    public void invalidSiblingUncles() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle1b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        blockChain.tryToConnect(genesis);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        blockChain.tryToConnect(block1);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUnclesUncleIncludedMultipeTimes () {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1a.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        blockChain.tryToConnect(genesis);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        blockChain.tryToConnect(block1);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidPOWUncles() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.getBlock(1);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        blockChain.tryToConnect(genesis);
        store.saveBlock(uncle1a, BigInteger.ONE, false);

        BlockParentDependantValidationRule parentValidationRule = Mockito.mock(BlockParentDependantValidationRule.class);
        Mockito.when(parentValidationRule.isValid(Mockito.any(), Mockito.any())).thenReturn(true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store, new ProofOfWorkRule(), parentValidationRule)
                                            .blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleIsAncestor() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(genesis.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }

    @Test
    public void invalidUncleHasNoSavedParent() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(BlockGenerator.createChildBlock(genesis));
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }


    @Test
    public void invalidUncleHasNoCommonAncestor() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();


        Block uncle1a = BlockGenerator.createChildBlock(new SimpleBlock(null, null, new byte[]{12, 12}, null, BigInteger.ONE.toByteArray(),
                0, null, 0L, 0L, new byte[]{}, null, null, null, new byte[]{1, 2}, null, null, null));

        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block1));
    }


    @Test
    public void invalidUncleHasParentThatIsNotAncestor() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle2a = BlockGenerator.createChildBlock(uncle1a);

        List<BlockHeader> uncles3 = new ArrayList<>();
        uncles3.add(uncle2a.getHeader());
        uncles3.add(uncle1a.getHeader());

        Block block1 = BlockGenerator.createChildBlock(genesis, null, null, 1, null);
        Block block2 = BlockGenerator.createChildBlock(block1, null, null, 1, null);
        Block block3 = BlockGenerator.createChildBlock(block2, null, uncles3, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(block2, BigInteger.ONE, true);
        store.saveBlock(block3, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void invalidUncleAlreadyUsed() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();

        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle1b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles1 = new ArrayList<>();
        uncles1.add(uncle1a.getHeader());
        uncles1.add(uncle1b.getHeader());
        Block block1 = BlockGenerator.createChildBlock(genesis, null, uncles1, 1, null);

        Block uncle2a = BlockGenerator.createChildBlock(genesis);
        Block uncle2b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());
        Block block2 = BlockGenerator.createChildBlock(block1, null, uncles2, 1, null);

        Block block3 = BlockGenerator.createChildBlock(block2, null, uncles2, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);
        store.saveBlock(block2, BigInteger.ONE, true);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block3));
    }

    @Test
    public void tooManyUncles() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        Block genesis = BlockGenerator.getGenesisBlock();


        Block uncle1a = BlockGenerator.createChildBlock(genesis);
        Block uncle1b = BlockGenerator.createChildBlock(genesis);
        Block block1 = BlockGenerator.createChildBlock(genesis, null, null, 1, null);

        Block uncle2a = BlockGenerator.createChildBlock(genesis);
        Block uncle2b = BlockGenerator.createChildBlock(genesis);
        List<BlockHeader> uncles2 = new ArrayList<>();
        uncles2.add(uncle2a.getHeader());
        uncles2.add(uncle2b.getHeader());
        uncles2.add(uncle1a.getHeader());
        uncles2.add(uncle1b.getHeader());
        for(int i = 0; i < 10; i++) {
            uncles2.add(BlockGenerator.createChildBlock(genesis).getHeader());
        }

        Block block2 = BlockGenerator.createChildBlock(block1, null, uncles2, 1, null);

        store.saveBlock(genesis, BigInteger.ONE, true);
        store.saveBlock(uncle1a, BigInteger.ONE, false);
        store.saveBlock(uncle1b, BigInteger.ONE, false);
        store.saveBlock(block1, BigInteger.ONE, true);
        store.saveBlock(uncle2a, BigInteger.ONE, false);
        store.saveBlock(uncle2b, BigInteger.ONE, false);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockUnclesValidationRule(store).blockStore(store).build();

        Assert.assertFalse(validator.isValid(block2));
    }

    @Test
    public void twoTransactionsSameNonce() {
        BlockExecutorTest.TestObjects objects = BlockExecutorTest.generateBlockWithOneTransaction();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = objects.getTransaction();
        txs.add(tx);
        txs.add(tx);

        Block genesis = BlockGenerator.getGenesisBlock();
        Block block = BlockGenerator.createChildBlock(genesis);
        block.setTransactionsList(txs);


        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockTxsValidationRule(objects.getRepository()).build();

        Assert.assertFalse(validator.isValid(block));
    }


    @Test
    public void processBlockWithInvalidMGPTxs() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);


        Block parent = new BlockBuilder().minGasPrice(BigInteger.ZERO)
                .parent(BlockGenerator.getGenesisBlock()).build();


        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN);
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);
        Block block = new BlockBuilder().minGasPrice(BigInteger.TEN).transactions(txs)
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addTxsMinGasPriceRule().blockStore(blockStore).build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void processBlockWithInvalidPrevMGP() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);


        Block parent = new BlockBuilder().minGasPrice(BigInteger.ZERO)
                .parent(BlockGenerator.getGenesisBlock()).build();

        Block block = new BlockBuilder().minGasPrice(BigInteger.TEN)
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addPrevMinGasPriceRule().blockStore(blockStore).build();


        Assert.assertFalse(validator.isValid(block));
    }


    @Test
    public void processValidMGPBlock() {
        BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Repository repository = Mockito.mock(Repository.class);

        Mockito.when(repository.getSnapshotTo(Mockito.any())).thenReturn(repository);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder().minGasPrice(BigInteger.TEN)
                .parent(BlockGenerator.getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);

        Block block = new BlockBuilder().transactions(txs).minGasPrice(BigInteger.valueOf(11L))
                .parent(parent).build();

        Mockito.when(blockStore.getBlockByHash(block.getParentHash())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder().addPrevMinGasPriceRule()
                .addTxsMinGasPriceRule().blockStore(blockStore).build();

        Assert.assertTrue(validator.isValid(block));

    }

    @Test
    public void parentInvalidNumber() {

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        Block block = new BlockBuilder().parent(genesis).build();
        block.getHeader().setNumber(25L);
        BlockValidatorImpl validator = new BlockValidatorBuilder().addParentNumberRule().blockStore(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void invalidTxNonce() {

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.TEN, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        block.getHeader().setNumber(25L);
        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockTxsValidationRule(blockChain.getRepository()).blockStore(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void noRemascTx() {

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addRemascValidationRule().blockStore(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder().parent(genesis).transactions(null).build();
        Assert.assertFalse(validator.isValid(block));

        block = new BlockBuilder().parent(genesis).transactions(new ArrayList<>()).build();
        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void remascTxNotInLastPosition() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        txs.add(tx);
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addRemascValidationRule().blockStore(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));
    }

    @Test
    public void remascTx() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction.create("06", BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(12L), BigInteger.TEN);
        tx.sign(new byte[]{});
        txs.add(tx);
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        Block block = new BlockBuilder().parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder().addRemascValidationRule().blockStore(blockChain.getBlockStore()).build();

        Assert.assertTrue(validator.isValid(block));
    }


    @Test
    public void blockInTheFuture() {
        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();

        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.tryToConnect(genesis);

        int validPeriod = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getNewBlockMaxMinInTheFuture();

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2*validPeriod);

        Mockito.when(block.getParentHash()).thenReturn(genesis.getHash());

        BlockValidatorImpl validator = new BlockValidatorBuilder().addBlockTimeStampValidationRule(validPeriod)
                                            .blockStore(blockChain.getBlockStore()).build();

        Assert.assertFalse(validator.isValid(block));

        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + validPeriod);

        Assert.assertTrue(validator.isValid(block));
    }

    private static class SimpleBlockStore implements BlockStore {
        private Block block;

        public SimpleBlockStore(Block block) {
            this.block = block;
        }

        @Override
        public byte[] getBlockHashByNumber(long blockNumber) {
            return new byte[0];
        }

        @Override
        public byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash) {
            return new byte[0];
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
        public Block getBlockByHash(byte[] hash) {
            return block;
        }

        @Override
        public Block getBlockByHashAndDepth(byte[] hash, long depth) {
            return null;
        }

        @Override
        public boolean isBlockExist(byte[] hash) {
            return false;
        }

        @Override
        public List<byte[]> getListHashesEndWith(byte[] hash, long qty) {
            return null;
        }

        @Override
        public List<BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {
            return null;
        }

        @Override
        public List<Block> getListBlocksEndWith(byte[] hash, long qty) {
            return null;
        }

        @Override
        public void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain) {

        }

        @Override
        public BigInteger getTotalDifficultyForHash(byte[] hash) {
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
