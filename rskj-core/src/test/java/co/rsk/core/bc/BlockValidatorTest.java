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

import co.rsk.bitcoinj.core.BitcoinSerializer;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.MessageSerializer;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.TimeProvider;
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 04/08/2016.
 */
class BlockValidatorTest {

    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    void validEmptyUnclesHash() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(null)
                .build();

        Assertions.assertTrue(validator.isValid(block1));
    }

    @Test
    void invalidUnclesHash() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        TestUtils.setInternalState(block1.getHeader(), "unclesHash", new byte[]{0x01});

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockUnclesValidationRule(null)
                .build();

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void getBlockOneAncestorSet() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        store.saveBlock(genesis, TEST_DIFFICULTY, true);
        Block block = new BlockGenerator().createChildBlock(genesis);

        Set<Keccak256> ancestors = FamilyUtils.getAncestors(store, block, 6);
        Assertions.assertFalse(ancestors.isEmpty());
        Assertions.assertTrue(ancestors.contains(genesis.getHash()));
        Assertions.assertFalse(ancestors.contains(block.getHash()));
    }

    @Test
    void getThreeAncestorSet() {
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
        Assertions.assertFalse(ancestors.isEmpty());
        Assertions.assertEquals(3, ancestors.size());
        Assertions.assertFalse(ancestors.contains(genesis.getHash()));
        Assertions.assertFalse(ancestors.contains(block1.getHash()));
        Assertions.assertTrue(ancestors.contains(block2.getHash()));
        Assertions.assertTrue(ancestors.contains(block3.getHash()));
        Assertions.assertTrue(ancestors.contains(block4.getHash()));
        Assertions.assertFalse(ancestors.contains(block5.getHash()));
    }

    @Test
    void getUsedUncles() {
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

        Assertions.assertFalse(used.isEmpty());
        Assertions.assertFalse(used.contains(block3.getHash()));
        Assertions.assertFalse(used.contains(block2.getHash()));
        Assertions.assertTrue(used.contains(uncle2a.getHash()));
        Assertions.assertTrue(used.contains(uncle2b.getHash()));
        Assertions.assertFalse(used.contains(block1.getHash()));
        Assertions.assertTrue(used.contains(uncle1a.getHash()));
        Assertions.assertTrue(used.contains(uncle1b.getHash()));
        Assertions.assertFalse(used.contains(genesis.getHash()));
    }

    @Test
    void invalidUncleTransactionsRoot() {
        Block block = new BlockGenerator().createBlock(2, 10);

        block.getHeader().setTransactionsRoot(new byte[]{0x01});

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addBlockRootValidationRule()
                .build();

        Assertions.assertFalse(validator.isValid(block));
    }

    @Test
    void validUncles() {
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

        Assertions.assertTrue(validator.isValid(block1));
    }

    @Test
    void invalidSiblingUncles() {
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidUnclesUncleIncludedMultipeTimes () {
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidPOWUncles() {
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidUncleIsAncestor() {
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidUncleHasNoSavedParent() {
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidUncleHasNoCommonAncestor() {
        IndexedBlockStore store = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

        BlockGenerator blockGenerator = new BlockGenerator();

        Block genesis = blockGenerator.getGenesisBlock();

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setCoinbase(TestUtils.generateAddress("nhCoinbase"))
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

        Assertions.assertFalse(validator.isValid(block1));
    }

    @Test
    void invalidUncleHasParentThatIsNotAncestor() {
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

        Assertions.assertFalse(validator.isValid(block3));
    }

    @Test
    void invalidUncleAlreadyUsed() {
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

        Assertions.assertFalse(validator.isValid(block3));
    }

    @Test
    void tooManyUncles() {
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

        Assertions.assertFalse(validator.isValid(block2));
    }

    @Test
    void processBlockWithInvalidMGPTxs() {
        BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        Repository repository = mock(Repository.class);

        when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder(null, null, null).minGasPrice(BigInteger.ZERO)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(chainId)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);
        Block block = new BlockBuilder(null, null, null).minGasPrice(BigInteger.TEN).transactions(txs)
                .parent(parent).build();

        when(blockStore.getBlockByHash(block.getParentHash().getBytes())).thenReturn(parent);

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addTxsMinGasPriceRule()
                .blockStore(blockStore)
                .build();

        Assertions.assertFalse(validator.isValid(block));
    }

    @Test
    void processBlockWithInvalidPrevMGP() {
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

        Assertions.assertFalse(validator.isValid(block));
    }

    @Test
    void processValidMGPBlock() {
        BlockStore blockStore = mock(org.ethereum.db.BlockStore.class);
        Repository repository = mock(Repository.class);

        when(repository.getNonce(Mockito.any())).thenReturn(BigInteger.ZERO);

        Block parent = new BlockBuilder(null, null, null).minGasPrice(BigInteger.TEN)
                .parent(new BlockGenerator().getGenesisBlock()).build();

        List<Transaction> txs = new ArrayList<>();
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(12L))
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(chainId)
                .value(BigInteger.ZERO)
                .build();
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

        Assertions.assertTrue(validator.isValid(block));
    }

    @Test
    void noRemascTx() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(12L))
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(chainId)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(new byte[]{});
        txs.add(tx);
        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assertions.assertFalse(validator.isValid(block));

        block = new BlockBuilder(null, null, null).parent(genesis).transactions(null).build();
        Assertions.assertFalse(validator.isValid(block));

        block = new BlockBuilder(null, null, null).parent(genesis).transactions(new ArrayList<>()).build();
        Assertions.assertFalse(validator.isValid(block));
    }

    @Test
    void remascTxNotInLastPosition() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(12L))
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(chainId)
                .value(BigInteger.ZERO)
                .build();

        tx.sign(new byte[]{});
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        txs.add(tx);

        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();

        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assertions.assertFalse(validator.isValid(block));
    }

    @Test
    void remascTx() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(12L))
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(chainId)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(new byte[]{});
        txs.add(tx);
        txs.add(new RemascTransaction(BigInteger.ONE.longValue()));
        Block block = new BlockBuilder(null, null, null).parent(genesis).transactions(txs).build();
        BlockValidatorImpl validator = new BlockValidatorBuilder()
                .addRemascValidationRule()
                .build();

        Assertions.assertTrue(validator.isValid(block));
    }

    @Test
    void blockInTheFuture() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        byte[] bitcoinMergedMiningHeader = new byte[0];
        int validPeriod = 6000;
        long baseTimeStamp = 1627932722L; // some random timestamp (taken from Sys.currentTimeMills())

        BlockHeader header = mock(BlockHeader.class);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(bitcoinMergedMiningHeader);
        when(header.getTimestamp())
                .thenReturn(baseTimeStamp + 2 * validPeriod);
        when(header.getParentHash()).thenReturn(genesis.getHash());

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(header);

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getTimeSeconds()).thenReturn(baseTimeStamp + validPeriod - 100); // a close enough block

        MessageSerializer messageSerializer = mock(BitcoinSerializer.class);
        when(messageSerializer.makeBlock(bitcoinMergedMiningHeader)).thenReturn(btcBlock);

        NetworkParameters bitcoinNetworkParameters = mock(NetworkParameters.class);
        when(bitcoinNetworkParameters.getDefaultSerializer()).thenReturn(messageSerializer);

        // Before Iris
        blockTimeStampValidation(validPeriod, baseTimeStamp, header,
                block, bitcoinNetworkParameters, false);

        //After Iris
        blockTimeStampValidation(validPeriod, baseTimeStamp, header,
                block, bitcoinNetworkParameters, true);
    }

    private TestSystemProperties blockTimeStampValidationProperties(boolean activateIris) {
        return new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300",
                        ConfigValueFactory.fromAnyRef(activateIris ? 0 : -1))
        );
    }

    private void blockTimeStampValidation(int validPeriod, long baseTimeStamp, BlockHeader header, Block block,
                                          NetworkParameters bitcoinNetworkParameters, boolean irisEnabled) {
        TestSystemProperties testSystemProperties = blockTimeStampValidationProperties(irisEnabled);
        TimeProvider timeProvider = mock(TimeProvider.class);
        when(timeProvider.currentTimeMillis()).thenReturn(baseTimeStamp * 1000 + validPeriod);

        BlockValidatorImpl validator = new BlockValidatorBuilder(testSystemProperties)
                .addBlockTimeStampValidation(validPeriod, timeProvider, bitcoinNetworkParameters)
                .build();

        when(header.getTimestamp())
                .thenReturn(baseTimeStamp + 2 * validPeriod);

        Assertions.assertFalse(validator.isValid(block));

        when(header.getTimestamp())
                .thenReturn(baseTimeStamp + validPeriod);

        Assertions.assertTrue(validator.isValid(block));
    }

    @Test
    void blockInTheFutureIsAcceptedWhenValidPeriodIsZero() {
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

        Assertions.assertTrue(validator.isValid(block));

        when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2000);

        Assertions.assertTrue(validator.isValid(block));
    }
}


