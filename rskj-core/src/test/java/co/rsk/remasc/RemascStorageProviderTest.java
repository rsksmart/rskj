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

import static co.rsk.RskTestUtils.createRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.*;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.Trie;
import java.math.BigInteger;
import java.util.*;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by usuario on 13/04/2017.
 */
class RemascStorageProviderTest {

    private final ECKey cowKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes()));
    private final Coin cowInitialBalance = new Coin(new BigInteger("1000000000000000000"));
    private final long initialGasLimit = 10000000L;
    private final byte[] cowAddress = cowKey.getAddress();
    private final Map<byte[], BigInteger> preMineMap = Collections.singletonMap(cowAddress, cowInitialBalance.asBigInteger());
    private final Genesis genesisBlock = (Genesis) (new BlockGenerator()).getNewGenesisBlock(initialGasLimit, preMineMap);

    private void validateRemascsStorageIsCorrect(RemascStorageProvider provider, Coin expectedRewardBalance, Coin expectedBurnedBalance, long expectedSiblingsSize) {
        assertEquals(expectedRewardBalance, provider.getRewardBalance());
        assertEquals(expectedBurnedBalance, provider.getBurnedBalance());
    }

    private RemascStorageProvider getRemascStorageProvider(RepositorySnapshot repository) {
        return new RemascStorageProvider(repository.startTracking(), PrecompiledContracts.REMASC_ADDR);
    }

    private List<Block> createSimpleBlocks(Block parent, int size, RskAddress coinbase) {
        List<Block> chain = new ArrayList<>();

        while (chain.size() < size) {
            Block newblock = RemascTestRunner.createBlock(this.genesisBlock, parent, PegTestUtils.createHash3(),
                                                          coinbase, Collections.emptyList(), null);
            chain.add(newblock);
            parent = newblock;
        }

        return chain;
    }



    @Test
    void getDefautRewardBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Coin.ZERO, provider.getRewardBalance());
    }

    @Test
    void setAndGetRewardBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(1));

        Assertions.assertEquals(Coin.valueOf(1), provider.getRewardBalance());
    }

    @Test
    void setSaveRetrieveAndGetRewardBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Coin.valueOf(255), newProvider.getRewardBalance());
    }

    @Test
    void getDefautBurnedBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Coin.ZERO, provider.getBurnedBalance());
    }

    @Test
    void setAndGetBurnedBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(1));

        Assertions.assertEquals(Coin.valueOf(1), provider.getBurnedBalance());
    }

    @Test
    void setSaveRetrieveAndGetBurnedBalance() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Coin.valueOf(255), newProvider.getBurnedBalance());
    }

    @Test
    void getDefaultBrokenSelectionRule() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Boolean.FALSE, provider.getBrokenSelectionRule());
    }

    @Test
    void setAndGetBrokenSelectionRule() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = createRepository();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        Assertions.assertEquals(Boolean.TRUE, provider.getBrokenSelectionRule());
    }

    @Test
    void setSaveRetrieveAndGetBrokenSelectionRule() {
        RskAddress accountAddress = TestUtils.generateAddress("account");
        Repository repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assertions.assertEquals(Boolean.TRUE, newProvider.getBrokenSelectionRule());
    }

    @Test
    void setSaveRetrieveAndGetSiblingsBeforeRFS() {
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getActivationConfig()).thenReturn(ActivationConfigsForTest.allBut(ConsensusRule.RSKIP85));
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        List<SiblingElement> siblings = Arrays.asList(new SiblingElement(5, 6, minerFee), new SiblingElement(10, 11, minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(repository), Coin.valueOf(0), Coin.valueOf(0L), 1L);
    }

    @Test
    void setSaveRetrieveAndGetSiblingsAfterRFS() {
        long minerFee = 21000;
        long txValue = 10000;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);

        List<SiblingElement> siblings = Arrays.asList(
                new SiblingElement(5, 6, minerFee),
                new SiblingElement(10, 11, minerFee)
        );

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();
        this.validateRemascsStorageIsCorrect(
                this.getRemascStorageProvider(builder.getRepository()),
                Coin.valueOf(0L),
                Coin.valueOf(0L),
                0L
        );
    }

    @Test
    void alwaysPaysBeforeRFS() {
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getActivationConfig()).thenReturn(ActivationConfigsForTest.allBut(ConsensusRule.RSKIP85));

        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(repository), Coin.valueOf(84000), Coin.valueOf(0L), 0L);
    }

    @Test
    void alwaysPaysFedBeforeRFS() {
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getActivationConfig()).thenReturn(ActivationConfigsForTest.allBut(ConsensusRule.RSKIP85));

        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;


        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        Block executionBlock = testRunner.getBlockChain().getBestBlock();

        ActivationConfig.ForBlock activations = config.getActivationConfig().forBlock(executionBlock.getNumber());
        Repository track = repository.startTracking();

        FederationConstants federationConstants = config.getNetworkConstants().getBridgeConstants().getFederationConstants();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(track);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        FederationSupport federationSupport = new FederationSupportImpl(federationConstants, federationStorageProvider, executionBlock, activations);

        RemascFederationProvider federationProvider = new RemascFederationProvider(config.getActivationConfig().forBlock(executionBlock.getNumber()), federationSupport);
        assertEquals(Coin.valueOf(0), this.getRemascStorageProvider(repository).getFederationBalance());
        long federatorBalance = (168 / federationProvider.getFederationSize()) * 2;
        assertEquals(Coin.valueOf(federatorBalance), RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    void doesntPayFedBelowMinimumRewardAfterRFS() {
        Constants constants = spy(Constants.testnet(null));
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn(Constants.REGTEST_CHAIN_ID);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(0));
        when(constants.getFederatorMinimumPayableGas()).thenReturn(BigInteger.valueOf(10000L));
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(constants);
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);
        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        Block executionBlock = testRunner.getBlockChain().getBestBlock();

        ActivationConfig.ForBlock activations = config.getActivationConfig().forBlock(executionBlock.getNumber());
        Repository track = repository.startTracking();

        FederationConstants federationConstants = config.getNetworkConstants().getBridgeConstants().getFederationConstants();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(track);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        FederationSupport federationSupport = new FederationSupportImpl(federationConstants, federationStorageProvider, executionBlock, activations);

        RemascFederationProvider federationProvider = new RemascFederationProvider(config.getActivationConfig().forBlock(executionBlock.getNumber()), federationSupport);
        assertEquals(Coin.valueOf(336), this.getRemascStorageProvider(repository).getFederationBalance());
        assertNull(RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    void doesntPayBelowMinimumRewardAfterRFS() {
        Constants constants = spy(Constants.testnet(null));
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn(Constants.REGTEST_CHAIN_ID);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(10000L));
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(constants);
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 1L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);
        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(repository), Coin.valueOf(126000), Coin.valueOf(0L), 0L);
    }

    @Test
    void paysFedWhenHigherThanMinimumRewardAfterRFS() {
        Constants constants = spy(Constants.testnet(null));
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn(Constants.REGTEST_CHAIN_ID);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(0));
        when(constants.getFederatorMinimumPayableGas()).thenReturn(BigInteger.valueOf(10L));
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(constants);
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 10L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        Block executionBlock = testRunner.getBlockChain().getBestBlock();

        ActivationConfig.ForBlock activations = config.getActivationConfig().forBlock(executionBlock.getNumber());
        Repository track = repository.startTracking();

        FederationConstants federationConstants = config.getNetworkConstants().getBridgeConstants().getFederationConstants();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(track);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        FederationSupport federationSupport = new FederationSupportImpl(federationConstants, federationStorageProvider, executionBlock, activations);

        RemascFederationProvider federationProvider = new RemascFederationProvider(config.getActivationConfig().forBlock(executionBlock.getNumber()), federationSupport);
        long federatorBalance = (1680 / federationProvider.getFederationSize()) * 2;
        assertEquals(Coin.valueOf(0), this.getRemascStorageProvider(repository).getFederationBalance());
        assertEquals(Coin.valueOf(federatorBalance), RemascTestRunner.getAccountBalance(repository, federationProvider.getFederatorAddress(0)));
    }

    @Test
    void paysWhenHigherThanMinimumRewardAfterRFS() {
        Constants constants = spy(Constants.testnet(null));
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn(Constants.REGTEST_CHAIN_ID);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(21000L));
        when(constants.getFederatorMinimumPayableGas()).thenReturn(BigInteger.valueOf(10L));
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(constants);
        long minerFee = 21000;
        long txValue = 10000;
        long gasPrice = 10L;

        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);

        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(repository), Coin.valueOf(840000L), Coin.valueOf(0L), 0L);
    }

    @Test
    void paysOnlyBlocksWithEnoughBalanceAccumulatedAfterRFS() {
        Constants constants = spy(Constants.testnet(null));
        // we need to pass chain id check, and make believe that testnet config has same chain id as cow account
        when(constants.getChainId()).thenReturn(Constants.REGTEST_CHAIN_ID);
        when(constants.getMinimumPayableGas()).thenReturn(BigInteger.valueOf(21000L));
        RskSystemProperties config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(constants);
        long txValue = 10000;
        long gasLimit = 100000L;
        long gasPrice = 10L;
        long lowGasPrice = 1L;
        long minerFee = 21000;

        RskAddress coinbase = TestUtils.generateAddress("coinbase");
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock).setConfig(config);

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(13).siblingElements(new ArrayList<>()).txSigningKey(this.cowKey).gasPrice(gasPrice);
        testRunner.setFixedCoinbase(coinbase);
        testRunner.start();
        Blockchain blockchain = testRunner.getBlockChain();
        RepositoryLocator repositoryLocator = builder.getRepositoryLocator();
        List<Block> blocks = new ArrayList<>();
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blockchain.getBestBlock(), PegTestUtils.createHash3(),
                coinbase, Collections.emptyList(), gasLimit, gasPrice, 14, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, Collections.emptyList(), gasLimit, lowGasPrice, 15, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, Collections.emptyList(), gasLimit, gasPrice, 16, txValue, cowKey, null));
        blocks.add(RemascTestRunner.createBlock(genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(),
                coinbase, Collections.emptyList(), gasLimit, lowGasPrice, 17, txValue, cowKey, null));

        blocks.addAll(createSimpleBlocks(blocks.get(blocks.size()-1),10, coinbase));

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), signatureCache);

        BlockExecutor blockExecutor = new BlockExecutor(
                repositoryLocator,
                new TransactionExecutorFactory(
                        config,
                        builder.getBlockStore(),
                        null,
                        new BlockFactory(config.getActivationConfig()),
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                        new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                ),
                config);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock().getHeader());
            Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(b));
            RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());

            long blockNumber = blockchain.getBestBlock().getNumber();
            if (blockNumber == 24){ // before first special block
                assertEquals(Coin.valueOf(1663200L), RemascTestRunner.getAccountBalance(repository, coinbase));
            } else if (blockNumber == 25 || blockNumber == 26){ // after first and second special block
                assertEquals(Coin.valueOf(1829520L), RemascTestRunner.getAccountBalance(repository, coinbase));
            } else if (blockNumber == 27 || blockNumber == 28){ // after third and fourth special block
                assertEquals(Coin.valueOf(1999167L), RemascTestRunner.getAccountBalance(repository, coinbase));
            }
        }
    }
}
