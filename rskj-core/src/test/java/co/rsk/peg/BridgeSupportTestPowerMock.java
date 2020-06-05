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

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.simples.SimpleBlockChain;
import co.rsk.peg.simples.SimpleWallet;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.Trie;
import com.google.common.collect.Lists;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.Program;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/9/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({BridgeUtils.class, BtcBlockChain.class, BridgeSupport.class})
public class BridgeSupportTestPowerMock {
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;
    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    private BridgeConstants bridgeConstants;
    private NetworkParameters btcParams;
    private ActivationConfig.ForBlock activationsBeforeForks;

    @Before
    public void setUpOnEachTest() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
    }

    @Test
    public void testInitialChainHeadWithoutBtcCheckpoints() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, btcBlockStoreFactory);

        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        StoredBlock chainHead = getBtcBlockStoreFromBridgeSupport(bridgeSupport).getChainHead();
        Assert.assertEquals(0, chainHead.getHeight());
        Assert.assertEquals(btcParams.getGenesisBlock(), chainHead.getHeader());
    }

    @Test
    public void testInitialChainHeadWithBtcCheckpoints() throws Exception {
        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider, track, btcBlockStoreFactory);

        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        InputStream checkpointsStream = bridgeSupport.getCheckPoints();
        CheckpointManager manager = new CheckpointManager(bridgeConstants.getBtcParams(), checkpointsStream);
        long time = bridgeSupport.getActiveFederation().getCreationTime().toEpochMilli() - 604800L; // The magic number is a substraction CheckpointManager does when getting the checkpoints.
        StoredBlock checkpoint = manager.getCheckpointBefore(time);

        Assert.assertEquals(checkpoint.getHeight(), bridgeSupport.getBtcBlockchainBestChainHeight());
    }

    @Test
    public void feePerKbFromStorageProvider() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Coin expected = Coin.MILLICOIN;
        provider.setFeePerKb(expected);
        provider.saveFeePerKb();

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track);

        Assert.assertEquals(expected, bridgeSupport.getFeePerKb());
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithoutBtcCheckpoints() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, btcBlockStoreFactory);

        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        StoredBlock chainHead = getBtcBlockStoreFromBridgeSupport(bridgeSupport).getChainHead();
        Assert.assertEquals(0, chainHead.getHeight());
        Assert.assertEquals(btcParams.getGenesisBlock(), chainHead.getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(btcParams.getGenesisBlock().getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(btcParams.getGenesisBlock().getHash(), locator.get(5));
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithBtcCheckpoints() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        List<BtcBlock> checkpoints = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                track,
                null,
                new Context(bridgeConstants.getBtcParams()),
                new FederationSupport(bridgeConstants, provider, null),
                btcBlockStoreFactory, mock(ActivationConfig.ForBlock.class)
        ) {
            @Override
            InputStream getCheckPoints() {
                return getCheckpoints(btcParams, checkpoints);
            }
        };

        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        StoredBlock chainHead = getBtcBlockStoreFromBridgeSupport(bridgeSupport).getChainHead();
        Assert.assertEquals(10, chainHead.getHeight());
        Assert.assertEquals(checkpoints.get(9), chainHead.getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(btcParams, checkpoints.get(9), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(5));
    }

    private List<BtcBlock> createBtcBlocks(NetworkParameters _networkParameters, BtcBlock parent, int numberOfBlocksToCreate) {
        List<BtcBlock> list = new ArrayList<>();
        for (int i = 0; i < numberOfBlocksToCreate; i++) {
            BtcBlock block = new BtcBlock(_networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds() + 1, parent.getDifficultyTarget(), 0, new ArrayList<BtcTransaction>());
            block.solve();
            list.add(block);
            parent = block;
        }
        return list;
    }

    private InputStream getCheckpoints(NetworkParameters _networkParameters, List<BtcBlock> checkpoints) {
        try {
            ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
            MessageDigest digest = Sha256Hash.newDigest();
            final DigestOutputStream digestOutputStream = new DigestOutputStream(baOutputStream, digest);
            digestOutputStream.on(false);
            final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
            StoredBlock storedBlock = new StoredBlock(_networkParameters.getGenesisBlock(), _networkParameters.getGenesisBlock().getWork(), 0);
            try {
                dataOutputStream.writeBytes("CHECKPOINTS 1");
                dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
                digestOutputStream.on(true);
                dataOutputStream.writeInt(checkpoints.size());
                ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                for (BtcBlock block : checkpoints) {
                    storedBlock = storedBlock.build(block);
                    storedBlock.serializeCompact(buffer);
                    dataOutputStream.write(buffer.array());
                    buffer.position(0);
                }
            } finally {
                dataOutputStream.close();
                digestOutputStream.close();
                baOutputStream.close();
            }
            return new ByteArrayInputStream(baOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void callUpdateCollectionsGenerateEventLog() throws IOException {
        Repository track = createRepository().startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider, track, eventLogger, rskCurrentBlock, null);

        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        ECKey key = new ECKey();
        tx.sign(key.getPrivKeyBytes());

        bridgeSupport.updateCollections(tx);
        verify(eventLogger, times(1)).logUpdateCollections(tx);
    }

    @Test
    public void callUpdateCollectionsFundsEnoughForJustTheSmallerTx() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(30, 0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(20, 0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(10, 0));
        provider0.setFeePerKb(Coin.MILLICOIN);

        provider0.getNewFederationBtcUTXOs().add(new UTXO(
                PegTestUtils.createHash(),
                1,
                Coin.valueOf(12, 0),
                0,
                false,
                ScriptBuilder.createOutputScript(federation.getAddress())
        ));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, providerForSupport, track, mock(BridgeEventLogger.class), rskCurrentBlock, null);

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration as the height doesn't affect it for releases
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        // Check value sent to user is 10 BTC minus fee
        Assert.assertEquals(Coin.valueOf(999962800l), provider.getReleaseTransactionSet().getEntries().iterator().next().getTransaction().getOutput(0).getValue());
        // Check the wallet has been emptied
        Assert.assertTrue(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void callUpdateCollectionsThrowsCouldNotAdjustDownwards() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(37500));
        provider0.setFeePerKb(Coin.MILLICOIN);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(
                PegTestUtils.createHash(),
                1,
                Coin.valueOf(1000000),
                0,
                false,
                ScriptBuilder.createOutputScript(federation.getAddress())
        ));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                providerForSupport,
                track,
                mock(BridgeEventLogger.class),
                rskCurrentBlock,
                null
        );

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration as it doesn't affect the release transactions
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        Assert.assertFalse(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void callUpdateCollectionsThrowsExceededMaxTransactionSize() throws IOException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.COIN.multiply(7));
        for (int i = 0; i < 2000; i++) {
            provider0.getNewFederationBtcUTXOs().add(new UTXO(
                    PegTestUtils.createHash(),
                    1,
                    Coin.CENT,
                    0,
                    false,
                    ScriptBuilder.createOutputScript(federation.getAddress())
            ));
        }

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = null;
        org.ethereum.db.BlockStore rskBlockStore = builder.getBlockStore();
        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                providerForSupport,
                track,
                mock(BridgeEventLogger.class),
                rskCurrentBlock,
                null
        );

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // keeping same bridge storage configuration
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        Assert.assertFalse(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void minimumProcessFundsMigrationValue() throws IOException {
        Federation oldFederation = bridgeConstants.getGenesisFederation();
        BtcECKey key = new BtcECKey(new SecureRandom());
        FederationMember member = new FederationMember(key, new ECKey(), new ECKey());
        Federation newFederation = new Federation(
                Collections.singletonList(member),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);

        Repository repository = createRepository();
        Repository track = repository.startTracking();
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BridgeEventLogger.class),
                rskCurrentBlock,
                null
        );

        // One MICROCOIN is less than half the fee per kb, which is the minimum funds to migrate,
        // and so it won't be removed from the old federation UTXOs list for migration.
        List<UTXO> unsufficientUTXOsForMigration1 = new ArrayList<>();
        unsufficientUTXOsForMigration1.add(createUTXO(Coin.MICROCOIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(unsufficientUTXOsForMigration1);
        bridgeSupport.updateCollections(tx);
        assertThat(unsufficientUTXOsForMigration1.size(), is(1));

        // MILLICOIN is greater than half the fee per kb,
        // and it will be removed from the old federation UTXOs list for migration.
        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.MILLICOIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);
        assertThat(sufficientUTXOsForMigration1.size(), is(0));

        // 2 smaller coins should work exactly like 1 MILLICOIN
        List<UTXO> sufficientUTXOsForMigration2 = new ArrayList<>();
        sufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN.divide(2), oldFederation.getAddress()));
        sufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN.divide(2), oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration2);

        bridgeSupport.updateCollections(tx);
        assertThat(sufficientUTXOsForMigration2.size(), is(0));

        // higher fee per kb prevents funds migration
        List<UTXO> unsufficientUTXOsForMigration2 = new ArrayList<>();
        unsufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(unsufficientUTXOsForMigration2);
        when(provider.getFeePerKb())
                .thenReturn(Coin.COIN);

        bridgeSupport.updateCollections(tx);
        assertThat(unsufficientUTXOsForMigration2.size(), is(1));
    }

    @Test
    public void callUpdateCollectionsChangeGetsOutOfDust() throws IOException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Map<byte[], BigInteger> preMineMap = new HashMap<byte[], BigInteger>();
        preMineMap.put(PrecompiledContracts.BRIDGE_ADDR.getBytes(), LIMIT_MONETARY_BASE.asBigInteger());

        BlockGenerator blockGenerator = new BlockGenerator();
        Genesis genesisBlock = (Genesis) blockGenerator.getNewGenesisBlock(0, preMineMap);

        List<Block> blocks = blockGenerator.getSimpleBlockChain(genesisBlock, 10);

        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).setGenesis(genesisBlock).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        Repository repository = builder.getRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.COIN);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.COIN.add(Coin.valueOf(100)), 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction tx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                providerForSupport,
                track,
                mock(BridgeEventLogger.class),
                rskCurrentBlock,
                null
        );

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600))), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600)), repository.getBalance(BridgeSupport.BURN_ADDRESS));
        // Check the wallet has been emptied
        Assert.assertTrue(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException {
        // Bridge constants and btc context
        Context context = new Context(btcParams);

        // Fake wallet returned every time
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(Context.class), any(Federation.class), any(List.class))).thenReturn(new SimpleWallet(context));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BtcTransaction txs = new BtcTransaction(btcParams);
        txs.addOutput(Coin.FIFTY_COINS, new BtcECKey());

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addInput(txs.getOutput(0));
        tx1.getInput(0).disconnect();
        tx1.addOutput(Coin.COIN, new BtcECKey());
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addInput(txs.getOutput(0));
        tx2.getInput(0).disconnect();
        tx2.addOutput(Coin.COIN, new BtcECKey());
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addInput(txs.getOutput(0));
        tx3.getInput(0).disconnect();
        tx3.addOutput(Coin.COIN, new BtcECKey());
        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 1L);
        provider0.getReleaseTransactionSet().add(tx3, 1L);

        provider0.save();

        track.commit();

        track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);

        BlockChainBuilder builder = new BlockChainBuilder();
        builder.setTesting(true).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction rskTx = new Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BridgeEventLogger.class),
                rskCurrentBlock,
                null
        );

        bridgeSupport.updateCollections(rskTx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(2, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(1, provider2.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException, BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, btcParams.getGenesisBlock().getDifficultyTarget(), 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addBlockHeaderToBlockchain() throws IOException, BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Context btcContext = new Context(btcParams);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);
        Whitebox.setInternalState(bridgeSupport, "btcBlockChain", btcBlockChain);
        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNotNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void releaseBtcWithDustOutput() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = new org.ethereum.core.Transaction(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtc() throws AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = new org.ethereum.core.Transaction(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtcFromContract() throws AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = new InternalTransaction(
                null, 0, 0,
                BigIntegers.asUnsignedByteArray(NONCE),
                DataWord.valueOf(BigIntegers.asUnsignedByteArray(GAS_PRICE)),
                DataWord.valueOf(BigIntegers.asUnsignedByteArray(GAS_LIMIT)),
                new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(new BtcECKey().getPrivKey()).getAddress()).getBytes(),
                Hex.decode(TO_ADDRESS),
                BigIntegers.asUnsignedByteArray(AMOUNT),
                Hex.decode(DATA),
                "");

        track.saveCode(tx.getSender(), new byte[]{0x1});
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track);

        try {
            bridgeSupport.releaseBtc(tx);
        } catch (Program.OutOfGasException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void registerBtcTransactionOfAlreadyProcessedTransaction() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider.setHeightBtcTxhashAlreadyProcessed(tx.getHash(), 1L);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, null);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    public void registerBtcTransactionOfTransactionNotInMerkleTree() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, btcBlockStoreFactory);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNegativeHeight() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, btcBlockStoreFactory);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), -1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNotEnoughtHeight() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, btcBlockStoreFactory);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void registerBtcTransactionWithoutInputs() throws IOException, BlockStoreException {
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BtcTransaction noInputsTx = new BtcTransaction(btcParams);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(noInputsTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        int btcTxHeight = 2;

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), noInputsTx.bitcoinSerialize(), btcTxHeight, pmt.bitcoinSerialize());
    }

    @Test
    public void registerBtcTransactionTxNotLockNorReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx = new BtcTransaction(btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(btcParams);
        tx.addOutput(Coin.COIN, address);
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));


        Context btcContext = new Context(btcParams);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(0, provider2.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    public void registerBtcTransactionReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Repository track = repository.startTracking();
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(btcParams);
        tx.addOutput(Coin.COIN, address);
        Address address2 = federation.getAddress();
        tx.addOutput(Coin.COIN, address2);

        // Create previous tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);
        // Create tx input
        tx.addInput(prevOut);
        // Create tx input base script sig
        Script scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        // Create sighash
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, federation.getBtcPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, federation.getBtcPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BridgeEventLogger.class),
                executionBlock,
                mockFactory
        );

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(1, provider2.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN, provider2.getNewFederationBtcUTXOs().get(0).getValue());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    public void registerBtcTransactionMigrationTx() throws BlockStoreException, AddressFormatException, IOException {
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        Federation activeFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys), Instant.ofEpochMilli(2000L), 2L, parameters);

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        Federation retiringFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys), Instant.ofEpochMilli(1000L), 1L, parameters);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(15L);

        Repository track = repository.startTracking();

        BtcTransaction tx = new BtcTransaction(parameters);
        Address activeFederationAddress = activeFederation.getAddress();
        tx.addOutput(Coin.COIN, activeFederationAddress);

        // Create previous tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, retiringFederation.getAddress());
        prevTx.addOutput(prevOut);
        // Create tx input
        tx.addInput(prevOut);
        // Create tx input base script sig
        Script scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(retiringFederation);
        // Create sighash
        Script redeemScript = ScriptBuilder.createRedeemScript(retiringFederation.getNumberOfSignaturesRequired(), retiringFederation.getBtcPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = retiringFederationKeys.get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getBtcPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = retiringFederationKeys.get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getBtcPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);
        provider.setNewFederation(activeFederation);
        provider.setOldFederation(retiringFederation);
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BridgeEventLogger.class),
                executionBlock,
                mockFactory
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 30, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        List<UTXO> activeFederationBtcUTXOs = provider.getNewFederationBtcUTXOs();
        List<Coin> activeFederationBtcCoins = activeFederationBtcUTXOs.stream().map(UTXO::getValue).collect(Collectors.toList());
        assertThat(activeFederationBtcUTXOs, hasSize(1));
        assertThat(activeFederationBtcCoins, hasItem(Coin.COIN));
    }

    @Test
    public void registerBtcTransactionWithCrossFederationsChange() throws Exception {
        NetworkParameters params = RegTestParams.get();
        Address randomAddress = new Address(params, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        Context btcContext = new Context(params);

        List<BtcECKey> activeFederationKeys = Stream.of("fa01", "fa02")
                .map(Hex::decode)
                .map(BtcECKey::fromPrivate)
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .collect(Collectors.toList());
        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
                Instant.ofEpochMilli(1000L), 0L, params
        );

        List<BtcECKey> retiringFederationKeys = Stream.of("fb01", "fb02", "fb03")
                .map(Hex::decode)
                .map(BtcECKey::fromPrivate)
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .collect(Collectors.toList());
        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
                Instant.ofEpochMilli(2000L), 0L, params
        );

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        List<UTXO> retiringFederationUtxos = new ArrayList<>();
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        doReturn(activeFederation).when(mockFederationSupport).getActiveFederation();
        doReturn(retiringFederation).when(mockFederationSupport).getRetiringFederation();
        doReturn(retiringFederationUtxos).when(mockFederationSupport).getRetiringFederationBtcUTXOs();

        BtcBlockStoreWithCache mockBtcBlockStore = mock(BtcBlockStoreWithCache.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockBtcBlockStore.getChainHead().getHeight()).thenReturn(14);

        Coin changeValue = Coin.COIN.plus(Coin.COIN);
        Address retiringFederationAddress = retiringFederation.getAddress();
        BtcTransaction releaseWithChangeTx = new BtcTransaction(params);
        releaseWithChangeTx.addOutput(Coin.COIN, randomAddress);
        releaseWithChangeTx.addOutput(changeValue, retiringFederationAddress);
        TransactionInput retiringFederationInput = new TransactionInput(params, releaseWithChangeTx, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        releaseWithChangeTx.addInput(retiringFederationInput);

        PartialMerkleTree partialMerkleTree = PartialMerkleTree.buildFromLeaves(params, new byte[]{(byte) 0xff}, Collections.singletonList(releaseWithChangeTx.getHash()));

        BtcBlock mockBtcBlock = mock(BtcBlock.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockBtcBlock.getMerkleRoot()).thenReturn(partialMerkleTree.getTxnHashAndMerkleRoot(new LinkedList<>()));

        StoredBlock mockStoredBlock = mock(StoredBlock.class);
        when(mockStoredBlock.getHeader()).thenReturn(mockBtcBlock);

        when(mockBtcBlockStore.getStoredBlockAtMainChainHeight(anyInt())).thenReturn(mockStoredBlock);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(mockBtcBlockStore);

        PowerMockito.spy(BridgeUtils.class);
        PowerMockito.doReturn(false).when(BridgeUtils.class, "isLockTx", any(BtcTransaction.class), anyList(), any(Context.class), any(BridgeConstants.class));
        PowerMockito.doReturn(true).when(BridgeUtils.class, "isReleaseTx", any(BtcTransaction.class), anyList());

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                mock(Block.class),
                btcContext,
                mockFederationSupport,
                mockFactory,
                mock(ActivationConfig.ForBlock.class)
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), releaseWithChangeTx.bitcoinSerialize(), 1, partialMerkleTree.bitcoinSerialize());

        assertThat(retiringFederationUtxos, hasSize(1));
        UTXO changeUtxo = retiringFederationUtxos.get(0);
        assertThat(changeUtxo.getValue(), is(changeValue));
        assertThat(changeUtxo.getScript().getToAddress(params), is(retiringFederationAddress));

        ArgumentCaptor<BtcTransaction> releasedBtcTxCaptor = ArgumentCaptor.forClass(BtcTransaction.class);
        ArgumentCaptor<List<Federation>> fedListCaptor = ArgumentCaptor.forClass((Class) List.class);
        PowerMockito.verifyStatic(BridgeUtils.class);
        BridgeUtils.isReleaseTx(releasedBtcTxCaptor.capture(), fedListCaptor.capture());

        assertThat(releasedBtcTxCaptor.getValue(), is(releaseWithChangeTx));
        assertThat(fedListCaptor.getValue(), IsIterableContainingInOrder.contains(activeFederation, retiringFederation));
    }

    @Test
    public void registerBtcTransactionLockTxWhitelisted() throws Exception {
        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation federation1 = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys), Instant.ofEpochMilli(1000L), 0L, btcParams);

        List<BtcECKey> federation2Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")),
        });
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation federation2 = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys), Instant.ofEpochMilli(2000L), 0L, btcParams);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.COIN.multiply(2), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(3), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activationsBeforeForks);
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        Address address1 = srcKey1.toAddress(btcParams);
        Address address2 = srcKey2.toAddress(btcParams);
        Address address3 = srcKey3.toAddress(btcParams);
        whitelist.put(address1, new OneOffWhiteListEntry(address1, Coin.COIN.multiply(5)));
        whitelist.put(address2, new OneOffWhiteListEntry(address2, Coin.COIN.multiply(10)));
        whitelist.put(address3, new OneOffWhiteListEntry(address3, Coin.COIN.multiply(2).add(Coin.COIN.multiply(3))));


        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BridgeEventLogger.class),
                executionBlock,
                mockFactory
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();
        Assert.assertThat(whitelist.isWhitelisted(address1), is(false));
        Assert.assertThat(whitelist.isWhitelisted(address2), is(false));
        Assert.assertThat(whitelist.isWhitelisted(address3), is(false));

        co.rsk.core.Coin amountToHaveBeenCreditedToSrc1 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));
        co.rsk.core.Coin amountToHaveBeenCreditedToSrc2 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(10, 0));
        co.rsk.core.Coin amountToHaveBeenCreditedToSrc3 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = amountToHaveBeenCreditedToSrc1
                .add(amountToHaveBeenCreditedToSrc2)
                .add(amountToHaveBeenCreditedToSrc3);
        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assert.assertEquals(amountToHaveBeenCreditedToSrc1, repository.getBalance(srcKey1RskAddress));
        Assert.assertEquals(amountToHaveBeenCreditedToSrc2, repository.getBalance(srcKey2RskAddress));
        Assert.assertEquals(amountToHaveBeenCreditedToSrc3, repository.getBalance(srcKey3RskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertEquals(2, provider2.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(2, provider2.getOldFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider2.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(2), provider2.getNewFederationBtcUTXOs().get(1).getValue());
        Assert.assertEquals(Coin.COIN.multiply(10), provider2.getOldFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(3), provider2.getOldFederationBtcUTXOs().get(1).getValue());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
        Assert.assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());
        Assert.assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());
    }

    @Test
    public void isBtcTxHashAlreadyProcessed() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = getBridgeSupport(getBridgeStorageProviderMockWithProcessedHashes(), null);

        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes())));
        }
        Assert.assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of("anything".getBytes())));
    }

    @Test
    public void getBtcTxHashProcessedHeight() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = getBridgeSupport(getBridgeStorageProviderMockWithProcessedHashes(), null);

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals((long) i, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of(("hash_" + i).getBytes())).longValue());
        }
        Assert.assertEquals(-1L, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of("anything".getBytes())).longValue());
    }

    @Test
    public void getFederationMethods_genesis() throws IOException {
        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembers(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                FederationTestUtils.getFederationMembers(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(true, activeFederation, genesisFederation, null, null, null, null);

        Assert.assertEquals(6, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(4, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(genesisFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(6);
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));
        }
    }

    @Test
    public void getFederationMethods_active() throws IOException {
        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembers(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                FederationTestUtils.getFederationMembers(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                activeFederation,
                genesisFederation,
                null,
                null,
                null,
                null
        );

        Assert.assertEquals(3, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(2, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(activeFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(3);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));
        }
    }

    @Test
    public void getFederationMethods_newActivated() throws IOException {
        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(3),
                Instant.ofEpochMilli(1000),
                15L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation oldFederation = new Federation(
                FederationTestUtils.getFederationMembers(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        when(mockedBlock.getNumber()).thenReturn(26L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                newFederation,
                null,
                oldFederation,
                null,
                null,
                mockedBlock
        );

        Assert.assertEquals(3, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(2, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(newFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(3);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));
        }
    }

    @Test
    public void getFederationMethods_newNotActivated() throws IOException {
        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(3),
                Instant.ofEpochMilli(1000),
                15L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation oldFederation = new Federation(
                FederationTestUtils.getFederationMembers(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        when(mockedBlock.getNumber()).thenReturn(20L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                newFederation,
                null,
                oldFederation,
                null,
                null,
                mockedBlock
        );

        Assert.assertEquals(6, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(4, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(oldFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(6);
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));

        }
    }

    @Test
    public void getRetiringFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKey(0));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    public void getRetiringFederationMethods_presentNewInactive() throws IOException {
        Federation mockedNewFederation = new Federation(
                FederationTestUtils.getFederationMembers(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                FederationTestUtils.getFederationMembers(4),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        // New federation should not be active in this block
        when(mockedBlock.getNumber()).thenReturn(15L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                mockedNewFederation,
                null, mockedOldFederation,
                null,
                null,
                mockedBlock
        );

        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKey(0));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    public void getRetiringFederationMethods_presentNewActive() throws IOException {
        Federation mockedNewFederation = new Federation(
                FederationTestUtils.getFederationMembers(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                FederationTestUtils.getFederationMembers(4),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        // New federation should be active in this block
        when(mockedBlock.getNumber()).thenReturn(25L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                mockedNewFederation,
                null, mockedOldFederation,
                null,
                null,
                mockedBlock
        );

        Assert.assertEquals(4, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(3, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertEquals(1000, bridgeSupport.getRetiringFederationCreationTime().toEpochMilli());
        Assert.assertEquals(mockedOldFederation.getAddress().toString(), bridgeSupport.getRetiringFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(4);
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getRetiringFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));
        }
    }

    @Test
    public void getPendingFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        Assert.assertEquals(-1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKey(0));
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    public void getPendingFederationMethods_present() throws IOException {
        PendingFederation mockedPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembers(5));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, mockedPendingFederation, null, null);

        Assert.assertEquals(5, bridgeSupport.getPendingFederationSize().intValue());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(5);
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getPendingFederatorPublicKey(i)));
            Assert.assertTrue(Arrays.equals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getPendingFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)));
            Assert.assertTrue(Arrays.equals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getPendingFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)));
            Assert.assertTrue(Arrays.equals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getPendingFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)));
        }
    }

    @Test
    public void voteFederationChange_methodNotAllowed() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ABICallSpec spec = new ABICallSpec("a-random-method", new byte[][]{});
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE, bridgeSupport.voteFederationChange(mock(Transaction.class), spec));
    }

    @Test
    public void voteFederationChange_notAuthorized() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ABICallSpec spec = new ABICallSpec("create", new byte[][]{});
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(12L)).getAddress()));
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE, bridgeSupport.voteFederationChange(mockedTx, spec));
    }

    private class VotingMocksProvider {
        private RskAddress voter;
        private ABICallElection election;
        private ABICallSpec winner;
        private ABICallSpec spec;
        private Transaction tx;

        public VotingMocksProvider(String function, byte[][] arguments, boolean mockVoteResult) {
            byte[] voterBytes = ECKey.fromPublicOnly(Hex.decode(
                    // Public key hex of an authorized voter in regtest, taken from BridgeRegTestConstants
                    "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991"
            )).getAddress();
            voter = new RskAddress(voterBytes);

            tx = mock(Transaction.class);
            when(tx.getSender()).thenReturn(voter);

            spec = new ABICallSpec(function, arguments);

            election = mock(ABICallElection.class);
            if (mockVoteResult)
                when(election.vote(spec, voter)).thenReturn(true);

            when(election.getWinner()).then((InvocationOnMock m) -> this.getWinner());
        }

        public RskAddress getVoter() {
            return voter;
        }

        public ABICallElection getElection() {
            return election;
        }

        public ABICallSpec getSpec() {
            return spec;
        }

        public Transaction getTx() {
            return tx;
        }

        public ABICallSpec getWinner() {
            return winner;
        }

        public void setWinner(ABICallSpec winner) {
            this.winner = winner;
        }

        public int execute(BridgeSupport bridgeSupport) {
            return bridgeSupport.voteFederationChange(tx, spec);
        }
    }

    @Test
    public void createFederation_ok() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash())
        );
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    public void createFederation_pendingExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                new PendingFederation(Collections.emptyList()),
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash()
        ));
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(
                new PendingFederation(Collections.emptyList()).getHash().getBytes(),
                bridgeSupport.getPendingFederationHash()
        ));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void createFederation_withPendingActivation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, false);

        Federation mockedNewFederation = new Federation(
                FederationTestUtils.getFederationMembers(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                FederationTestUtils.getFederationMembers(4),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        // New federation should be waiting for activation in this block
        when(mockedBlock.getNumber()).thenReturn(19L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                mockedNewFederation,
                null,
                mockedOldFederation,
                null,
                mocksProvider.getElection(),
                mockedBlock
        );
        ((BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider")).getOldFederationBtcUTXOs().add(mock(UTXO.class));

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void createFederation_withExistingRetiringFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("create", new byte[][]{}, false);

        Federation mockedNewFederation = new Federation(
                FederationTestUtils.getFederationMembers(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                FederationTestUtils.getFederationMembers(4),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Block mockedBlock = mock(Block.class);
        // New federation should be active in this block
        when(mockedBlock.getNumber()).thenReturn(21L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                mockedNewFederation,
                null,
                mockedOldFederation,
                null,
                mocksProvider.getElection(),
                mockedBlock
        );
        ((BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider")).getOldFederationBtcUTXOs().add(mock(UTXO.class));

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-3, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_okNoKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Collections.emptyList());
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKey(0)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)));
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKey_okKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(2, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKey(0)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)));

        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKey(1)));
        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.MST)));

        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKey_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_keyExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(new BtcECKey[]{
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_invalidKey() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("aabbccdd")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE.intValue(), mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKeyMultikey_okNoKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
                Hex.decode("026289413837ab836eb76428406a3b4f200418d31d99c259a0532b8e435f35153b"),
                Hex.decode("03e12efa1146037bc9325574b0f15749ba6dc0eec360b1670b05029eead511a6ff")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Collections.emptyList());
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("026289413837ab836eb76428406a3b4f200418d31d99c259a0532b8e435f35153b"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("03e12efa1146037bc9325574b0f15749ba6dc0eec360b1670b05029eead511a6ff"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)));
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKeyMultikey_okKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
                Hex.decode("03f64d2c022bca70f3ff0b1e95336be2c1507daa2ad37a484e0b66cbda86cfc6c5"),
                Hex.decode("03eed62698319f754407a31fde9a51da8b2be0ab40e9c4c695bb057757729be37f")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new FederationMember(
                BtcECKey.fromPublicOnly(Hex.decode("02ebd9e8b2caff48b10e661e69fe107d6986d2df1ce7e377f2ef927f3194a61b99")),
                ECKey.fromPublicOnly(Hex.decode("02a23343f50363dc9a4c29f0c0a8386780cc8bf469211f4de51d50f8c0f274e9a7")),
                ECKey.fromPublicOnly(Hex.decode("030dd584c286275ab2ce249096d0d7e6c78853e0902db061b14f2e39df068f95bc"))
        )));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(2, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertTrue(Arrays.equals(Hex.decode("02ebd9e8b2caff48b10e661e69fe107d6986d2df1ce7e377f2ef927f3194a61b99"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("02a23343f50363dc9a4c29f0c0a8386780cc8bf469211f4de51d50f8c0f274e9a7"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("030dd584c286275ab2ce249096d0d7e6c78853e0902db061b14f2e39df068f95bc"), bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)));

        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.BTC)));
        Assert.assertTrue(Arrays.equals(Hex.decode("03f64d2c022bca70f3ff0b1e95336be2c1507daa2ad37a484e0b66cbda86cfc6c5"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.RSK)));
        Assert.assertTrue(Arrays.equals(Hex.decode("03eed62698319f754407a31fde9a51da8b2be0ab40e9c4c695bb057757729be37f"), bridgeSupport.getPendingFederatorPublicKeyOfType(1, FederationMember.KeyType.MST)));

        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKeyMultikey_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
                Hex.decode("0259323a848372c51673622b29298b6e5854b28d45de297fe7cff67915ad900a59"),
                Hex.decode("0304d9178db5e243667824188f86f7507104d0e237838bfb22bb4af592a8bca08a")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKeyMultikey_btcKeyExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
                Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new FederationMember(
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                ECKey.fromPublicOnly(Hex.decode("03981d06bd7bc419612aa09f860188f08d3c3010796dcb41cdfc43a6875600efa8")),
                ECKey.fromPublicOnly(Hex.decode("0365e45f68d6347aaa03c76f3d6f47000df6090801e49eef6d49f547f5c19de5dd"))
        )));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKeyMultikey_rskKeyExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
                Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new FederationMember(
                BtcECKey.fromPublicOnly(Hex.decode("0290d68bf50a8389e541a19d47c51447b443f41a13049e0783db6a25c419d612db")),
                ECKey.fromPublicOnly(Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053")),
                ECKey.fromPublicOnly(Hex.decode("0365e45f68d6347aaa03c76f3d6f47000df6090801e49eef6d49f547f5c19de5dd"))
        )));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKeyMultikey_mstKeyExists() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
                Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
                Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new FederationMember(
                BtcECKey.fromPublicOnly(Hex.decode("0290d68bf50a8389e541a19d47c51447b443f41a13049e0783db6a25c419d612db")),
                ECKey.fromPublicOnly(Hex.decode("033174d2fb7a3d7a0c87d43fa3e9ba43d4962014960b82bcd793706c05f68111c8")),
                ECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        )));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_invalidBtcKey() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("aabbccdd"),
                Hex.decode("0245db19d3d4b8c3567a47189ae60588e18e1305f3473a7fe99b6ef559bb1d1dc6"),
                Hex.decode("029664581b2e8dc9ba7885696a03134aaebe4be834ce0049d62f80499bbe130206")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE.intValue(), mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_invalidRskKey() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("0245db19d3d4b8c3567a47189ae60588e18e1305f3473a7fe99b6ef559bb1d1dc6"),
                Hex.decode("aabbccdd"),
                Hex.decode("029664581b2e8dc9ba7885696a03134aaebe4be834ce0049d62f80499bbe130206")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE.intValue(), mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void addFederatorPublicKey_invalidMstKey() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
                Hex.decode("0245db19d3d4b8c3567a47189ae60588e18e1305f3473a7fe99b6ef559bb1d1dc6"),
                Hex.decode("029664581b2e8dc9ba7885696a03134aaebe4be834ce0049d62f80499bbe130206"),
                Hex.decode("aabbccdd")
        }, false);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(BridgeSupport.FEDERATION_CHANGE_GENERIC_ERROR_CODE.intValue(), mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void rollbackFederation_ok() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        // Vote with no winner
        Assert.assertNotNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNotNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    public void rollbackFederation_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_ok() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49")))
        ));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                pendingFederation.getHash().getBytes()
        }, true);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getTimestamp()).thenReturn(15005L);
        when(executionBlock.getNumber()).thenReturn(15L);

        Federation expectedFederation = new Federation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49")))),
                Instant.ofEpochMilli(15005L), 15L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        Federation newFederation = new Federation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
                BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
                BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338")))),
                Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        BridgeEventLogger eventLoggerMock = mock(BridgeEventLogger.class);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                newFederation,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                executionBlock,
                eventLoggerMock
        );
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");

        // Mock some utxos in the currently active federation
        for (int i = 0; i < 5; i++) {
            UTXO utxoMock = mock(UTXO.class);
            when(utxoMock.getIndex()).thenReturn((long) i);
            when(utxoMock.getValue()).thenReturn(Coin.valueOf((i + 1) * 1000));
            provider.getNewFederationBtcUTXOs().add(utxoMock);
        }

        // Currently active federation
        Federation oldActiveFederation = provider.getNewFederation();
        Assert.assertNotNull(oldActiveFederation);

        // Vote with no winner
        Assert.assertNotNull(provider.getPendingFederation());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assert.assertNotNull(provider.getPendingFederation());

        Assert.assertEquals(oldActiveFederation, provider.getNewFederation());
        Assert.assertNull(provider.getOldFederation());

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        Assert.assertEquals(1, mocksProvider.execute(bridgeSupport));

        Assert.assertNull(provider.getPendingFederation());

        Federation retiringFederation = provider.getOldFederation();
        Federation activeFederation = provider.getNewFederation();

        Assert.assertEquals(expectedFederation, activeFederation);
        Assert.assertEquals(retiringFederation, oldActiveFederation);

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(5, provider.getOldFederationBtcUTXOs().size());
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals((long) i, provider.getOldFederationBtcUTXOs().get(i).getIndex());
            Assert.assertEquals(Coin.valueOf((i + 1) * 1000), provider.getOldFederationBtcUTXOs().get(i).getValue());
        }
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();

        // Check logs are made
        verify(eventLoggerMock, times(1)).logCommitFederation(executionBlock, newFederation, expectedFederation);
    }

    @Test
    public void commitFederation_noPendingFederation() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Keccak256(HashUtil.keccak256(Hex.decode("aabbcc"))).getBytes()
        }, true);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                null,
                mocksProvider.getElection(),
                null
        );

        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        Assert.assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assert.assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_incompleteFederation() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Keccak256(HashUtil.keccak256(Hex.decode("aabbcc"))).getBytes()
        }, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        Assert.assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void commitFederation_hashMismatch() throws IOException {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12"))
        })));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                new Keccak256(HashUtil.keccak256(Hex.decode("aabbcc"))).getBytes()
        }, true);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                null,
                null,
                null,
                pendingFederation,
                mocksProvider.getElection(),
                null
        );

        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        Assert.assertEquals(-3, mocksProvider.execute(bridgeSupport));
        Assert.assertTrue(Arrays.equals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash()));
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    public void getActiveFederationWallet() throws IOException {
        Federation expectedFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                expectedFederation,
                null,
                null,
                null,
                null,
                null
        );
        Context expectedContext = mock(Context.class);
        Whitebox.setInternalState(bridgeSupport, "btcContext", expectedContext);
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");
        Object expectedUtxos = provider.getNewFederationBtcUTXOs();

        final Wallet expectedWallet = mock(Wallet.class);
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(), any(), any())).then((InvocationOnMock m) -> {
            Assert.assertEquals(m.<Context>getArgument(0), expectedContext);
            Assert.assertEquals(m.<Federation>getArgument(1), expectedFederation);
            Assert.assertEquals(m.<Object>getArgument(2), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getActiveFederationWallet());
    }

    @Test
    public void getRetiringFederationWallet_nonEmpty() throws IOException {
        Federation mockedNewFederation = new Federation(
                FederationTestUtils.getFederationMembers(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation expectedFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        })), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        Block mockedBlock = mock(Block.class);
        when(mockedBlock.getNumber()).thenReturn(25L);

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
                false,
                mockedNewFederation,
                null,
                expectedFederation,
                null,
                null,
                mockedBlock
        );
        Context expectedContext = mock(Context.class);
        Whitebox.setInternalState(bridgeSupport, "btcContext", expectedContext);
        BridgeStorageProvider provider = (BridgeStorageProvider) Whitebox.getInternalState(bridgeSupport, "provider");
        Object expectedUtxos = provider.getOldFederationBtcUTXOs();

        final Wallet expectedWallet = mock(Wallet.class);
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(), any(), any())).then((InvocationOnMock m) -> {
            Assert.assertEquals(m.<Context>getArgument(0), expectedContext);
            Assert.assertEquals(m.<Federation>getArgument(1), expectedFederation);
            Assert.assertEquals(m.<Object>getArgument(2), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getRetiringFederationWallet());
    }

    @Test
    public void getLockWhitelistMethods() throws IOException {
        NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.getSize()).thenReturn(4);
        List<LockWhitelistEntry> entries = Arrays.stream(new Integer[]{2, 3, 4, 5}).map(i ->
                new UnlimitedWhiteListEntry(new Address(parameters, BtcECKey.fromPrivate(BigInteger.valueOf(i)).getPubKeyHash()))
        ).collect(Collectors.toList());
        when(mockedWhitelist.getAll()).thenReturn(entries);
        for (int i = 0; i < 4; i++) {
            when(mockedWhitelist.get(entries.get(i).address())).thenReturn(entries.get(i));
        }
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(4, bridgeSupport.getLockWhitelistSize().intValue());
        Assert.assertNull(bridgeSupport.getLockWhitelistEntryByIndex(-1));
        Assert.assertNull(bridgeSupport.getLockWhitelistEntryByIndex(4));
        Assert.assertNull(bridgeSupport.getLockWhitelistEntryByIndex(5));
        Assert.assertNull(bridgeSupport.getLockWhitelistEntryByAddress(new Address(parameters, BtcECKey.fromPrivate(BigInteger.valueOf(-1)).getPubKeyHash()).toBase58()));
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(entries.get(i), bridgeSupport.getLockWhitelistEntryByIndex(i));
            Assert.assertEquals(entries.get(i), bridgeSupport.getLockWhitelistEntryByAddress(entries.get(i).address().toBase58()));
        }
    }

    @Test
    public void addLockWhitelistAddress_ok() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.put(any(Address.class), any(OneOffWhiteListEntry.class))).then((InvocationOnMock m) -> {
            Address address = m.<Address>getArgument(0);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return true;
        });

        Assert.assertEquals(1, bridgeSupport.addOneOffLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
    }

    @Test
    public void addLockWhitelistAddress_addFails() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        ArgumentCaptor<Address> argument = ArgumentCaptor.forClass(Address.class);
        when(mockedWhitelist.isWhitelisted(any(Address.class))).thenReturn(true);

        Assert.assertEquals(-1, bridgeSupport.addOneOffLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
        verify(mockedWhitelist).isWhitelisted(argument.capture());
        Assert.assertThat(argument.getValue().toBase58(), is("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN"));
    }

    @Test
    public void addLockWhitelistAddress_notAuthorized() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = Hex.decode("0000000000000000000000000000000000aabbcc");
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), bridgeSupport.addOneOffLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void addLockWhitelistAddress_invalidAddress() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(-2, bridgeSupport.addOneOffLockWhitelistAddress(mockedTx, "i-am-invalid", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_ok() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(false);
        int bestChainHeight = 10;
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(storedBlock.getHeight()).thenReturn(bestChainHeight);
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, btcBlockStore);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(100);

        Assert.assertEquals(1, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, times(1)).setDisableBlockHeight(disableBlockDelayBI.intValue() + bestChainHeight);
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_negativeDisableBlockBI() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(false);
        int bestChainHeight = 10;
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(storedBlock.getHeight()).thenReturn(bestChainHeight);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, btcBlockStore);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(-2);

        Assert.assertEquals(-2, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test(expected = ArithmeticException.class)
    public void setLockWhitelistDisableBlockDelay_disableBlockDelayBIBiggerThanInt() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(false);
        int bestChainHeight = 10;
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(storedBlock.getHeight()).thenReturn(bestChainHeight);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, btcBlockStore);
        //Duplicate Int Max Value by 2 because its signed and add 1 to pass the limit
        BigInteger disableBlockDelayBI = BigInteger.valueOf((long) Integer.MAX_VALUE * 2 + 1);

        bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI);
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_overflow() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(false);
        int bestChainHeight = (Integer.MAX_VALUE / 2) + 2;
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(storedBlock.getHeight()).thenReturn(bestChainHeight);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, btcBlockStore);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(Integer.MAX_VALUE / 2);

        Assert.assertEquals(-2, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_maxIntValueDisableBlockBI() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(false);
        int bestChainHeight = 10;
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(storedBlock.getHeight()).thenReturn(bestChainHeight);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, btcBlockStore);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(Integer.MAX_VALUE);

        Assert.assertEquals(-2, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_disabled() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.isDisableBlockSet()).thenReturn(true);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(100);
        Assert.assertEquals(-1, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void setLockWhitelistDisableBlockDelay_notAuthorized() throws IOException, BlockStoreException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = Hex.decode("0000000000000000000000000000000000aabbcc");
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        BigInteger disableBlockDelayBI = BigInteger.valueOf(100);
        Assert.assertEquals(-10, bridgeSupport.setLockWhitelistDisableBlockDelay(mockedTx, disableBlockDelayBI).intValue());
        verify(mockedWhitelist, never()).put(any(), any());
    }

    @Test
    public void removeLockWhitelistAddress_ok() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.remove(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.<Address>getArgument(0);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return true;
        });

        Assert.assertEquals(1, bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void removeLockWhitelistAddress_removeFails() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
                "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        when(mockedWhitelist.remove(any(Address.class))).then((InvocationOnMock m) -> {
            Address address = m.<Address>getArgument(0);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return false;
        });

        Assert.assertEquals(-1, bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
    }

    @Test
    public void removeLockWhitelistAddress_notAuthorized() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = Hex.decode("0000000000000000000000000000000000aabbcc");
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), bridgeSupport.removeLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN").intValue());
        verify(mockedWhitelist, never()).remove(any());
    }

    @Test
    public void removeLockWhitelistAddress_invalidAddress() throws IOException {
        Transaction mockedTx = mock(Transaction.class);
        byte[] senderBytes = ECKey.fromPublicOnly(Hex.decode(
            // Public key hex of the authorized whitelist admin in regtest, taken from BridgeRegTestConstants
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        )).getAddress();
        RskAddress sender = new RskAddress(senderBytes);
        when(mockedTx.getSender()).thenReturn(sender);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(-2, bridgeSupport.removeLockWhitelistAddress(mockedTx, "i-am-invalid").intValue());
        verify(mockedWhitelist, never()).remove(any());
    }

    @Test
    public void getBtcBlockchainInitialBlockHeight() throws IOException {
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BridgeSupport bridgeSupport = getBridgeSupport(null, repository, btcBlockStoreFactory);

        // As we don't have any checkpoint the genesis block at height 0 should be used and returned
        Assert.assertEquals(0, bridgeSupport.getBtcBlockchainInitialBlockHeight());
    }

    @Test
    public void getBtcTransactionConfirmations_inexistentBlockHash() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        BtcBlockStoreWithCache btcBlockStore = mock(RepositoryBtcBlockStoreWithCache.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        doReturn(storedBlock).when(btcBlockStore).getChainHead();

        when(btcBlockStore.getFromCache(blockHash)).thenReturn(null);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);


        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INEXISTENT_BLOCK_HASH_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_blockNotInBestChain() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        Sha256Hash blockHashInMainChain = Sha256Hash.of(Hex.decode("334455"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        BtcBlock blockHeaderInMainChain = mock(BtcBlock.class);
        when(blockHeaderInMainChain.getHash()).thenReturn(blockHashInMainChain);

        int height = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(null);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_blockNotInBestChainBlockWithHeightNotFound() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_blockTooOld() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        final int BLOCK_HEIGHT = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), BLOCK_HEIGHT);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), BLOCK_HEIGHT + 4320 + 1);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);


        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);
        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_TOO_OLD_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_heightInconsistency() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        int height = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenThrow(new BlockStoreException("blah"));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);
        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_merkleBranchDoesNotProve() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        Sha256Hash merkleRoot = Sha256Hash.of(Hex.decode("ddeeff"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getMerkleRoot()).thenReturn(merkleRoot);

        int height = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);
        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);
        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(btcTransactionHash)).thenReturn(Sha256Hash.ZERO_HASH);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, merkleBranch);

        Assert.assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INVALID_MERKLE_BRANCH_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    public void getBtcTransactionConfirmationsGetCost_ok() throws BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        StoredBlock block = new StoredBlock(null, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();

        StoredBlock chainHead = new StoredBlock(btcBlock, new BigInteger("0"), 60);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        args[3] = new Object[]{};
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        Assert.assertEquals(27_000 + 10 * 315, cost);
    }

    @Test
    public void getBtcTransactionConfirmationsGetCost_blockDoesNotExist() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlockStoreWithCache btcBlockStore = mock(RepositoryBtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(null);

        BtcBlock header = mock(BtcBlock.class);
        when(header.getHash()).thenReturn(blockHash);
        StoredBlock chainHead = new StoredBlock(header, new BigInteger("0"), 6000);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        Assert.assertEquals(27_000, cost);
    }

    @Test
    public void getBtcTransactionConfirmationsGetCost_getBestChainHeightError() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        StoredBlock block = new StoredBlock(null, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);
        when(btcBlockStore.getChainHead()).thenThrow(new BlockStoreException(""));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        Assert.assertEquals(27_000, cost);
    }

    @Test
    public void getBtcTransactionConfirmationsGetCost_blockTooDeep() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        StoredBlock block = new StoredBlock(null, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(blockHash)).thenReturn(block);

        BtcBlock header = mock(BtcBlock.class);
        when(header.getHash()).thenReturn(blockHash);
        StoredBlock chainHead = new StoredBlock(header, new BigInteger("0"), 6000);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        Assert.assertEquals(27_000, cost);
    }

    @Test
    public void getBtcBlockchainBlockHashAtDepth() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track);
        BtcBlockStoreWithCache.Factory mockFactory = mock(RepositoryBtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        StoredBlock chainHead = btcBlockStore.getChainHead();
        Assert.assertEquals(0, chainHead.getHeight());
        Assert.assertEquals(btcParams.getGenesisBlock(), chainHead.getHeader());

        Assert.assertEquals(btcParams.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(0));
        try {
            bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1);
            Assert.fail();
        } catch (IndexOutOfBoundsException e) {
        }
        try {
            bridgeSupport.getBtcBlockchainBlockHashAtDepth(1);
            Assert.fail();
        } catch (IndexOutOfBoundsException e) {
        }

        List<BtcBlock> blocks = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));

        Assert.assertEquals(btcParams.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(10));
        try {
            bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1);
            Assert.fail();
        } catch (IndexOutOfBoundsException e) {
        }
        try {
            bridgeSupport.getBtcBlockchainBlockHashAtDepth(11);
            Assert.fail();
        } catch (IndexOutOfBoundsException e) {
        }
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(blocks.get(i).getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(9 - i));
        }
    }

    @Test
    public void eventLoggerBeforeRskip146Fork() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class))).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }

    @Test
    public void eventLoggerAfterRskip146Fork() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class))).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }

    private BridgeStorageProvider getBridgeStorageProviderMockWithProcessedHashes() throws IOException {
        Map<Sha256Hash, Long> mockedHashes = new HashMap<>();
        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);

        for (int i = 0; i < 10; i++) {
            when(providerMock.getHeightIfBtcTxhashIsAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes())))
                    .thenReturn(Optional.of(Long.valueOf((long)i)));
        }

        return providerMock;
    }

    private BridgeSupport getBridgeSupportWithMocksForFederationTests(
            boolean genesis,
            Federation mockedNewFederation,
            Federation mockedGenesisFederation,
            Federation mockedOldFederation,
            PendingFederation mockedPendingFederation,
            ABICallElection mockedFederationElection,
            Block executionBlock) throws IOException {
        return this.getBridgeSupportWithMocksForFederationTests(genesis, mockedNewFederation, mockedGenesisFederation,
                mockedOldFederation, mockedPendingFederation, mockedFederationElection, executionBlock, null);
    }

    private BridgeSupport getBridgeSupportWithMocksForFederationTests(
            boolean genesis,
            Federation mockedNewFederation,
            Federation mockedGenesisFederation,
            Federation mockedOldFederation,
            PendingFederation mockedPendingFederation,
            ABICallElection mockedFederationElection,
            Block executionBlock,
            BridgeEventLogger eventLogger) throws IOException {

        BridgeConstants constantsMock = mock(BridgeConstants.class);
        when(constantsMock.getGenesisFederation()).thenReturn(mockedGenesisFederation);

        when(constantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(constantsMock.getFederationChangeAuthorizer()).thenReturn(bridgeConstants.getFederationChangeAuthorizer());
        when(constantsMock.getFederationActivationAge()).thenReturn(bridgeConstants.getFederationActivationAge());

        class FederationHolder {
            private PendingFederation pendingFederation;
            private Federation activeFederation;
            private Federation retiringFederation;
            private ABICallElection federationElection;

            public List<UTXO> retiringUTXOs = new ArrayList<>();
            public List<UTXO> activeUTXOs = new ArrayList<>();

            PendingFederation getPendingFederation() {
                return pendingFederation;
            }

            void setPendingFederation(PendingFederation pendingFederation) {
                this.pendingFederation = pendingFederation;
            }

            Federation getActiveFederation() {
                return activeFederation;
            }

            void setActiveFederation(Federation activeFederation) {
                this.activeFederation = activeFederation;
            }

            Federation getRetiringFederation() {
                return retiringFederation;
            }

            void setRetiringFederation(Federation retiringFederation) {
                this.retiringFederation = retiringFederation;
            }

            public ABICallElection getFederationElection() {
                return federationElection;
            }

            public void setFederationElection(ABICallElection federationElection) {
                this.federationElection = federationElection;
            }
        }

        final FederationHolder holder = new FederationHolder();
        holder.setPendingFederation(mockedPendingFederation);

        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);

        when(providerMock.getOldFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.retiringUTXOs);
        when(providerMock.getNewFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.activeUTXOs);

        holder.setActiveFederation(genesis ? null : mockedNewFederation);
        holder.setRetiringFederation(mockedOldFederation);
        when(providerMock.getNewFederation()).then((InvocationOnMock m) -> holder.getActiveFederation());
        when(providerMock.getOldFederation()).then((InvocationOnMock m) -> holder.getRetiringFederation());
        when(providerMock.getPendingFederation()).then((InvocationOnMock m) -> holder.getPendingFederation());
        when(providerMock.getFederationElection(any())).then((InvocationOnMock m) -> {
            if (mockedFederationElection != null) {
                holder.setFederationElection(mockedFederationElection);
            }

            if (holder.getFederationElection() == null) {
                AddressBasedAuthorizer auth = m.<AddressBasedAuthorizer>getArgument(0);
                holder.setFederationElection(new ABICallElection(auth));
            }

            return holder.getFederationElection();
        });
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setActiveFederation(m.<Federation>getArgument(0));
            return null;
        }).when(providerMock).setNewFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setRetiringFederation(m.<Federation>getArgument(0));
            return null;
        }).when(providerMock).setOldFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setPendingFederation(m.<PendingFederation>getArgument(0));
            return null;
        }).when(providerMock).setPendingFederation(any());

        return getBridgeSupport(
                constantsMock,
                providerMock,
                null,
                eventLogger,
                executionBlock,
                null
        );
    }

    private BridgeSupport getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(LockWhitelist mockedWhitelist, BtcBlockStoreWithCache btcBlockStore) {
        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);
        when(providerMock.getLockWhitelist()).thenReturn(mockedWhitelist);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        return getBridgeSupport(providerMock, null, mockFactory);
    }

    private BridgeSupport getBridgeSupportWithMocksForWhitelistTests(LockWhitelist mockedWhitelist) {
        return getBridgeSupportWithMocksAndBtcBlockstoreForWhitelistTests(mockedWhitelist, null);
    }

    private BridgeSupport getBridgeSupport(BridgeStorageProvider provider, Repository track) {
        return getBridgeSupport(bridgeConstants, provider, track, mock(BtcBlockStoreWithCache.Factory.class));
    }

    private BridgeSupport getBridgeSupport(BridgeStorageProvider provider, Repository track, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(bridgeConstants, provider, track, blockStoreFactory);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider) {
        return getBridgeSupport(constants, provider, null, mock(BridgeEventLogger.class), null, null);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(constants, provider, track, mock(BridgeEventLogger.class), null, blockStoreFactory);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(
                constants,
                provider,
                track,
                eventLogger,
                new BtcLockSenderProvider(),
                executionBlock,
                blockStoreFactory
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, BtcLockSenderProvider btcLockSenderProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(
                constants,
                provider,
                track,
                eventLogger,
                btcLockSenderProvider,
                executionBlock,
                blockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, BtcLockSenderProvider btcLockSenderProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        return new BridgeSupport(
                constants,
                provider,
                eventLogger,
                btcLockSenderProvider,
                track,
                executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory,
                activations
        );
    }

    private BtcLockSenderProvider getBtcLockSenderProvider(BtcLockSender.TxType txType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getType()).thenReturn(txType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private BtcTransaction createTransaction() {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(new TransactionInput(btcParams, btcTx, new byte[0]));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams)));
        return btcTx;
    }

    private UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
                PegTestUtils.createHash(1),
                1,
                value,
                0,
                false,
                ScriptBuilder.createOutputScript(address));
    }

    private BtcBlockStore getBtcBlockStoreFromBridgeSupport(BridgeSupport bridgeSupport) {
        return (BtcBlockStore) Whitebox.getInternalState(bridgeSupport, "btcBlockStore");
    }

    private void mockChainOfStoredBlocks(BtcBlockStoreWithCache btcBlockStore, BtcBlock targetHeader, int headHeight, int targetHeight) throws BlockStoreException {
        // Simulate that the block is in there by mocking the getter by height,
        // and then simulate that the txs have enough confirmations by setting a high head.
        when(btcBlockStore.getStoredBlockAtMainChainHeight(targetHeight)).thenReturn(new StoredBlock(targetHeader, BigInteger.ONE, targetHeight));
        // Mock current pointer's header
        StoredBlock currentStored = mock(StoredBlock.class);
        BtcBlock currentBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(currentBlock).getHash();
        doReturn(currentBlock).when(currentStored).getHeader();
        when(currentStored.getHeader()).thenReturn(currentBlock);
        when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }

    public static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }
}
