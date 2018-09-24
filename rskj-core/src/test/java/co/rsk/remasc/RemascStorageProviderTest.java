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

package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryImplForTesting;
import co.rsk.peg.PegTestUtils;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieImpl;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;

import org.ethereum.config.blockchain.mainnet.MainNetAfterBridgeSyncConfig;
import org.ethereum.config.blockchain.mainnet.MainNetOrchidConfig;
import org.ethereum.config.blockchain.testnet.TestNetBeforeBridgeSyncConfig;
import org.ethereum.config.blockchain.testnet.TestNetDifficultyDropEnabledConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Created by usuario on 13/04/2017.
 */
public class RemascStorageProviderTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private ECKey cowKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes()));
    private Coin cowInitialBalance = new Coin(new BigInteger("1000000000000000000"));
    private long initialGasLimit = 10000000L;
    private byte[] cowAddress = cowKey.getAddress();
    private Map<byte[], BigInteger> preMineMap = Collections.singletonMap(cowAddress, cowInitialBalance.asBigInteger());
    private Genesis genesisBlock = (Genesis) (new BlockGenerator()).getNewGenesisBlock(initialGasLimit, preMineMap);

    private void validateRemascsStorageIsCorrect(RemascStorageProvider provider, Coin expectedRewardBalance, Coin expectedBurnedBalance, long expectedSiblingsSize) {
        assertEquals(expectedRewardBalance, provider.getRewardBalance());
        assertEquals(expectedBurnedBalance, provider.getBurnedBalance());
        assertEquals(expectedSiblingsSize, provider.getSiblings().size());
    }

    private RemascStorageProvider getRemascStorageProvider(Blockchain blockchain) throws IOException {
        return new RemascStorageProvider(blockchain.getRepository(), PrecompiledContracts.REMASC_ADDR);
    }

    private List<Block> createSimpleBlocks(Block parent, int size, RskAddress coinbase) {
        List<Block> chain = new ArrayList<>();

        while (chain.size() < size) {
            Block newblock = RemascTestRunner.createBlock(this.genesisBlock, parent, PegTestUtils.createHash3(),
                                                          coinbase, null, null);
            chain.add(newblock);
            parent = newblock;
        }

        return chain;
    }

    private RskAddress randomAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return new RskAddress(bytes);
    }

    @Test
    public void getDefautRewardBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.ZERO, provider.getRewardBalance());
    }

    @Test
    public void setAndGetRewardBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(1));

        Assert.assertEquals(Coin.valueOf(1), provider.getRewardBalance());
    }

    @Test
    public void setSaveRetrieveAndGetRewardBalance() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.valueOf(255), newProvider.getRewardBalance());
    }

    @Test
    public void getDefautBurnedBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.ZERO, provider.getBurnedBalance());
    }

    @Test
    public void setAndGetBurnedBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(1));

        Assert.assertEquals(Coin.valueOf(1), provider.getBurnedBalance());
    }

    @Test
    public void setSaveRetrieveAndGetBurnedBalance() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.valueOf(255), newProvider.getBurnedBalance());
    }

    @Test
    public void getDefaultBrokenSelectionRule() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Boolean.FALSE, provider.getBrokenSelectionRule());
    }

    @Test
    public void setAndGetBrokenSelectionRule() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        Assert.assertEquals(Boolean.TRUE, provider.getBrokenSelectionRule());
    }

    @Test
    public void setSaveRetrieveAndGetBrokenSelectionRule() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Boolean.TRUE, newProvider.getBrokenSelectionRule());
    }

    @Test
    public void getDefaultSiblings() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = provider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void setAndGetSiblings() {
        RskAddress accountAddress = randomAddress();
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block.getHeader(), block.getCoinbase(), 2);

        List<Sibling> siblings = new ArrayList<>();
        siblings.add(sibling1);
        siblings.add(sibling2);

        provider.getSiblings().put(Long.valueOf(1), siblings);

        SortedMap<Long, List<Sibling>> map = provider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));

        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
    }

    @Test
    public void setSaveRetrieveAndGetSiblings() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block.getHeader(), block.getCoinbase(), 2);

        List<Sibling> siblings = new ArrayList<>();
        siblings.add(sibling1);
        siblings.add(sibling2);

        provider.getSiblings().put(Long.valueOf(1), siblings);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = newProvider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));

        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
    }

    @Test
    public void setSaveRetrieveAndGetManySiblings() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block1.getHeader(), block1.getCoinbase(), 2);
        Sibling sibling3 = new Sibling(block2.getHeader(), block2.getCoinbase(), 3);
        Sibling sibling4 = new Sibling(block3.getHeader(), block3.getCoinbase(), 4);
        Sibling sibling5 = new Sibling(block4.getHeader(), block4.getCoinbase(), 5);
        Sibling sibling6 = new Sibling(block5.getHeader(), block5.getCoinbase(), 6);

        List<Sibling> siblings0 = new ArrayList<>();
        List<Sibling> siblings1 = new ArrayList<>();
        List<Sibling> siblings2 = new ArrayList<>();

        siblings0.add(sibling1);
        siblings0.add(sibling2);

        siblings1.add(sibling3);
        siblings1.add(sibling4);

        siblings2.add(sibling5);
        siblings2.add(sibling6);

        provider.getSiblings().put(Long.valueOf(0), siblings0);
        provider.getSiblings().put(Long.valueOf(1), siblings1);
        provider.getSiblings().put(Long.valueOf(2), siblings2);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = newProvider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());

        Assert.assertTrue(map.containsKey(Long.valueOf(0)));
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));
        Assert.assertTrue(map.containsKey(Long.valueOf(2)));

        Assert.assertEquals(2, map.get(Long.valueOf(0)).size());
        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
        Assert.assertEquals(2, map.get(Long.valueOf(2)).size());

        List<Sibling> list0 = map.get(Long.valueOf(0));
        List<Sibling> list1 = map.get(Long.valueOf(1));
        List<Sibling> list2 = map.get(Long.valueOf(2));

        Assert.assertEquals(1, list0.get(0).getIncludedHeight());
        Assert.assertArrayEquals(genesis.getHeader().getHash().getBytes(), list0.get(0).getHash());
        Assert.assertEquals(2, list0.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block1.getHeader().getHash().getBytes(), list0.get(1).getHash());

        Assert.assertEquals(3, list1.get(0).getIncludedHeight());
        Assert.assertArrayEquals(block2.getHeader().getHash().getBytes(), list1.get(0).getHash());
        Assert.assertEquals(4, list1.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block3.getHeader().getHash().getBytes(), list1.get(1).getHash());

        Assert.assertEquals(5, list2.get(0).getIncludedHeight());
        Assert.assertArrayEquals(block4.getHeader().getHash().getBytes(), list2.get(0).getHash());
        Assert.assertEquals(6, list2.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block5.getHeader().getHash().getBytes(), list2.get(1).getHash());
    }

    @Test
    public void setSaveRetrieveAndGetSiblingsBeforeRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetBeforeBridgeSyncConfig());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(((TestNetBeforeBridgeSyncConfig) blockchainConfig).isRskip85()).thenReturn(false);
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        List<SiblingElement> siblings = Arrays.asList(new SiblingElement(5, 6, minerFee), new SiblingElement(10, 11, minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(testRunner.getBlockChain()), Coin.valueOf(0), Coin.valueOf(0L), 1L);
    }

    @Test
    public void setSaveRetrieveAndGetSiblingsAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = new TestNetDifficultyDropEnabledConfig();
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        List<SiblingElement> siblings = Arrays.asList(
                new SiblingElement(5, 6, minerFee),
                new SiblingElement(10, 11, minerFee)
        );

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(
                this.getRemascStorageProvider(testRunner.getBlockChain()),
                Coin.valueOf(0L),
                Coin.valueOf(0L),
                0L
        );
    }

    @Test
    public void hasSiblingsStoredBeforeRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = new MainNetAfterBridgeSyncConfig();
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        List<SiblingElement> siblings = Arrays.asList(new SiblingElement(5, 6, minerFee), new SiblingElement(10, 11, minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        Assert.assertFalse(this.getRemascStorageProvider(testRunner.getBlockChain()).getSiblings().isEmpty());
    }

    @Test
    public void noSiblingsStoredAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = new MainNetOrchidConfig();
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        List<SiblingElement> siblings = Arrays.asList(new SiblingElement(5, 6, minerFee), new SiblingElement(10, 11, minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        Assert.assertTrue(this.getRemascStorageProvider(testRunner.getBlockChain()).getSiblings().isEmpty());
    }

    @Test
    public void alwaysPaysBeforeRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetBeforeBridgeSyncConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((TestNetBeforeBridgeSyncConfig) blockchainConfig).isRskip85()).thenReturn(false);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);

        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(testRunner.getBlockChain()), Coin.valueOf(84000), Coin.valueOf(0L), 0L);
    }

    @Test
    public void alwaysPaysFedBeforeRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetBeforeBridgeSyncConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((TestNetBeforeBridgeSyncConfig) blockchainConfig).isRskip85()).thenReturn(false);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);

        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Repository repository = testRunner.getBlockChain().getRepository();
        RemascFederationProvider federationProvider = new RemascFederationProvider(config, repository, testRunner.getBlockChain().getBestBlock());
        assertEquals(Coin.valueOf(0), this.getRemascStorageProvider(testRunner.getBlockChain()).getFederationBalance());
        long federatorBalance = (168 / federationProvider.getFederationSize()) * 2;
        assertEquals(Coin.valueOf(federatorBalance), RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    public void doesntPayFedBelowMinimumRewardAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetDifficultyDropEnabledConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(0));
        when(constants.getFederatorMinimumPayableGas()).thenReturn(BigInteger.valueOf(10000L));
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);
        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Repository repository = testRunner.getBlockChain().getRepository();
        RemascFederationProvider federationProvider = new RemascFederationProvider(config, repository, testRunner.getBlockChain().getBestBlock());
        assertEquals(Coin.valueOf(336), this.getRemascStorageProvider(testRunner.getBlockChain()).getFederationBalance());
        assertEquals(null, RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    public void doesntPayBelowMinimumRewardAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetDifficultyDropEnabledConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(10000L));
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);
        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(testRunner.getBlockChain()), Coin.valueOf(126000), Coin.valueOf(0L), 0L);
    }

    @Test
    public void paysFedWhenHigherThanMinimumRewardAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetDifficultyDropEnabledConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(0));
        when(constants.getFederatorMinimumPayableGas()).thenReturn(BigInteger.valueOf(10L));
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 10L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Repository repository = testRunner.getBlockChain().getRepository();
        RemascFederationProvider federationProvider = new RemascFederationProvider(config, repository, testRunner.getBlockChain().getBestBlock());
        long federatorBalance = (1680 / federationProvider.getFederationSize()) * 2;
        assertEquals(Coin.valueOf(0), this.getRemascStorageProvider(testRunner.getBlockChain()).getFederationBalance());
        assertEquals(Coin.valueOf(federatorBalance), RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    public void paysWhenHigherThanMinimumRewardAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetDifficultyDropEnabledConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(21000L));
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 10L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(testRunner.getBlockChain()), Coin.valueOf(840000L), Coin.valueOf(0L), 0L);
    }

    @Test
    public void paysOnlyBlocksWithEnoughBalanceAccumulatedAfterRFS() throws IOException {
        RskSystemProperties config = spy(new TestSystemProperties());
        BlockchainNetConfig blockchainConfig = spy(new TestNetDifficultyDropEnabledConfig());
        Constants constants = spy(new TestNetBeforeBridgeSyncConfig.TestNetConstants());
        when(config.getBlockchainConfig()).thenReturn(blockchainConfig);
        when(blockchainConfig.getCommonConstants()).thenReturn(constants);
        when(((BlockchainConfig)blockchainConfig).getConstants()).thenReturn(constants);
        when(blockchainConfig.getConfigForBlock(anyLong())).thenReturn((BlockchainConfig)blockchainConfig);
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn((byte)33);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(21000L));
        long txValue = 10000;
        long gasLimit = 100000L;
        long gasPrice = 10L;
        long lowGasPrice = 1L;
        long minerFee = 21000;

        RskAddress coinbase = randomAddress();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(13).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);
        testRunner.setFixedCoinbase(coinbase);
        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        List<Block> blocks = new ArrayList<>();
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blockchain.getBestBlock(), PegTestUtils.createHash3(),
                coinbase, null, gasLimit, gasPrice, 14, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, null, gasLimit, lowGasPrice, 15, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, null, gasLimit, gasPrice, 16, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, null, gasLimit, lowGasPrice, 17, txValue, cowKey, null));

        blocks.addAll(createSimpleBlocks(blocks.get(blocks.size()-1),10, coinbase));

        Repository repository = blockchain.getRepository();
        BlockStore blockStore = blockchain.getBlockStore();
        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        BlockExecutor blockExecutor = new BlockExecutor(repository, (tx, txindex, coinbase1, track, block, totalGasUsed) -> new TransactionExecutor(
                tx,
                txindex,
                block.getCoinbase(),
                track,
                blockStore,
                null,
                programInvokeFactory,
                block,
                null,
                totalGasUsed,
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

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(b));

            long blockNumber = blockchain.getBestBlock().getNumber();
            if (blockNumber == 24){ // before first special block
                assertEquals(Coin.valueOf(1663200L), RemascTestRunner.getAccountBalance(blockchain.getRepository(), coinbase));
            } else if (blockNumber == 25 || blockNumber == 26){ // after first and second special block
                assertEquals(Coin.valueOf(1829520L), RemascTestRunner.getAccountBalance(blockchain.getRepository(), coinbase));
            } else if (blockNumber == 27 || blockNumber == 28){ // after third and fourth special block
                assertEquals(Coin.valueOf(1999167L), RemascTestRunner.getAccountBalance(blockchain.getRepository(), coinbase));
            }
        }
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
    }
}
