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
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.peg.simples.SimpleBlockChain;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.peg.simples.SimpleWallet;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BlockChainBuilder;
import com.google.common.collect.Lists;
import org.ethereum.config.Constants;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.config.net.TestNetConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.util.encoders.Hex;

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
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/9/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ BridgeUtils.class })
public class BridgeSupportTest {
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;
    public static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    private static BridgeConstants bridgeConstants;
    private static NetworkParameters btcParams;
    private TestSystemProperties config;

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    @Before
    public void setUpOnEachTest(){
        config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcParams = bridgeConstants.getBtcParams();
    }

    @Test
    public void testInitialChainHeadWithoutBtcCheckpoints() throws Exception {
        NetworkParameters _networkParameters = btcParams;
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());
    }

    @Test
    public void testInitialChainHeadWithBtcCheckpoints() throws Exception {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new TestNetConfig());
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcParams = bridgeConstants.getBtcParams();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);
        Assert.assertEquals(1229760, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
    }

    @Test
    public void feePerKbFromStorageProvider() throws Exception {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Coin expected = Coin.MILLICOIN;
        provider.setFeePerKb(expected);
        provider.saveFeePerKb();

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        Assert.assertEquals(expected, bridgeSupport.getFeePerKb());
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithoutBtcCheckpoints() throws Exception {
        NetworkParameters _networkParameters = btcParams;

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(5));
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithBtcCheckpoints() throws Exception {
        NetworkParameters _networkParameters = btcParams;

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        List<BtcBlock> checkpoints = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null) {
            @Override
            InputStream getCheckPoints() {
                return getCheckpoints(_networkParameters, checkpoints);
            }
        };
        Assert.assertEquals(10, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(checkpoints.get(9), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, checkpoints.get(9), 10);
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
            BtcBlock block = new BtcBlock(_networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), 0, new ArrayList<BtcTransaction>());
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
            }
            finally {
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
    public void callUpdateCollectionsGenerateEventLog() throws IOException, BlockStoreException {
        Repository track = new RepositoryImpl(config).startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        List<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, eventLogs);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, eventLogger, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock);

        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        ECKey key = new ECKey();
        tx.sign(key.getPrivKeyBytes());

        bridgeSupport.updateCollections(tx);

        Assert.assertEquals(1, eventLogs.size());

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(1, result.getTopics().size());
        Assert.assertEquals(Bridge.UPDATE_COLLECTIONS_TOPIC, result.getTopics().get(0));

        // Assert log data
        Assert.assertArrayEquals(key.getAddress(), RLP.decode2(result.getData()).get(0).getRLPData());
    }

    @Test
    public void callUpdateCollectionsFundsEnoughForJustTheSmallerTx() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(30,0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(20,0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(10,0));
        provider0.setFeePerKb(Coin.MILLICOIN);

        provider0.getNewFederationBtcUTXOs().add(new UTXO(
                PegTestUtils.createHash(),
                1,
                Coin.valueOf(12,0),
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
        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock);

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

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

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

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

        Blockchain blockchain = builder.setTesting(true).build();


        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock);

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        Assert.assertFalse(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void callUpdateCollectionsThrowsExceededMaxTransactionSize() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

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

        Blockchain blockchain = builder.setTesting(true).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = null;
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();
        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock);

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        Assert.assertFalse(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void minimumProcessFundsMigrationValue() throws IOException, BlockStoreException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation oldFederation = bridgeConstants.getGenesisFederation();
        Federation newFederation = new Federation(
                Collections.singletonList(new BtcECKey(new SecureRandom())),
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
        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, rskCurrentBlock);

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
    public void callUpdateCollectionsChangeGetsOutOfDust() throws IOException, BlockStoreException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        Map<byte[], BigInteger> preMineMap = new HashMap<byte[], BigInteger>();
        preMineMap.put(PrecompiledContracts.BRIDGE_ADDR.getBytes(), LIMIT_MONETARY_BASE.asBigInteger());

        BlockGenerator blockGenerator = new BlockGenerator();
        Genesis genesisBlock = (Genesis) blockGenerator.getNewGenesisBlock(0, preMineMap);

        List<Block> blocks = blockGenerator.getSimpleBlockChain(genesisBlock, 10);

        BlockChainBuilder builder = new BlockChainBuilder();

        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        Repository repository = blockchain.getRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.COIN);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.COIN.add(Coin.valueOf(100)), 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction tx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock);

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600))), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600)), repository.getBalance(config.getBlockchainConfig().getCommonConstants().getBurnAddress()));
        // Check the wallet has been emptied
        Assert.assertTrue(provider.getNewFederationBtcUTXOs().isEmpty());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException, BlockStoreException {
        // Bridge constants and btc context
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context context = new Context(bridgeConstants.getBtcParams());

        // Fake wallet returned every time
        PowerMockito.mockStatic(BridgeUtils.class);
        PowerMockito.when(BridgeUtils.getFederationSpendWallet(any(Context.class), any(Federation.class), any(List.class))).thenReturn(new SimpleWallet(context));

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

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
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);

        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).build();

        for (Block block : blocks)
            blockchain.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction rskTx = Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, rskCurrentBlock);

        bridgeSupport.updateCollections(rskTx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(2, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(1, provider2.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addBlockHeaderToBlockchain() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNotNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addSignatureToMissingTransaction() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, (Block) null);

        bridgeSupport.addSignature(1, federation.getPublicKeys().get(0), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureFromInvalidFederator() throws Exception {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), PrecompiledContracts.BRIDGE_ADDR, (Block) null);

        bridgeSupport.addSignature(1, new BtcECKey(), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureWithInvalidSignature() throws Exception {
        addSignatureFromValidFederator(Lists.newArrayList(new BtcECKey()), 1, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    @Test
    public void addSignatureCreateEventLog() throws Exception {
        // Setup
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository track = new RepositoryImpl(config).startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(prevOut).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        TransactionOutput output = new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams));
        btcTx.addOutput(output);

        // Save btc tx to be signed
        final Keccak256 rskTxHash = PegTestUtils.createHash3();
        provider.getRskTxsWaitingForSignatures().put(rskTxHash, btcTx);
        provider.save();
        track.commit();

        // Setup BridgeSupport
        List<LogInfo> eventLogs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, eventLogs);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, eventLogger, contractAddress, null);

        // Create signed hash of Btc tx
        Script inputScript = btcTx.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sigHash = btcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey privateKeyToSignWith = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0);

        BtcECKey.ECDSASignature sig = privateKeyToSignWith.sign(sigHash);
        List derEncodedSigs = Collections.singletonList(sig.encodeToDER());

        BtcECKey federatorPubKey = findPublicKeySignedBy(federation.getPublicKeys(), privateKeyToSignWith);
        bridgeSupport.addSignature(1, federatorPubKey, derEncodedSigs, rskTxHash.getBytes());

        Assert.assertEquals(1, eventLogs.size());

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(1, result.getTopics().size());
        Assert.assertEquals(Bridge.ADD_SIGNATURE_TOPIC, result.getTopics().get(0));

        // Assert log data
        Assert.assertNotNull(result.getData());
        List<RLPElement> rlpData = RLP.decode2(result.getData());
        Assert.assertEquals(1 , rlpData.size());
        RLPList dataList = (RLPList)rlpData.get(0);
        Assert.assertEquals(3, dataList.size());
        Assert.assertArrayEquals(btcTx.getHashAsString().getBytes(), dataList.get(0).getRLPData());
        Assert.assertArrayEquals(federatorPubKey.getPubKeyHash(), dataList.get(1).getRLPData());
        Assert.assertArrayEquals(rskTxHash.getBytes(), dataList.get(2).getRLPData());
    }

    @Test
    public void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    public void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    public void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys();
        List<BtcECKey> keys = Lists.newArrayList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    @Test
    public void addSignatureMultipleInputsPartiallyValid() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl(config);

        final Keccak256 keccak256 = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);

        BtcTransaction prevTx1 = new BtcTransaction(btcParams);
        TransactionOutput prevOut1 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx1.addOutput(prevOut1);
        BtcTransaction prevTx2 = new BtcTransaction(btcParams);
        TransactionOutput prevOut2 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx2.addOutput(prevOut2);
        BtcTransaction prevTx3 = new BtcTransaction(btcParams);
        TransactionOutput prevOut3 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx3.addOutput(prevOut3);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut1).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut2).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut3).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, logs);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, eventLogger, contractAddress, (Block) null);

        // Generate valid signatures for inputs
        List<byte[]> derEncodedSigsFirstFed = new ArrayList<>();
        List<byte[]> derEncodedSigsSecondFed = new ArrayList<>();
        BtcECKey privateKeyOfFirstFed = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0);
        BtcECKey privateKeyOfSecondFed = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(1);

        BtcECKey.ECDSASignature lastSig = null;
        for (int i = 0; i < 3; i++) {
            Script inputScript = t.getInput(i).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = t.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);

            // Sign the last input with a random key
            // but keep the good signature for a subsequent call
            BtcECKey.ECDSASignature sig = privateKeyOfFirstFed.sign(sighash);
            if (i == 2) {
                lastSig = sig;
                sig = new BtcECKey().sign(sighash);
            }
            derEncodedSigsFirstFed.add(sig.encodeToDER());
            derEncodedSigsSecondFed.add(privateKeyOfSecondFed.sign(sighash).encodeToDER());
        }

        // Sign with two valid signatuers and one invalid signature
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();
        track.commit();

        // Sign with two valid signatuers and one malformed signature
        byte[] malformedSignature = new byte[lastSig.encodeToDER().length];
        for (int i = 0; i < malformedSignature.length; i++) {
            malformedSignature[i] = (byte) i;
        }
        derEncodedSigsFirstFed.set(2, malformedSignature);
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();
        track.commit();

        // Sign with fully valid signatures for same federator
        derEncodedSigsFirstFed.set(2, lastSig.encodeToDER());
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();
        track.commit();

        // Sign with second federation
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeyOfSecondFed), derEncodedSigsSecondFed, keccak256.getBytes());
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertThat(logs, is(not(empty())));
        Assert.assertThat(logs, hasSize(5));
        LogInfo releaseTxEvent = logs.get(4);
        Assert.assertThat(releaseTxEvent.getTopics(), hasSize(1));
        Assert.assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
        BtcTransaction releaseTx = new BtcTransaction(bridgeConstants.getBtcParams(), ((RLPList)RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
        // Verify all inputs fully signed
        for (int i = 0; i < releaseTx.getInputs().size(); i++) {
            Script retrievedScriptSig = releaseTx.getInput(i).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(2).data.length > 0);
        }
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice Sign again with the same key
     * @param expectedResult "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl(config);

        final Keccak256 keccak256 = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, logs);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, eventLogger, contractAddress, (Block) null);

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List derEncodedSigs = new ArrayList();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(0)), derEncodedSigs, keccak256.getBytes());
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), BtcECKey.CURVE);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(0)), Lists.newArrayList(sig2.encodeToDER()), keccak256.getBytes());
        }
        if (privateKeysToSignWith.size()>1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List derEncodedSigs2 = new ArrayList();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(1, findPublicKeySignedBy(federation.getPublicKeys(), privateKeysToSignWith.get(1)), derEncodedSigs2, keccak256.getBytes());
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        if ("FullySigned".equals(expectedResult)) {
            Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
            Assert.assertThat(logs, is(not(empty())));
            Assert.assertThat(logs, hasSize(3));
            LogInfo releaseTxEvent = logs.get(2);
            Assert.assertThat(releaseTxEvent.getTopics(), hasSize(1));
            Assert.assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
            BtcTransaction releaseTx = new BtcTransaction(bridgeConstants.getBtcParams(), ((RLPList)RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
            Script retrievedScriptSig = releaseTx.getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(2).data.length > 0);
        } else {
            Script retrievedScriptSig = provider.getRskTxsWaitingForSignatures().get(keccak256).getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = false; // for "InvalidParameters"
            if ("PartiallySigned".equals(expectedResult)) {
                expectSignatureToBePersisted = true;
            }
            Assert.assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(false, retrievedScriptSig.getChunks().get(2).data.length > 0);
        }
    }

    private BtcECKey findPublicKeySignedBy(List<BtcECKey> pubs, BtcECKey pk) {
        for (BtcECKey pub : pubs) {
            if (Arrays.equals(pk.getPubKey(), pub.getPubKey())) {
                return pub;
            }
        }
        return null;
    }

    @Test
    public void releaseBtcWithDustOutput() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(config, TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtc() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(config, TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void releaseBtcFromContract() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(config, TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());
        track.saveCode(tx.getSender(), new byte[] {0x1});
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        try {
            bridgeSupport.releaseBtc(tx);
        } catch (Program.OutOfGasException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void registerBtcTransactionOfAlreadyProcessedTransaction() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        provider.getBtcTxHashesAlreadyProcessed().put(tx.getHash(), 1L);

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, null);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionNotInMerkleTree() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNegativeHeight() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), -1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNotEnoughtHeight() throws BlockStoreException, AddressFormatException, IOException {
        NetworkParameters _networkParameters = btcParams;

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(_networkParameters, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertTrue(provider2.getNewFederationBtcUTXOs().isEmpty());
        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
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
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlockChain btcBlockChain = mock(BtcBlockChain.class);
        doReturn(storedBlock).when(btcBlockChain).getChainHead();

        BridgeSupport bridgeSupport = new BridgeSupport(
                mock(TestSystemProperties.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(BtcBlockstoreWithCache.class),
                btcBlockChain);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), noInputsTx.bitcoinSerialize(), btcTxHeight, pmt.bitcoinSerialize());
    }

    @Test
    public void registerBtcTransactionTxNotLockNorReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));


        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);

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

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(0, provider2.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = new RepositoryImpl(config);
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Repository track = repository.startTracking();
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
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
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = bridgeConstants.getFederatorPrivateKeys().get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, federation.getPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = bridgeConstants.getFederatorPrivateKeys().get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, federation.getPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);
        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(1, provider2.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN, provider2.getNewFederationBtcUTXOs().get(0).getValue());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(1, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void registerBtcTransactionMigrationTx() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(activeFederationKeys, Instant.ofEpochMilli(2000L), 2L, parameters);

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiringFederation = new Federation(retiringFederationKeys, Instant.ofEpochMilli(1000L), 1L, parameters);

        Repository repository = new RepositoryImpl(config);
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
        Script redeemScript = ScriptBuilder.createRedeemScript(retiringFederation.getNumberOfSignaturesRequired(), retiringFederation.getPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = retiringFederationKeys.get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = retiringFederationKeys.get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, retiringFederation.getPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);


        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        provider.setNewFederation(activeFederation);
        provider.setOldFederation(retiringFederation);
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);
        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        List<UTXO> activeFederationBtcUTXOs = provider.getNewFederationBtcUTXOs();
        List<Coin> activeFederationBtcCoins = activeFederationBtcUTXOs.stream().map(UTXO::getValue).collect(Collectors.toList());
        assertThat(activeFederationBtcUTXOs, hasSize(1));
        assertThat(activeFederationBtcCoins, hasItem(Coin.COIN));
    }

    @Test
    public void registerBtcTransactionLockTxWhitelisted() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(federation1Keys, Instant.ofEpochMilli(1000L), 0L, parameters);

        List<BtcECKey> federation2Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")),
        });
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(federation2Keys, Instant.ofEpochMilli(2000L), 0L, parameters);

        Repository repository = new RepositoryImpl(config);
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(this.btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(this.btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(this.btcParams);
        tx3.addOutput(Coin.COIN.multiply(2), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(3), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        Address address1 = srcKey1.toAddress(parameters);
        Address address2 = srcKey2.toAddress(parameters);
        Address address3 = srcKey3.toAddress(parameters);
        whitelist.put(address1, Coin.COIN.multiply(5));
        whitelist.put(address2, Coin.COIN.multiply(10));
        whitelist.put(address3, Coin.COIN.multiply(2).add(Coin.COIN.multiply(3)));

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx2.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx3.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

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

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(2, provider2.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(2, provider2.getOldFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider2.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(2), provider2.getNewFederationBtcUTXOs().get(1).getValue());
        Assert.assertEquals(Coin.COIN.multiply(10), provider2.getOldFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(Coin.COIN.multiply(3), provider2.getOldFederationBtcUTXOs().get(1).getValue());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider2.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(3, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void registerBtcTransactionLockTxNotWhitelisted() throws BlockStoreException, AddressFormatException, IOException {
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        NetworkParameters parameters = bridgeConstants.getBtcParams();

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(federation1Keys, Instant.ofEpochMilli(1000L), 0L, parameters);

        List<BtcECKey> federation2Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")),
        });
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(federation2Keys, Instant.ofEpochMilli(2000L), 0L, parameters);

        Repository repository = new RepositoryImpl(config);
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(this.btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(this.btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(this.btcParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockstoreWithCache btcBlockStore = new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        BridgeSupport bridgeSupport = new BridgeSupport(config, track, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, btcBlockStore, btcBlockChain);
        Whitebox.setInternalState(bridgeSupport, "rskExecutionBlock", executionBlock);
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        Transaction rskTx1 = getMockedRskTxWithHash("aa");
        Transaction rskTx2 = getMockedRskTxWithHash("bb");
        Transaction rskTx3 = getMockedRskTxWithHash("cc");

        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assert.assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());

        Assert.assertEquals(0, provider2.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider2.getOldFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(3, provider2.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider2.getReleaseTransactionSet().getEntries()
                .stream()
                .map(e -> e.getTransaction())
                .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(5).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey1.toAddress(parameters), releaseTx.getOutput(0).getAddressFromP2PKHScript(parameters));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        releaseTx = releaseTxs.get(1);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(7).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey3.toAddress(parameters), releaseTx.getOutput(0).getAddressFromP2PKHScript(parameters));
        Assert.assertEquals(2, releaseTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = releaseTx.getInputs().stream().map(i -> i.getOutpoint()).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).collect(Collectors.toList());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        Assert.assertEquals(0, releaseOutpoints.get(0).getIndex());
        Assert.assertEquals(1, releaseOutpoints.get(1).getIndex());

        // Third release tx should correspond to the 10 BTC lock tx
        releaseTx = releaseTxs.get(2);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(10).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey2.toAddress(parameters), releaseTx.getOutput(0).getAddressFromP2PKHScript(parameters));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx2.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());

        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(3, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void isBtcTxHashAlreadyProcessed() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = new BridgeSupport(config, null, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), getBridgeStorageProviderMockWithProcessedHashes(), null, null);

        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes())));
        }
        Assert.assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of("anything".getBytes())));
    }

    @Test
    public void getBtcTxHashProcessedHeight() throws IOException, BlockStoreException {
        BridgeSupport bridgeSupport = new BridgeSupport(config, null, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), getBridgeStorageProviderMockWithProcessedHashes(), null, null);

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals((long) i, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of(("hash_" + i).getBytes())).longValue());
        }
        Assert.assertEquals(-1L, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of("anything".getBytes())).longValue());
    }

    @Test
    public void getFederationMethods_genesis() throws IOException {
        Federation activeFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                getTestFederationPublicKeys(6),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(true, activeFederation, genesisFederation, null, null, null, null);

        Assert.assertEquals(6, bridgeSupport.getFederationSize().intValue());
        Assert.assertEquals(4, bridgeSupport.getFederationThreshold().intValue());
        Assert.assertEquals(genesisFederation.getAddress().toString(), bridgeSupport.getFederationAddress().toString());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(6);
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getFederationMethods_active() throws IOException {
        Federation activeFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = new Federation(
                getTestFederationPublicKeys(6),
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
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(3);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getFederationMethods_newActivated() throws IOException {
        Federation newFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                15L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation oldFederation = new Federation(
                getTestFederationPublicKeys(6),
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
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(3);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getFederationMethods_newNotActivated() throws IOException {
        Federation newFederation = new Federation(
                getTestFederationPublicKeys(3),
                Instant.ofEpochMilli(1000),
                15L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation oldFederation = new Federation(
                getTestFederationPublicKeys(6),
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
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(6);
        for (int i = 0; i < 6; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getFederatorPublicKey(i)));
        }
    }

    @Test
    public void getRetiringFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        Assert.assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        Assert.assertNull(bridgeSupport.getRetiringFederatorPublicKey(0));
    }

    @Test
    public void getRetiringFederationMethods_presentNewInactive() throws IOException {
        Federation mockedNewFederation = new Federation(
                getTestFederationPublicKeys(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                getTestFederationPublicKeys(4),
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
    }

    @Test
    public void getRetiringFederationMethods_presentNewActive() throws IOException {
        Federation mockedNewFederation = new Federation(
                getTestFederationPublicKeys(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                getTestFederationPublicKeys(4),
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
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(4);
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getRetiringFederatorPublicKey(i)));
        }
    }

    @Test
    public void getPendingFederationMethods_none() throws IOException {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null,  null, null);

        Assert.assertEquals(-1, bridgeSupport.getPendingFederationSize().intValue());
        Assert.assertNull(bridgeSupport.getPendingFederatorPublicKey(0));
    }

    @Test
    public void getPendingFederationMethods_present() throws IOException {
        PendingFederation mockedPendingFederation = new PendingFederation(
                getTestFederationPublicKeys(5)
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, mockedPendingFederation, null, null);

        Assert.assertEquals(5, bridgeSupport.getPendingFederationSize().intValue());
        List<BtcECKey> publicKeys = getTestFederationPublicKeys(5);
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(Arrays.equals(publicKeys.get(i).getPubKey(), bridgeSupport.getPendingFederatorPublicKey(i)));
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

        public RskAddress getVoter() { return voter; }

        public ABICallElection getElection() { return election; }

        public ABICallSpec getSpec() { return spec; }

        public Transaction getTx() { return tx; }

        public ABICallSpec getWinner() { return winner; }
        public void setWinner(ABICallSpec winner) { this.winner = winner; }

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
                getTestFederationPublicKeys(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                getTestFederationPublicKeys(4),
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
                getTestFederationPublicKeys(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation mockedOldFederation = new Federation(
                getTestFederationPublicKeys(4),
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
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    public void addFederatorPublicKey_okKeys() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
                Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
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
        Assert.assertTrue(Arrays.equals(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"), bridgeSupport.getPendingFederatorPublicKey(1)));
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

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
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
    public void rollbackFederation_ok() throws IOException {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));
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
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))));

        VotingMocksProvider mocksProvider = new VotingMocksProvider("commit", new byte[][]{
                pendingFederation.getHash().getBytes()
        }, true);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getTimestamp()).thenReturn(15005L);
        when(executionBlock.getNumber()).thenReturn(15L);

        Federation expectedFederation = new Federation(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))),
                Instant.ofEpochMilli(15005L), 15L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        Federation newFederation = new Federation(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
                BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
                BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))),
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
            when(utxoMock.getIndex()).thenReturn((long)i);
            when(utxoMock.getValue()).thenReturn(Coin.valueOf((i+1)*1000));
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
            Assert.assertEquals(Coin.valueOf((i+1)*1000), provider.getOldFederationBtcUTXOs().get(i).getValue());
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
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }));

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
        PendingFederation pendingFederation = new PendingFederation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12"))
        }));

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
        Federation expectedFederation = new Federation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
            Assert.assertEquals(m.getArgumentAt(0, Context.class), expectedContext);
            Assert.assertEquals(m.getArgumentAt(1, Federation.class), expectedFederation);
            Assert.assertEquals(m.getArgumentAt(2, Object.class), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getActiveFederationWallet());
    }

    @Test
    public void getRetiringFederationWallet_nonEmpty() throws IOException {
        Federation mockedNewFederation = new Federation(
                getTestFederationPublicKeys(2),
                Instant.ofEpochMilli(2000),
                10L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation expectedFederation = new Federation(Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

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
            Assert.assertEquals(m.getArgumentAt(0, Context.class), expectedContext);
            Assert.assertEquals(m.getArgumentAt(1, Federation.class), expectedFederation);
            Assert.assertEquals(m.getArgumentAt(2, Object.class), expectedUtxos);
            return expectedWallet;
        });

        Assert.assertSame(expectedWallet, bridgeSupport.getRetiringFederationWallet());
    }

    @Test
    public void getLockWhitelistMethods() throws IOException {
        NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        LockWhitelist mockedWhitelist = mock(LockWhitelist.class);
        when(mockedWhitelist.getSize()).thenReturn(4);
        List<Address> addresses = Arrays.stream(new Integer[]{2,3,4,5}).map(i ->
            new Address(parameters, BtcECKey.fromPrivate(BigInteger.valueOf(i)).getPubKeyHash())
        ).collect(Collectors.toList());
        when(mockedWhitelist.getAddresses()).thenReturn(addresses);
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForWhitelistTests(mockedWhitelist);

        Assert.assertEquals(4, bridgeSupport.getLockWhitelistSize().intValue());
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(-1));
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(4));
        Assert.assertNull(bridgeSupport.getLockWhitelistAddress(5));
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(addresses.get(i).toBase58(), bridgeSupport.getLockWhitelistAddress(i));
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

        when(mockedWhitelist.put(any(Address.class), any(Coin.class))).then((InvocationOnMock m) -> {
            Address address = m.getArgumentAt(0, Address.class);
            Assert.assertEquals("mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", address.toBase58());
            return true;
        });

        Assert.assertEquals(1, bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
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

        Assert.assertEquals(-1, bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
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

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), bridgeSupport.addLockWhitelistAddress(mockedTx, "mwKcYS3H8FUgrPtyGMv3xWvf4jgeZUkCYN", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
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

        Assert.assertEquals(-2, bridgeSupport.addLockWhitelistAddress(mockedTx, "i-am-invalid", BigInteger.valueOf(Coin.COIN.getValue())).intValue());
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
            Address address = m.getArgumentAt(0, Address.class);
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
            Address address = m.getArgumentAt(0, Address.class);
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

    @Test(expected = NullPointerException.class)
    public void voteFeePerKbChange_nullFeeThrows() {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(null));
        when(tx.getSender())
                .thenReturn(new RskAddress(ByteUtil.leftPadBytes(new byte[] {0x43}, 20)));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(config, repositoryMock, null, constants, provider, null, null);
        bridgeSupport.voteFeePerKbChange(tx, null);
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote() {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);
        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(false);

        BridgeSupport bridgeSupport = new BridgeSupport(config, repositoryMock, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), provider, null, null);
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVote() {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);
        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);

        BridgeSupport bridgeSupport = new BridgeSupport(config, repositoryMock, null, constants, provider, null, null);
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVoteWithFeeChange() {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);
        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(1);

        BridgeSupport bridgeSupport = new BridgeSupport(config, repositoryMock, null, constants, provider, null, null);
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider).setFeePerKb(Coin.CENT);
    }

    @Test
    public void getBtcBlockchainInitialBlockHeight() {
        BridgeSupport bridgeSupport = new BridgeSupport(config, null, null, BridgeRegTestConstants.getInstance(), null, null, null);
        StoredBlock initialBlockMock = mock(StoredBlock.class);
        when(initialBlockMock.getHeight()).thenReturn(123);
        Whitebox.setInternalState(bridgeSupport, "initialBtcStoredBlock", initialBlockMock);

        Assert.assertEquals(123, bridgeSupport.getBtcBlockchainInitialBlockHeight());
    }

    @Test
    public void getBtcBlockchainBlockHashAtDepth() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = config.getBlockchainConfig();
        config.setBlockchainConfig(new RegTestConfig());
        NetworkParameters networkParameters = config.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl(config);
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants());
        BridgeSupport bridgeSupport = new BridgeSupport(config, track, mock(BridgeEventLogger.class), provider, null);
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        Assert.assertEquals(networkParameters.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(0));
        try { bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1); Assert.fail(); } catch (IndexOutOfBoundsException e) {}
        try { bridgeSupport.getBtcBlockchainBlockHashAtDepth(1); Assert.fail(); } catch (IndexOutOfBoundsException e) {}

        List<BtcBlock> blocks = createBtcBlocks(networkParameters, networkParameters.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));

        Assert.assertEquals(networkParameters.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(10));
        try { bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1); Assert.fail(); } catch (IndexOutOfBoundsException e) {}
        try { bridgeSupport.getBtcBlockchainBlockHashAtDepth(11); Assert.fail(); } catch (IndexOutOfBoundsException e) {}
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(blocks.get(i).getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(9-i));
        }

        config.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    private BridgeStorageProvider getBridgeStorageProviderMockWithProcessedHashes() throws IOException {
        Map<Sha256Hash, Long> mockedHashes = new HashMap<>();
        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);
        when(providerMock.getBtcTxHashesAlreadyProcessed()).thenReturn(mockedHashes);

        for (int i = 0; i < 10; i++) {
            mockedHashes.put(Sha256Hash.of(("hash_" + i).getBytes()), (long) i);
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
        when(constantsMock.getFederationChangeAuthorizer()).thenReturn(BridgeRegTestConstants.getInstance().getFederationChangeAuthorizer());
        when(constantsMock.getFederationActivationAge()).thenReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge());

        class FederationHolder {
            private PendingFederation pendingFederation;
            private Federation activeFederation;
            private Federation retiringFederation;
            private ABICallElection federationElection;

            public List<UTXO> retiringUTXOs = new ArrayList<>();
            public List<UTXO> activeUTXOs = new ArrayList<>();

            PendingFederation getPendingFederation() { return pendingFederation; }
            void setPendingFederation(PendingFederation pendingFederation) { this.pendingFederation = pendingFederation; }

            Federation getActiveFederation() { return activeFederation; }
            void setActiveFederation(Federation activeFederation) { this.activeFederation = activeFederation; }

            Federation getRetiringFederation() { return retiringFederation; }
            void setRetiringFederation(Federation retiringFederation) { this.retiringFederation = retiringFederation; }

            public ABICallElection getFederationElection() { return federationElection; }
            public void setFederationElection(ABICallElection federationElection) { this.federationElection = federationElection; }
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
                AddressBasedAuthorizer auth = m.getArgumentAt(0, AddressBasedAuthorizer.class);
                holder.setFederationElection(new ABICallElection(auth));
            }

            return holder.getFederationElection();
        });
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setActiveFederation(m.getArgumentAt(0, Federation.class));
            return null;
        }).when(providerMock).setNewFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setRetiringFederation(m.getArgumentAt(0, Federation.class));
            return null;
        }).when(providerMock).setOldFederation(any());
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.setPendingFederation(m.getArgumentAt(0, PendingFederation.class));
            return null;
        }).when(providerMock).setPendingFederation(any());

        BridgeSupport result = new BridgeSupport(config, null, eventLogger, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), providerMock, null, null);

        Whitebox.setInternalState(result, "bridgeConstants", constantsMock);
        Whitebox.setInternalState(result, "rskExecutionBlock", executionBlock);

        return result;
    }

    private BridgeSupport getBridgeSupportWithMocksForWhitelistTests(LockWhitelist mockedWhitelist) throws IOException {
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        when(constantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(constantsMock.getLockWhitelistChangeAuthorizer()).thenReturn(BridgeRegTestConstants.getInstance().getLockWhitelistChangeAuthorizer());

        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);
        when(providerMock.getLockWhitelist()).thenReturn(mockedWhitelist);

        BridgeSupport result = new BridgeSupport(config, null, null, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), providerMock, null, null);

        Whitebox.setInternalState(result, "bridgeConstants", constantsMock);

        return result;
    }

    private List<BtcECKey> getTestFederationPublicKeys(int amount) {
        List<BtcECKey> result = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            result.add(BtcECKey.fromPrivate(BigInteger.valueOf((i+1) * 100)));
        }
        result.sort(BtcECKey.PUBKEY_COMPARATOR);
        return result;
    }
    
    private BtcTransaction createTransaction() {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(new TransactionInput(btcParams, btcTx, new byte[0]));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams)));
        return btcTx;
        //new SimpleBtcTransaction(btcParams, PegTestUtils.createHash());
    }

    private Transaction getMockedRskTxWithHash(String s) {
        byte[] hash = Keccak256Helper.keccak256(s);
        return new SimpleRskTransaction(hash);
    }

    private UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
                PegTestUtils.createHash(),
                1,
                value,
                0,
                false,
                ScriptBuilder.createOutputScript(address));
    }
}
