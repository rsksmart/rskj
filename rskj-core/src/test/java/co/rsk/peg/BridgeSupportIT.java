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

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.FederationMember.KeyType;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.*;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.simples.SimpleBlockChain;
import co.rsk.peg.storage.*;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.*;
import co.rsk.peg.whitelist.*;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import co.rsk.test.builders.*;
import co.rsk.trie.Trie;
import com.google.common.collect.Lists;
import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.Program;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Created by ajlopez on 6/9/2016.
 */

@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to some setup generalizations
@MockitoSettings(strictness = Strictness.LENIENT)
public class BridgeSupportIT {
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final BlockDifficulty TEST_DIFFICULTY = new BlockDifficulty(BigInteger.ONE);

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private final FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();

    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
    private FederationConstants federationConstants;
    private NetworkParameters btcParams;
    private ActivationConfig.ForBlock activationsBeforeForks;
    private ActivationConfig.ForBlock activations;

    private SignatureCache signatureCache;
    private FeePerKbSupport feePerKbSupport;
    private WhitelistSupport whitelistSupport;
    private WhitelistStorageProvider whitelistStorageProvider;
    private LockingCapSupport lockingCapSupport;
    private Repository track;

    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUpOnEachTest() {
        federationConstants = bridgeRegTestConstants.getFederationConstants();
        btcParams = bridgeRegTestConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activations = mock(ActivationConfig.ForBlock.class);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        StorageAccessor inMemoryStorage = new InMemoryStorage();
        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        whitelistSupport = new WhitelistSupportImpl(
            WhitelistMainNetConstants.getInstance(),
            whitelistStorageProvider,
            activations,
            signatureCache
        );
        LockingCapStorageProvider lockingCapStorageProvider = new LockingCapStorageProviderImpl(inMemoryStorage);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            activations,
            LockingCapMainNetConstants.getInstance(),
            signatureCache
        );

        track = createRepository().startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            btcParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR,
            btcParams,
            activations);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeRegTestConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        this.bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withActivations(activations)
            .build();
    }

    @Test
    void initialChainHeadWithoutBtcCheckpoints() throws Exception {
        // Act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        // Assert
        StoredBlock actualChainHead = bridgeSupport.getBtcBlockStore().getChainHead();
        int expectedChainHead = 0;
        assertEquals(expectedChainHead, actualChainHead.getHeight());
        BtcBlock expectedHeader = btcParams.getGenesisBlock();
        assertEquals(expectedHeader, actualChainHead.getHeader());
    }

    @Test
    void initialChainHeadWithBtcCheckpoints() throws Exception {
        // arrange
        NetworkParameters btcNetworkParams = bridgeMainNetConstants.getBtcParams();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR,
            btcNetworkParams,
            activations);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            btcNetworkParams);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeMainNetConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        this.bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withActivations(activations)
            .build();

        // act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        // assert
        assertGenesisFedCreationTimeIsCheckpointBestChainHeight(bridgeMainNetConstants);
    }

    @ParameterizedTest
    @CsvSource(
        {
            "12-byte-chainwork.production.checkpoints",
            "12-byte-chainwork-mix-format.production.checkpoints"
        }
    )
    void initialChainHeadWithBtcCheckpoints_whenCheckpointsWith12BytesChainWork_before_RSKIP454_ok(
        String checkpointFileName) throws Exception {
        // arrange
        arrangeCheckpointsSource(checkpointFileName, bridgeMainNetConstants);

        activations =  ActivationConfigsForTest.arrowhead631().forBlock(0);

        NetworkParameters btcNetworkParams = bridgeMainNetConstants.getBtcParams();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            btcNetworkParams);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeMainNetConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        this.bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withActivations(activations)
            .build();

        // act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();
        assertGenesisFedCreationTimeIsCheckpointBestChainHeight(bridgeMainNetConstants);
    }

    private void arrangeCheckpointsSource(String checkpointFileName,
        BridgeConstants bridgeConstants) throws IOException {
        String checkpointToCreate = "/rskbitcoincheckpoints/" + bridgeConstants.getBtcParams().getId() + ".checkpoints";

        Path target = Paths.get(getClass().getResource(checkpointToCreate).getPath());

        Path source = Paths.get(getClass().getResource("/checkpoints/" + checkpointFileName).getPath());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void initialChainHeadWithBtcCheckpoints_whenCheckpointsWith32BytesChainWork_before_RSKIP454_shouldFail() throws Exception {
        // arrange
        arrangeCheckpointsSource("32-byte-chainwork.production.checkpoints", bridgeMainNetConstants);
        activations =  ActivationConfigsForTest.arrowhead631().forBlock(0);

        NetworkParameters btcNetworkParams = bridgeMainNetConstants.getBtcParams();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            btcNetworkParams);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeMainNetConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        this.bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withActivations(activations)
            .build();

        // act and assert
        Assertions.assertThrows(IllegalArgumentException.class, bridgeSupport::getBtcBlockchainBestChainHeight, "The given number does not fit in 12");
    }

    @ParameterizedTest
    @CsvSource(
        {
            "12-byte-chainwork.production.checkpoints",
            "12-byte-chainwork.production.checkpoints",
            "32-byte-chainwork.production.checkpoints",
            "12-byte-chainwork-mix-format.production.checkpoints"
        }
    )
    void initialChainHeadWithBtcCheckpoints_whenCheckpointsWith32BytesChainWork_after_RSKIP454_ok(
        String checkpointFileName) throws Exception {
        // arrange
        arrangeCheckpointsSource(checkpointFileName, bridgeMainNetConstants);
        activations =  ActivationConfigsForTest.lovell700().forBlock(0);

        NetworkParameters btcNetworkParams = bridgeMainNetConstants.getBtcParams();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            btcNetworkParams);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeMainNetConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        this.bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withActivations(activations)
            .build();

        // act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        // assert
        assertGenesisFedCreationTimeIsCheckpointBestChainHeight(bridgeMainNetConstants);
    }

    private void assertGenesisFedCreationTimeIsCheckpointBestChainHeight(BridgeConstants bridgeConstants)
        throws BlockStoreException, IOException {
        InputStream checkpointsStream = bridgeSupport.getCheckPoints();
        CheckpointManager manager = new CheckpointManager(bridgeConstants.getBtcParams(),
            checkpointsStream);

        /**
         * Time to use in CheckpointManager adjust checkpoint backwards by a week to account for possible clock drift in the block headers.
         * For more detail please see {@link CheckpointManager#checkpoint(NetworkParameters, InputStream, co.rsk.bitcoinj.store.BtcBlockStore, long)}
         */
        final long dayInSeconds = 24 * 60 * 60;
        final long weekInSeconds = 7 * dayInSeconds;
        long genesisFedCreationTimeAdjusted = bridgeSupport.getActiveFederation().getCreationTime().toEpochMilli() - weekInSeconds;
        StoredBlock checkpoint = manager.getCheckpointBefore(genesisFedCreationTimeAdjusted);

        assertEquals(checkpoint.getHeight(), bridgeSupport.getBtcBlockchainBestChainHeight());
    }

    @Test
    void feePerKbFromStorageProvider() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        Coin expected = Coin.MILLICOIN;

        when(feePerKbSupport.getFeePerKb()).thenReturn(expected);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        assertEquals(expected, bridgeSupport.getFeePerKb());
    }

    @Test
    void testGetBtcBlockchainBlockLocatorWithoutBtcCheckpoints() throws Exception {
        // act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        // assert
        StoredBlock chainHead = bridgeSupport.getBtcBlockStore().getChainHead();
        assertEquals(0, chainHead.getHeight());
        assertEquals(btcParams.getGenesisBlock(), chainHead.getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        assertEquals(1, locator.size());
        assertEquals(btcParams.getGenesisBlock().getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        assertEquals(6, locator.size());
        assertEquals(blocks.get(9).getHash(), locator.get(0));
        assertEquals(blocks.get(8).getHash(), locator.get(1));
        assertEquals(blocks.get(7).getHash(), locator.get(2));
        assertEquals(blocks.get(5).getHash(), locator.get(3));
        assertEquals(blocks.get(1).getHash(), locator.get(4));
        assertEquals(btcParams.getGenesisBlock().getHash(), locator.get(5));
    }

    @Test
    void testGetBtcBlockchainBlockLocatorWithBtcCheckpoints() throws Exception {
        // arrange
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        List<BtcBlock> checkpoints = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .build();

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeRegTestConstants,
            provider,
            mock(BridgeEventLogger.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            track,
            null,
            new Context(btcParams),
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            btcBlockStoreFactory,
            mock(ActivationConfig.ForBlock.class),
            signatureCache
        ) {
            @Override
            InputStream getCheckPoints() {
                return getCheckpoints(btcParams, checkpoints);
            }
        };

        // act
        // Force instantiation of blockstore
        bridgeSupport.getBtcBlockchainBestChainHeight();

        // assert
        StoredBlock chainHead = bridgeSupport.getBtcBlockStore().getChainHead();
        assertEquals(10, chainHead.getHeight());
        assertEquals(checkpoints.get(9), chainHead.getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        assertEquals(1, locator.size());
        assertEquals(checkpoints.get(9).getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(btcParams, checkpoints.get(9), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        assertEquals(6, locator.size());
        assertEquals(blocks.get(9).getHash(), locator.get(0));
        assertEquals(blocks.get(8).getHash(), locator.get(1));
        assertEquals(blocks.get(7).getHash(), locator.get(2));
        assertEquals(blocks.get(5).getHash(), locator.get(3));
        assertEquals(blocks.get(1).getHash(), locator.get(4));
        assertEquals(checkpoints.get(9).getHash(), locator.get(5));
    }

    private List<BtcBlock> createBtcBlocks(NetworkParameters networkParameters, BtcBlock parent, int numberOfBlocksToCreate) {
        List<BtcBlock> list = new ArrayList<>();
        for (int i = 0; i < numberOfBlocksToCreate; i++) {
            BtcBlock block = new BtcBlock(
                networkParameters,
                2L,
                parent.getHash(),
                Sha256Hash.ZERO_HASH,
                parent.getTimeSeconds() + 1,
                parent.getDifficultyTarget(),
                0,
                new ArrayList<>()
            );
            block.solve();
            list.add(block);
            parent = block;
        }
        return list;
    }

    private InputStream getCheckpoints(NetworkParameters networkParameters, List<BtcBlock> checkpoints) {
        try {
            ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
            MessageDigest digest = Sha256Hash.newDigest();
            final DigestOutputStream digestOutputStream = new DigestOutputStream(baOutputStream, digest);
            digestOutputStream.on(false);
            final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
            StoredBlock storedBlock = new StoredBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock().getWork(), 0);
            try {
                dataOutputStream.writeBytes("CHECKPOINTS 1");
                dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
                digestOutputStream.on(true);
                dataOutputStream.writeInt(checkpoints.size());
                ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE_LEGACY);
                for (BtcBlock block : checkpoints) {
                    storedBlock = storedBlock.build(block);
                    storedBlock.serializeCompactLegacy(buffer);
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
    void callUpdateCollectionsGenerateEventLog() throws IOException {
        Repository track = createRepository().startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activationsBeforeForks)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withEventLogger(eventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigIntegers.asUnsignedByteArray(DUST_AMOUNT))
            .build();

        ECKey key = new ECKey();
        RskAddress sender = new RskAddress(key.getAddress());
        tx.sign(key.getPrivKeyBytes());

        bridgeSupport.updateCollections(tx);
        verify(eventLogger).logUpdateCollections(sender);
    }

    @Test
    void callUpdateCollectionsFundsEnoughForJustTheSmallerTx() throws IOException {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(new BridgeStorageAccessorImpl(track));

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(30, 0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(20, 0));
        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(10, 0));

        federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).add(new UTXO(
            BitcoinTestUtils.createHash(1),
            1,
            Coin.valueOf(12, 0),
            0,
            false,
            ScriptBuilder.createOutputScript(genesisFederation.getAddress())
        ));

        provider0.save();
        federationStorageProvider.save(btcParams, activationsBeforeForks);

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .withRskExecutionBlock(rskCurrentBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(providerForSupport)
            .withRepository(track)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration as the height doesn't affect it for releases
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        federationStorageProvider = createFederationStorageProvider(track);

        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
        // Check value sent to user is 10 BTC minus fee
        assertEquals(Coin.valueOf(999962800L), provider.getPegoutsWaitingForConfirmations().getEntries().iterator().next().getBtcTransaction().getOutput(0).getValue());
        // Check the wallet has been emptied
        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
    }

    @Test
    void callUpdateCollectionsThrowsCouldNotAdjustDownwards() throws IOException {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.valueOf(37500));

        federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).add(new UTXO(
            BitcoinTestUtils.createHash(1),
            1,
            Coin.valueOf(1000000),
            0,
            false,
            ScriptBuilder.createOutputScript(genesisFederation.getAddress())
        ));

        provider0.save();
        federationStorageProvider.save(btcParams, activationsBeforeForks);

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).setRequireUnclesValidation(false).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .withRskExecutionBlock(rskCurrentBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(providerForSupport)
            .withRepository(track)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration as it doesn't affect the release transactions
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        federationStorageProvider = createFederationStorageProvider(track);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        assertFalse(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
    }

    @Test
    void callUpdateCollectionsThrowsExceededMaxTransactionSize() throws IOException {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.COIN.multiply(7));
        for (int i = 0; i < 2000; i++) {
            federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).add(new UTXO(
                BitcoinTestUtils.createHash(1),
                1,
                Coin.CENT,
                0,
                false,
                ScriptBuilder.createOutputScript(genesisFederation.getAddress())
            ));
        }

        provider0.save();
        federationStorageProvider.save(btcParams, activationsBeforeForks);

        track.commit();

        track = repository.startTracking();

        BlockGenerator blockGenerator = new BlockGenerator();
        List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);
        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).setRequireUnclesValidation(false).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .withRskExecutionBlock(rskCurrentBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(providerForSupport)
            .withRepository(track)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // keeping same bridge storage configuration
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        federationStorageProvider = createFederationStorageProvider(track);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
        // Check the wallet has not been emptied
        assertFalse(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
    }

    @Test
    void minimumProcessFundsMigrationValue() throws IOException {
        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstants);
        BtcECKey key = new BtcECKey(new SecureRandom());
        FederationMember member = new FederationMember(key, new ECKey(), new ECKey());
        FederationArgs newFederationArgs = new FederationArgs(
            Collections.singletonList(member),
            Instant.EPOCH,
            5L,
            bridgeRegTestConstants.getBtcParams()
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(federationStorageProvider.getOldFederation(federationConstants, activationsBeforeForks)).thenReturn(oldFederation);
        when(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeForks)).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        ECKey senderKey = RskTestUtils.getEcKeyFromSeed("sender");
        tx.sign(senderKey.getPrivKeyBytes());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .withRskExecutionBlock(rskCurrentBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // One MICROCOIN is less than half the fee per kb, which is the minimum funds to migrate,
        // and so it won't be removed from the old federation UTXOs list for migration.
        List<UTXO> unsufficientUTXOsForMigration1 = new ArrayList<>();
        unsufficientUTXOsForMigration1.add(createUTXO(Coin.MICROCOIN, oldFederation.getAddress()));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(unsufficientUTXOsForMigration1);
        bridgeSupport.updateCollections(tx);
        assertThat(unsufficientUTXOsForMigration1.size(), is(1));

        // MILLICOIN is greater than half the fee per kb,
        // and it will be removed from the old federation UTXOs list for migration.
        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.MILLICOIN, oldFederation.getAddress()));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);
        assertThat(sufficientUTXOsForMigration1.size(), is(0));

        // 2 smaller coins should work exactly like 1 MILLICOIN
        List<UTXO> sufficientUTXOsForMigration2 = new ArrayList<>();
        sufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN.divide(2), oldFederation.getAddress()));
        sufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN.divide(2), oldFederation.getAddress()));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration2);

        bridgeSupport.updateCollections(tx);
        assertThat(sufficientUTXOsForMigration2.size(), is(0));

        // higher fee per kb prevents funds migration
        List<UTXO> unsufficientUTXOsForMigration2 = new ArrayList<>();
        unsufficientUTXOsForMigration2.add(createUTXO(Coin.MILLICOIN, oldFederation.getAddress()));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(unsufficientUTXOsForMigration2);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.COIN);

        bridgeSupport.updateCollections(tx);
        assertThat(unsufficientUTXOsForMigration2.size(), is(1));
    }

    @Test
    void callUpdateCollectionsChangeGetsOutOfDust() throws IOException {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        Map<byte[], BigInteger> preMineMap = new HashMap<>();
        preMineMap.put(PrecompiledContracts.BRIDGE_ADDR.getBytes(), LIMIT_MONETARY_BASE.asBigInteger());

        BlockGenerator blockGenerator = new BlockGenerator();
        Genesis genesisBlock = (Genesis) blockGenerator.getNewGenesisBlock(0, preMineMap);

        List<Block> blocks = blockGenerator.getSimpleBlockChain(genesisBlock, 10);

        BlockChainBuilder builder = new BlockChainBuilder();

        builder.setTesting(true).setRequireUnclesValidation(false).setGenesis(genesisBlock).build();

        for (Block block : blocks)
            builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);

        Repository repository = builder.getRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        provider0.getReleaseRequestQueue().add(new BtcECKey().toAddress(btcParams), Coin.COIN);

        UTXO utxo = new UTXO(
            BitcoinTestUtils.createHash(1),
            1,
            Coin.COIN.add(Coin.valueOf(100)),
            0,
            false,
            ScriptBuilder.createOutputScript(genesisFederation.getAddress())
        );
        federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).add(utxo);

        provider0.save();
        federationStorageProvider.save(btcParams, activationsBeforeForks);

        track.commit();

        track = repository.startTracking();
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        federationStorageProvider = createFederationStorageProvider(track);
        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .withRskExecutionBlock(rskCurrentBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(providerForSupport)
            .withRepository(track)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        bridgeSupport.updateCollections(tx);

        bridgeSupport.save();

        track.commit();

        // reusing same bridge storage configuration
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        federationStorageProvider = createFederationStorageProvider(track);

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
        assertEquals(LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600))), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2600)), repository.getBalance(BridgeSupport.BURN_ADDRESS));
        // Check the wallet has been emptied
        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
    }

    @Test
    void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException {
        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            // Fake wallet returned every time
            bridgeUtilsMocked.when(() -> BridgeUtils.getFederationSpendWallet(
                any(Context.class),
                any(Federation.class),
                any(List.class),
                anyBoolean(),
                any()
            )).thenReturn(mock(Wallet.class));

            Repository repository = createRepository();
            Repository track = repository.startTracking();

            BridgeStorageProvider provider0 = new BridgeStorageProvider(
                track,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeRegTestConstants.getBtcParams(),
                activationsBeforeForks
            );

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
            provider0.getPegoutsWaitingForConfirmations().add(tx1, 1L);
            provider0.getPegoutsWaitingForConfirmations().add(tx2, 1L);
            provider0.getPegoutsWaitingForConfirmations().add(tx3, 1L);

            provider0.save();

            track.commit();

            track = repository.startTracking();
            BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

            BlockGenerator blockGenerator = new BlockGenerator();
            List<Block> blocks = blockGenerator.getSimpleBlockChain(blockGenerator.getGenesisBlock(), 10);

            BlockChainBuilder builder = new BlockChainBuilder();
            builder.setTesting(true).setRequireUnclesValidation(false).build();

            for (Block block : blocks) {
                builder.getBlockStore().saveBlock(block, TEST_DIFFICULTY, true);
            }

            org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
            Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
            rskTx.sign(new ECKey().getPrivKeyBytes());

            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
            FederationSupport federationSupport = federationSupportBuilder
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activationsBeforeForks)
                .withRskExecutionBlock(rskCurrentBlock)
                .build();

            BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeRegTestConstants)
                .withProvider(provider)
                .withRepository(track)
                .withExecutionBlock(rskCurrentBlock)
                .withFederationSupport(federationSupport)
                .build();

            bridgeSupport.updateCollections(rskTx);
            bridgeSupport.save();

            track.commit();

            BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

            assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
            assertEquals(2, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
            assertEquals(1, provider2.getPegoutsWaitingForSignatures().size());
        }
    }

    @Test
    void sendOrphanBlockHeader() throws IOException, BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track,
            bridgeRegTestConstants, provider, activations);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(2),
            1,
            btcParams.getGenesisBlock().getDifficultyTarget(),
            1,
            new ArrayList<>()
        );
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        assertNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    void addBlockHeaderToBlockchain() throws IOException, BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Context btcContext = new Context(btcParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track,
            bridgeRegTestConstants, provider, activations);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);
        TestUtils.setInternalState(bridgeSupport, "btcBlockChain", btcBlockChain);

        Sha256Hash merkleRoot = BitcoinTestUtils.createHash(2);

        co.rsk.bitcoinj.core.BtcBlock prevBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1), // hash from its previous block
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        BigInteger prevBlockChainWork = new BigInteger("ffffffffffffffff", 16);
        StoredBlock prevStoredBlock = new StoredBlock(prevBlock, prevBlockChainWork, 1);
        // save previous block in storage, so we are able to build next block from it
        btcBlockStore.put(prevStoredBlock);
        track.save();

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            prevBlock.getHash(),
            BitcoinTestUtils.createHash(2),
            1,
            1,
            1,
            new ArrayList<>()
        );
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        assertNotNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    void releaseBtcWithDustOutput() throws AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .build();

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void releaseBtc() throws AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(AMOUNT)
            .build();

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .build();

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider2.getReleaseRequestQueue().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void releaseBtcFromContract() throws AddressFormatException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        org.ethereum.core.Transaction tx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            BigIntegers.asUnsignedByteArray(NONCE),
            DataWord.valueOf(BigIntegers.asUnsignedByteArray(GAS_PRICE)),
            DataWord.valueOf(BigIntegers.asUnsignedByteArray(GAS_LIMIT)),
            new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(new BtcECKey().getPrivKey()).getAddress()).getBytes(),
            Hex.decode(TO_ADDRESS),
            BigIntegers.asUnsignedByteArray(AMOUNT),
            Hex.decode(DATA),
            "",
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );

        track.saveCode(tx.getSender(), new byte[]{0x1});
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .build();

        try {
            bridgeSupport.releaseBtc(tx);
        } catch (Program.OutOfGasException e) {
            return;
        }
        Assertions.fail();
    }

    @Test
    void registerBtcTransactionOfAlreadyProcessedTransaction() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        provider.setHeightBtcTxhashAlreadyProcessed(tx.getHash(), 1L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, null);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionOfTransactionNotInMerkleTree() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(BitcoinTestUtils.createHash(5));

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 0, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionOfTransactionInMerkleTreeWithNegativeHeight() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), -1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionOfTransactionInMerkleTreeWithNotEnoughtHeight() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);

        assertTrue(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).isEmpty());
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionWithoutInputs() throws BlockStoreException {
        BtcTransaction noInputsTx = new BtcTransaction(btcParams);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(noInputsTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        int btcTxHeight = 2;

        BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);
        doReturn(btcParams).when(bridgeConstantsMock).getBtcParams();
        doReturn(0).when(bridgeConstantsMock).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsMock)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertThrows(VerificationException.EmptyInputsOrOutputs.class, () -> bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            noInputsTx.bitcoinSerialize(),
            btcTxHeight,
            pmt.bitcoinSerialize())
        );
    }

    @Test
    void registerBtcTransactionTxNotLockNorReleaseTx() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx = new BtcTransaction(btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(btcParams);
        tx.addOutput(Coin.COIN, address);
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        Context btcContext = new Context(btcParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track,
            bridgeRegTestConstants, provider, activations);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        btcBlockChain.add(block);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 1, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).size());

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionReleaseTx() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        Federation federation = FederationTestUtils.getGenesisFederation(federationConstants);
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Repository track = repository.startTracking();
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        BtcTransaction tx = new BtcTransaction(btcParams);
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
        BtcECKey.ECDSASignature sig0 = REGTEST_FEDERATION_PRIVATE_KEYS.get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, federation.getBtcPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = REGTEST_FEDERATION_PRIVATE_KEYS.get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, federation.getBtcPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            btcParams,
            activationsBeforeForks
        );
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        federationStorageProvider.setNewFederation(federation);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        federationStorageProvider = createFederationStorageProvider(track);
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).size());
        assertEquals(Coin.COIN, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).get(0).getValue());

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, btcParams, activationsBeforeForks);
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx.getHash()).isPresent());
    }

    @Test
    void registerBtcTransactionMigrationTx() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        NetworkParameters parameters = bridgeRegTestConstants.getBtcParams();

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        List<FederationMember> activeFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys);
        FederationArgs activeFedArgs = new FederationArgs(activeFederationMembers,
            Instant.ofEpochMilli(2000L),
            2L,
            parameters
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs
        );

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        List<FederationMember> retiringFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys);
        FederationArgs retiringFedArgs = new FederationArgs(retiringFederationMembers,
            Instant.ofEpochMilli(1000L),
            1L,
            parameters
        );
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(retiringFedArgs);

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

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        federationStorageProvider.setNewFederation(activeFederation);
        federationStorageProvider.setOldFederation(retiringFederation);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), 30, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        List<UTXO> activeFederationBtcUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activations);
        List<Coin> activeFederationBtcCoins = activeFederationBtcUTXOs.stream().map(UTXO::getValue).collect(Collectors.toList());
        assertThat(activeFederationBtcUTXOs, hasSize(1));
        assertThat(activeFederationBtcCoins, hasItem(Coin.COIN));
    }

    @Test
    void registerBtcTransactionWithCrossFederationsChange() throws Exception {
        NetworkParameters params = bridgeRegTestConstants.getBtcParams();

        List<BtcECKey> activeFederationKeys = Stream.of("fa01", "fa02")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());

        List<FederationMember> activeFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys);
        FederationArgs activeFedArgs = new FederationArgs(
            activeFederationMembers,
            Instant.ofEpochMilli(1000L),
            5L,
            params
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs
        );

        final List<BtcECKey> retiringFedPrivateKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
            BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
            BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
        );

        Federation retiringFederation = createFederation(bridgeRegTestConstants, retiringFedPrivateKeys);

        List<UTXO> activeFederationUtxos = new ArrayList<>();
        List<UTXO> retiringFederationUtxos = new ArrayList<>();

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);

        when(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeForks)).thenReturn(activeFederation);
        when(federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks)).thenReturn(activeFederationUtxos);

        when(federationStorageProvider.getOldFederation(federationConstants, activationsBeforeForks)).thenReturn(retiringFederation);
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(retiringFederationUtxos);

        Address retiringFederationAddress = retiringFederation.getAddress();

        // Peg-out from retiring federation with a change output
        BtcTransaction releaseWithChangeTx = new BtcTransaction(params);

        releaseWithChangeTx.addInput(Sha256Hash.ZERO_HASH, 0, retiringFederation.getRedeemScript());

        Address randomAddress = new Address(params, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        releaseWithChangeTx.addOutput(Coin.COIN, randomAddress);

        Coin changeValue = Coin.COIN.plus(Coin.COIN);
        releaseWithChangeTx.addOutput(changeValue, retiringFederationAddress);
        FederationTestUtils.addSignatures(retiringFederation, retiringFedPrivateKeys, releaseWithChangeTx);

        PartialMerkleTree partialMerkleTree = PartialMerkleTree.buildFromLeaves(params, new byte[]{(byte) 0xff}, Collections.singletonList(releaseWithChangeTx.getHash()));

        BtcBlock mockBtcBlock = mock(BtcBlock.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockBtcBlock.getMerkleRoot()).thenReturn(partialMerkleTree.getTxnHashAndMerkleRoot(new LinkedList<>()));

        BtcBlockStoreWithCache mockBtcBlockStore = mock(BtcBlockStoreWithCache.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockBtcBlockStore.getChainHead().getHeight()).thenReturn(14);

        StoredBlock mockStoredBlock = mock(StoredBlock.class);
        when(mockStoredBlock.getHeader()).thenReturn(mockBtcBlock);

        when(mockBtcBlockStore.getStoredBlockAtMainChainHeight(anyInt())).thenReturn(mockStoredBlock);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(mockBtcBlockStore);

        BlockGenerator blockGenerator = new BlockGenerator();
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(185, 1);

        Transaction rskTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activationsBeforeForks)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(rskCurrentBlock)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(rskTx, releaseWithChangeTx.bitcoinSerialize(), 1, partialMerkleTree.bitcoinSerialize());

        // Assert no utxo is registered to active fed
        assertThat(activeFederationUtxos, hasSize(0));
        // Assert utxo is registered to retiring fed
        assertThat(retiringFederationUtxos, hasSize(1));
        // assert utxo registered is the expected one
        UTXO changeUtxo = retiringFederationUtxos.get(0);
        assertThat(changeUtxo.getValue(), is(changeValue));
        assertThat(changeUtxo.getScript().getToAddress(params), is(retiringFederationAddress));
    }

    @Test
    void registerBtcTransactionLockTxWhitelisted() throws Exception {
        List<BtcECKey> federation1Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<FederationMember> federation1Members = FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys);
        FederationArgs federation1Args = new FederationArgs(federation1Members,
            Instant.ofEpochMilli(1000L),
            0L,
            btcParams
        );
        Federation federation1 = FederationFactory.buildStandardMultiSigFederation(
            federation1Args
        );

        List<BtcECKey> federation2Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        );
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<FederationMember> federation2Members = FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys);
        FederationArgs federation2Args = new FederationArgs(federation2Members,
            Instant.ofEpochMilli(2000L),
            0L,
            btcParams);
        Federation federation2 = FederationFactory.buildStandardMultiSigFederation(
            federation2Args
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = Mockito.mock(Block.class);
        Mockito.when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.COIN.multiply(2), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(3), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        federationStorageProvider.setNewFederation(federation1);
        federationStorageProvider.setOldFederation(federation2);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            BRIDGE_ADDRESS,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        // Whitelist the addresses
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, btcParams);
        Address address1 = srcKey1.toAddress(btcParams);
        Address address2 = srcKey2.toAddress(btcParams);
        Address address3 = srcKey3.toAddress(btcParams);
        whitelist.put(address1, new OneOffWhiteListEntry(address1, Coin.COIN.multiply(5)));
        whitelist.put(address2, new OneOffWhiteListEntry(address2, Coin.COIN.multiply(10)));
        whitelist.put(address3, new OneOffWhiteListEntry(address3, Coin.COIN.multiply(2).add(Coin.COIN.multiply(3))));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();
        MatcherAssert.assertThat(whitelist.isWhitelisted(address1), is(false));
        MatcherAssert.assertThat(whitelist.isWhitelisted(address2), is(false));
        MatcherAssert.assertThat(whitelist.isWhitelisted(address3), is(false));

        co.rsk.core.Coin amountToHaveBeenCreditedToSrc1 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));
        co.rsk.core.Coin amountToHaveBeenCreditedToSrc2 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(10, 0));
        co.rsk.core.Coin amountToHaveBeenCreditedToSrc3 = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = amountToHaveBeenCreditedToSrc1
            .add(amountToHaveBeenCreditedToSrc2)
            .add(amountToHaveBeenCreditedToSrc3);
        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        assertEquals(amountToHaveBeenCreditedToSrc1, repository.getBalance(srcKey1RskAddress));
        assertEquals(amountToHaveBeenCreditedToSrc2, repository.getBalance(srcKey2RskAddress));
        assertEquals(amountToHaveBeenCreditedToSrc3, repository.getBalance(srcKey3RskAddress));
        assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        federationStorageProvider = createFederationStorageProvider(track);
        assertEquals(2, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).size());
        assertEquals(2, federationStorageProvider.getOldFederationBtcUTXOs().size());
        assertEquals(Coin.COIN.multiply(5), federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).get(0).getValue());
        assertEquals(Coin.COIN.multiply(2), federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).get(1).getValue());
        assertEquals(Coin.COIN.multiply(10), federationStorageProvider.getOldFederationBtcUTXOs().get(0).getValue());
        assertEquals(Coin.COIN.multiply(3), federationStorageProvider.getOldFederationBtcUTXOs().get(1).getValue());

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeRegTestConstants.getBtcParams(), activationsBeforeForks);
        assertEquals(0, provider2.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider2.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider2.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
        assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());
        assertTrue(provider2.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());
    }

    @Test
    void isBtcTxHashAlreadyProcessed() throws IOException {
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(getBridgeStorageProviderMockWithProcessedHashes())
            .withRepository(null)
            .build();

        for (int i = 0; i < 10; i++) {
            assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes())));
        }
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(Sha256Hash.of("anything".getBytes())));
    }

    @Test
    void getBtcTxHashProcessedHeight() throws IOException {
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(getBridgeStorageProviderMockWithProcessedHashes())
            .withRepository(null)
            .build();

        for (int i = 0; i < 10; i++) {
            assertEquals(i, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of(("hash_" + i).getBytes())).longValue());
        }
        assertEquals(-1L, bridgeSupport.getBtcTxHashProcessedHeight(Sha256Hash.of("anything".getBytes())).longValue());
    }

    @Test
    void getFederationMethods_genesis() {
        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs
        );
        FederationArgs genesisFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithKeys(
            Stream.iterate(1, i -> i + 1)
                .limit(6)
                .map(i -> BtcECKey.fromPrivate(BigInteger.valueOf((i) * 100)))
                .collect(Collectors.toList())),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = FederationFactory.buildStandardMultiSigFederation(
            genesisFedArgs
        );
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(true, activeFederation, genesisFederation, null, null, null, null);

        assertEquals(6, bridgeSupport.getActiveFederationSize().intValue());
        assertEquals(4, bridgeSupport.getActiveFederationThreshold().intValue());
        assertEquals(genesisFederation.getAddress().toString(), bridgeSupport.getActiveFederationAddress().toString());

        List<FederationMember> members = genesisFederation.getMembers();
        for (int i = 0; i < 6; i++) {
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getActiveFederatorBtcPublicKey(i)
            );
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)
            );
            assertArrayEquals(
                members.get(i).getRskPublicKey().getPubKey(true),
                bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)
            );
            assertArrayEquals(
                members.get(i).getMstPublicKey().getPubKey(true),
                bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)
            );
        }
    }

    @Test
    void getFederationMethods_active() {
        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs
        );

        FederationArgs genesisFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(6),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation genesisFederation = FederationFactory.buildStandardMultiSigFederation(
            genesisFedArgs
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

        assertEquals(3, bridgeSupport.getActiveFederationSize().intValue());
        assertEquals(2, bridgeSupport.getActiveFederationThreshold().intValue());
        assertEquals(activeFederation.getAddress().toString(), bridgeSupport.getActiveFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(3);
        for (int i = 0; i < 3; i++) {
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorBtcPublicKey(i));
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC));
            Assertions.assertArrayEquals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK));
            Assertions.assertArrayEquals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.MST));
        }
    }

    @Test
    void getFederationMethods_newActivated() {
        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            15L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        FederationArgs oldFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(6),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation oldFederation = FederationFactory.buildStandardMultiSigFederation(
            oldFedArgs
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

        assertEquals(3, bridgeSupport.getActiveFederationSize().intValue());
        assertEquals(2, bridgeSupport.getActiveFederationThreshold().intValue());
        assertEquals(newFederation.getAddress().toString(), bridgeSupport.getActiveFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(3);
        for (int i = 0; i < 3; i++) {
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorBtcPublicKey(i));
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC));
            Assertions.assertArrayEquals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK));
            Assertions.assertArrayEquals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.MST));
        }
    }

    @Test
    void getFederationMethods_newNotActivated() {
        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            15L,
            btcParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        FederationArgs oldFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(6),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation oldFederation = FederationFactory.buildStandardMultiSigFederation(
            oldFedArgs
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

        assertEquals(6, bridgeSupport.getActiveFederationSize().intValue());
        assertEquals(4, bridgeSupport.getActiveFederationThreshold().intValue());
        assertEquals(oldFederation.getAddress().toString(), bridgeSupport.getActiveFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(6);
        for (int i = 0; i < 6; i++) {
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorBtcPublicKey(i));
            Assertions.assertArrayEquals(members.get(i).getBtcPublicKey().getPubKey(), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC));
            Assertions.assertArrayEquals(members.get(i).getRskPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK));
            Assertions.assertArrayEquals(members.get(i).getMstPublicKey().getPubKey(true), bridgeSupport.getActiveFederatorPublicKeyOfType(i, FederationMember.KeyType.MST));

        }
    }

    @Test
    void getRetiringFederationMethods_none() {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        assertNull(bridgeSupport.getRetiringFederatorBtcPublicKey(0));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    void getRetiringFederationMethods_presentNewInactive() {
        FederationArgs mockedNewFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(2),
            Instant.ofEpochMilli(1000),
            10L,
            btcParams
        );
        Federation mockedNewFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedNewFedArgs
        );

        FederationArgs mockedOldFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(4),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation mockedOldFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedOldFedArgs
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

        assertEquals(-1, bridgeSupport.getRetiringFederationSize().intValue());
        assertEquals(-1, bridgeSupport.getRetiringFederationThreshold().intValue());
        assertNull(bridgeSupport.getRetiringFederatorBtcPublicKey(0));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        assertNull(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    void getRetiringFederationMethods_presentNewActive() {
        FederationArgs mockedNewFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(2),
            Instant.ofEpochMilli(1000),
            10L,
            btcParams
        );
        Federation mockedNewFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedNewFedArgs
        );

        FederationArgs mockedOldFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(4),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation mockedOldFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedOldFedArgs
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

        assertEquals(4, bridgeSupport.getRetiringFederationSize().intValue());
        assertEquals(3, bridgeSupport.getRetiringFederationThreshold().intValue());
        assertEquals(1000, bridgeSupport.getRetiringFederationCreationTime().toEpochMilli());
        assertEquals(mockedOldFederation.getAddress().toString(), bridgeSupport.getRetiringFederationAddress().toString());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(4);
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getRetiringFederatorBtcPublicKey(i)
                );
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getRetiringFederatorPublicKeyOfType(i, KeyType.BTC)
                );
            assertArrayEquals(
                members.get(i).getRskPublicKey().getPubKey(true),
                bridgeSupport.getRetiringFederatorPublicKeyOfType(i, KeyType.RSK)
);
            assertArrayEquals(
                members.get(i).getMstPublicKey().getPubKey(true),
                bridgeSupport.getRetiringFederatorPublicKeyOfType(i, KeyType.MST)
            );
        }
    }

    @Test
    void getPendingFederationMethods_none() {
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, null, null, null);

        assertEquals(-1, bridgeSupport.getPendingFederationSize().intValue());
        assertNull(bridgeSupport.getPendingFederatorBtcPublicKey(0));
        assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC));
        assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK));
        assertNull(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST));
    }

    @Test
    void getPendingFederationMethods_present() {
        PendingFederation mockedPendingFederation = new PendingFederation(FederationTestUtils.getFederationMembers(5));
        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(false, null, null, null, mockedPendingFederation, null, null);

        assertEquals(5, bridgeSupport.getPendingFederationSize().intValue());
        List<FederationMember> members = FederationTestUtils.getFederationMembers(5);
        for (int i = 0; i < 5; i++) {
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getPendingFederatorBtcPublicKey(i)
            );
            assertArrayEquals(
                members.get(i).getBtcPublicKey().getPubKey(),
                bridgeSupport.getPendingFederatorPublicKeyOfType(i, KeyType.BTC)
            );
            assertArrayEquals(
                members.get(i).getRskPublicKey().getPubKey(true),
                bridgeSupport.getPendingFederatorPublicKeyOfType(i, KeyType.RSK)
            );
            assertArrayEquals(
                members.get(i).getMstPublicKey().getPubKey(true),
                bridgeSupport.getPendingFederatorPublicKeyOfType(i, KeyType.MST)
            );
        }
    }

    @Test
    void voteFederationChange_methodNotAllowed() {
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
        assertEquals(
            FederationChangeResponseCode.GENERIC_ERROR.getCode(),
            bridgeSupport.voteFederationChange(mock(Transaction.class), spec)
        );
    }

    @Test
    void voteFederationChange_notAuthorized() {
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
        when(mockedTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(12L)).getAddress()));
        assertEquals(
            FederationChangeResponseCode.GENERIC_ERROR.getCode(),
            bridgeSupport.voteFederationChange(mockedTx, spec)
        );
    }

    private static class VotingMocksProvider {
        private final RskAddress voter;
        private final ABICallElection election;
        private final ABICallSpec spec;
        private final Transaction tx;
        private ABICallSpec winner;

        public VotingMocksProvider(String function, byte[][] arguments, boolean mockVoteResult) {
            byte[] voterBytes = ECKey.fromPublicOnly(Hex.decode(
                // Public key hex of an authorized voter in regtest, taken from BridgeRegTestConstants
                "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991"
            )).getAddress();
            voter = new RskAddress(voterBytes);

            tx = mock(Transaction.class);
            when(tx.getSender(any(SignatureCache.class))).thenReturn(voter);

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

        public Optional<ABICallSpec> getWinner() {
            if (winner == null) {
                return Optional.empty();
            }

            return Optional.of(winner);
        }

        public void setWinner(ABICallSpec winner) {
            this.winner = winner;
        }

        public int execute(BridgeSupport bridgeSupport) {
            return bridgeSupport.voteFederationChange(tx, spec);
        }
    }

    @Test
    void createFederation_ok() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        // Vote with no winner
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        Assertions.assertArrayEquals(new PendingFederation(Collections.emptyList()).getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    void createFederation_pendingExists() {
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

        Assertions.assertArrayEquals(new PendingFederation(Collections.emptyList()).getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        assertEquals(-1, mocksProvider.execute(bridgeSupport));
        Assertions.assertArrayEquals(new PendingFederation(Collections.emptyList()).getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void createFederation_withPendingActivation() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider(FederationChangeFunction.CREATE.getKey(), new byte[][]{}, false);

        FederationArgs mockedNewFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(2),
            Instant.ofEpochMilli(2000),
            10L,
            btcParams
        );
        Federation mockedNewFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedNewFedArgs
        );

        FederationArgs mockedOldFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(4),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation mockedOldFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedOldFedArgs
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

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(mockedNewFederation);
        when(federationStorageProvider.getOldFederation(any(), any())).thenReturn(mockedOldFederation);
        when(federationStorageProvider.getFederationElection(any())).thenReturn(mocksProvider.getElection());

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void createFederation_withExistingRetiringFederation() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider(FederationChangeFunction.CREATE.getKey(), new byte[][]{}, false);

        FederationArgs mockedNewFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(2),
            Instant.ofEpochMilli(2000),
            10L,
            btcParams
        );
        Federation mockedNewFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedNewFedArgs
        );

        FederationArgs mockedOldFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(4),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation mockedOldFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedOldFedArgs
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

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(mockedNewFederation);
        when(federationStorageProvider.getOldFederation(any(), any())).thenReturn(mockedOldFederation);
        when(federationStorageProvider.getFederationElection(any())).thenReturn(mocksProvider.getElection());

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-3, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_okNoKeys() {
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

        assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorBtcPublicKey(0)
            );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST)
        );
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    void addFederatorPublicKey_okKeys() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Collections.singletonList(
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(2, bridgeSupport.getPendingFederationSize().intValue());
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorBtcPublicKey(0)
            );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST)
        );

        assertArrayEquals(
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            bridgeSupport.getPendingFederatorBtcPublicKey(1)
        );
        assertArrayEquals(
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.MST)
        );

        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    void addFederatorPublicKey_noPendingFederation() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_keyExists() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Collections.singletonList(
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_invalidKey() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKeyMultikey_okNoKeys() {
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

        assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(0, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertArrayEquals(
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("026289413837ab836eb76428406a3b4f200418d31d99c259a0532b8e435f35153b"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("03e12efa1146037bc9325574b0f15749ba6dc0eec360b1670b05029eead511a6ff"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST)
        );
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    void addFederatorPublicKeyMultikey_okKeys() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            Hex.decode("03f64d2c022bca70f3ff0b1e95336be2c1507daa2ad37a484e0b66cbda86cfc6c5"),
            Hex.decode("03eed62698319f754407a31fde9a51da8b2be0ab40e9c4c695bb057757729be37f")
        }, true);

        PendingFederation pendingFederation = new PendingFederation(Collections.singletonList(new FederationMember(
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        // Vote with no winner
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertEquals(2, bridgeSupport.getPendingFederationSize().intValue());
        assertArrayEquals(
            Hex.decode("02ebd9e8b2caff48b10e661e69fe107d6986d2df1ce7e377f2ef927f3194a61b99"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("02a23343f50363dc9a4c29f0c0a8386780cc8bf469211f4de51d50f8c0f274e9a7"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("030dd584c286275ab2ce249096d0d7e6c78853e0902db061b14f2e39df068f95bc"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(0, KeyType.MST)
        );

        assertArrayEquals(
            Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.BTC)
        );
        assertArrayEquals(
            Hex.decode("03f64d2c022bca70f3ff0b1e95336be2c1507daa2ad37a484e0b66cbda86cfc6c5"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.RSK)
        );
        assertArrayEquals(
            Hex.decode("03eed62698319f754407a31fde9a51da8b2be0ab40e9c4c695bb057757729be37f"),
            bridgeSupport.getPendingFederatorPublicKeyOfType(1, KeyType.MST)
        );

        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
    }

    @Test
    void addFederatorPublicKeyMultikey_noPendingFederation() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKeyMultikey_btcKeyExists() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Collections.singletonList(new FederationMember(
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKeyMultikey_rskKeyExists() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Collections.singletonList(new FederationMember(
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKeyMultikey_mstKeyExists() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("add-multi", new byte[][]{
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"),
            Hex.decode("02cf0dec68ca34502e4ebd35c40a0e66ff47ba520f0418bcd7388717b12ab4b053"),
            Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")
        }, false);

        PendingFederation pendingFederation = new PendingFederation(Collections.singletonList(new FederationMember(
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

        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        assertEquals(1, bridgeSupport.getPendingFederationSize().intValue());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_invalidBtcKey() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_invalidRskKey() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void addFederatorPublicKey_invalidMstKey() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void rollbackFederation_ok() {
        VotingMocksProvider mocksProvider = new VotingMocksProvider("rollback", new byte[][]{}, true);

        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
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

        // Vote with no winner
        assertNotNull(bridgeSupport.getPendingFederationHash());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertNotNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();
    }

    @Test
    void rollbackFederation_noPendingFederation() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void commitFederation_ok() {
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

        List<FederationMember> expectedFederationMembers =
            FederationTestUtils.getFederationMembersWithKeys(
                Arrays.asList(
                    BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                    BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                    BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                    BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))
                )
            );
        FederationArgs expectedFedArgs = new FederationArgs(expectedFederationMembers,
            Instant.ofEpochMilli(15005L),
            15L,
            btcParams
        );
        Federation expectedFederation = FederationFactory.buildStandardMultiSigFederation(
            expectedFedArgs
        );

        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
            BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
            BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        ));
        FederationArgs newFederationArgs = new FederationArgs(newFederationMembers,
            Instant.ofEpochMilli(5005L),
            0L,
            btcParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        BridgeEventLogger eventLoggerMock = mock(BridgeEventLogger.class);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(newFederation);
        when(federationStorageProvider.getPendingFederation()).thenReturn(pendingFederation);
        when(federationStorageProvider.getFederationElection(any())).thenReturn(mocksProvider.getElection());

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
            federationStorageProvider,
            newFederation,
            pendingFederation,
            mocksProvider.getElection(),
            executionBlock,
            eventLoggerMock
        );

        // Mock some utxos in the currently active federation
        for (int i = 0; i < 5; i++) {
            UTXO utxoMock = mock(UTXO.class);
            when(utxoMock.getIndex()).thenReturn((long) i);
            when(utxoMock.getValue()).thenReturn(Coin.valueOf((i + 1) * 1000));
            federationStorageProvider.getNewFederationBtcUTXOs(any(), any()).add(utxoMock);
        }

        // Currently active federation
        Federation oldActiveFederation = federationStorageProvider.getNewFederation(federationConstants, activationsBeforeForks);
        assertNotNull(oldActiveFederation);

        // Vote with no winner
        assertNotNull(federationStorageProvider.getPendingFederation());
        assertEquals(1, mocksProvider.execute(bridgeSupport));
        assertNotNull(federationStorageProvider.getPendingFederation());

        assertEquals(oldActiveFederation, federationStorageProvider.getNewFederation(federationConstants, activationsBeforeForks));
        assertNull(federationStorageProvider.getOldFederation(federationConstants, activationsBeforeForks));

        // Vote with winner
        mocksProvider.setWinner(mocksProvider.getSpec());
        assertEquals(1, mocksProvider.execute(bridgeSupport));

        assertNull(federationStorageProvider.getPendingFederation());

        Federation retiringFederation = federationStorageProvider.getOldFederation(federationConstants, activationsBeforeForks);
        Federation activeFederation = federationStorageProvider.getNewFederation(federationConstants, activationsBeforeForks);

        assertEquals(expectedFederation, activeFederation);
        assertEquals(retiringFederation, oldActiveFederation);

        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks).size());
        assertEquals(5, federationStorageProvider.getOldFederationBtcUTXOs().size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, federationStorageProvider.getOldFederationBtcUTXOs().get(i).getIndex());
            assertEquals(Coin.valueOf((i + 1) * 1000), federationStorageProvider.getOldFederationBtcUTXOs().get(i).getValue());
        }
        verify(mocksProvider.getElection(), times(1)).clearWinners();
        verify(mocksProvider.getElection(), times(1)).clear();

        // Check logs are made
        verify(eventLoggerMock, times(1)).logCommitFederation(executionBlock, newFederation, expectedFederation);
    }

    @Test
    void commitFederation_noPendingFederation() {
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

        assertNull(bridgeSupport.getPendingFederationHash());
        assertEquals(-1, mocksProvider.execute(bridgeSupport));
        assertNull(bridgeSupport.getPendingFederationHash());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void commitFederation_incompleteFederation() {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Collections.singletonList(
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        )));

        VotingMocksProvider mocksProvider = new VotingMocksProvider(FederationChangeFunction.COMMIT.getKey(), new byte[][]{
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

        Assertions.assertArrayEquals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        assertEquals(-2, mocksProvider.execute(bridgeSupport));
        Assertions.assertArrayEquals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void commitFederation_hashMismatch() {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersWithKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
            BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12"))
        )));

        VotingMocksProvider mocksProvider = new VotingMocksProvider(
            FederationChangeFunction.COMMIT.getKey(),
            new byte[][]{new Keccak256(HashUtil.keccak256(Hex.decode("aabbcc"))).getBytes()},
            true
        );

        BridgeSupport bridgeSupport = getBridgeSupportWithMocksForFederationTests(
            false,
            null,
            null,
            null,
            pendingFederation,
            mocksProvider.getElection(),
            null
        );

        assertArrayEquals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        assertEquals(-3, mocksProvider.execute(bridgeSupport));
        assertArrayEquals(pendingFederation.getHash().getBytes(), bridgeSupport.getPendingFederationHash().getBytes());
        verify(mocksProvider.getElection(), never()).clearWinners();
        verify(mocksProvider.getElection(), never()).clear();
        verify(mocksProvider.getElection(), never()).vote(mocksProvider.getSpec(), mocksProvider.getVoter());
    }

    @Test
    void getActiveFederationWallet() {
        List<FederationMember> expectedFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        ));
        FederationArgs expectedFederationArgs = new FederationArgs(expectedFederationMembers,
            Instant.ofEpochMilli(5005L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation expectedFederation = FederationFactory.buildStandardMultiSigFederation(
            expectedFederationArgs
        );

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
        TestUtils.setInternalState(bridgeSupport, "btcContext", expectedContext);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(expectedFederation);
        Object expectedUtxos = federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activationsBeforeForks);

        final Wallet expectedWallet = mock(Wallet.class);
        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.getFederationSpendWallet(any(), any(), any(), anyBoolean(), any())).then((InvocationOnMock m) -> {
                assertEquals(m.<Context>getArgument(0), expectedContext);
                assertEquals(m.<Federation>getArgument(1), expectedFederation);
                assertEquals(m.getArgument(2), expectedUtxos);
                return expectedWallet;
            });

            assertSame(expectedWallet, bridgeSupport.getActiveFederationWallet(false));
        }
    }

    @Test
    void getRetiringFederationWallet_nonEmpty() {
        FederationArgs mockedFedArgs = new FederationArgs(FederationTestUtils.getFederationMembers(2),
            Instant.ofEpochMilli(2000),
            10L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation mockedNewFederation = FederationFactory.buildStandardMultiSigFederation(
            mockedFedArgs
        );

        List<FederationMember> expectedFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        ));
        FederationArgs expectedFederationArgs = new FederationArgs(
            expectedFederationMembers,
            Instant.ofEpochMilli(5005L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Federation expectedFederation = FederationFactory.buildStandardMultiSigFederation(
            expectedFederationArgs
        );

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
        TestUtils.setInternalState(bridgeSupport, "btcContext", expectedContext);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(mockedNewFederation);
        when(federationStorageProvider.getOldFederation(any(), any())).thenReturn(expectedFederation);
        Object expectedUtxos = federationStorageProvider.getOldFederationBtcUTXOs();

        final Wallet expectedWallet = mock(Wallet.class);
        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.getFederationSpendWallet(any(), any(), any(), anyBoolean(), any())).then((InvocationOnMock m) -> {
                assertEquals(m.<Context>getArgument(0), expectedContext);
                assertEquals(m.<Federation>getArgument(1), expectedFederation);
                assertEquals(m.getArgument(2), expectedUtxos);
                return expectedWallet;
            });

            Assertions.assertSame(expectedWallet, bridgeSupport.getRetiringFederationWallet(false));
        }
    }

    @Test
    void getBtcBlockchainInitialBlockHeight() throws IOException {
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(null)
            .withRepository(repository)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        // As we don't have any checkpoint the genesis block at height 0 should be used and returned
        assertEquals(0, bridgeSupport.getBtcBlockchainInitialBlockHeight());
    }

    @Test
    void getBtcTransactionConfirmations_inexistentBlockHash() throws BlockStoreException, IOException {
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
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INEXISTENT_BLOCK_HASH_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_blockNotInBestChain() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        int height = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(null);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_blockNotInBestChainBlockWithHeightNotFound() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_blockTooOld() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        final int BLOCK_HEIGHT = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), BLOCK_HEIGHT);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), BLOCK_HEIGHT + 4320 + 1);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();
        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_BLOCK_TOO_OLD_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_heightInconsistency() throws BlockStoreException, IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BtcBlock blockHeader = mock(BtcBlock.class);
        when(blockHeader.getHash()).thenReturn(blockHash);

        int height = 50;
        StoredBlock block = new StoredBlock(blockHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenThrow(new BlockStoreException("blah"));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();
        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, null);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_merkleBranchDoesNotProve() throws BlockStoreException, IOException {
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
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(blockHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);
        Sha256Hash btcTransactionHash = Sha256Hash.of(Hex.decode("112233"));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();
        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(btcTransactionHash)).thenReturn(Sha256Hash.ZERO_HASH);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(btcTransactionHash, blockHash, merkleBranch);

        assertEquals(BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INVALID_MERKLE_BRANCH_ERROR_CODE.intValue(), confirmations);
    }

    @Test
    void getBtcTransactionConfirmationsGetCost_ok() throws BlockStoreException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));
        StoredBlock block = new StoredBlock(null, new BigInteger("0"), 50);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(blockHash)).thenReturn(block);

        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();

        StoredBlock chainHead = new StoredBlock(btcBlock, new BigInteger("0"), 60);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport =
            bridgeSupportBuilder
                .withBridgeConstants(bridgeRegTestConstants)
                .withProvider(provider)
                .withRepository(track)
                .withBtcBlockStoreFactory(mockFactory)
                .withActivations(activations)
                .build();

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        args[3] = new Object[]{};
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        assertEquals(27_000 + 10 * 315, cost);
    }

    @Test
    void getBtcTransactionConfirmationsGetCost_blockDoesNotExist() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        assertEquals(27_000, cost);
    }

    @Test
    void getBtcTransactionConfirmationsGetCost_getBestChainHeightError() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        assertEquals(27_000, cost);
    }

    @Test
    void getBtcTransactionConfirmationsGetCost_blockTooDeep() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Sha256Hash blockHash = Sha256Hash.of(Hex.decode("aabbcc"));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        Object[] args = new Object[4];
        args[1] = blockHash.getBytes();
        long cost = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

        assertEquals(27_000, cost);
    }

    @Test
    void getBtcBlockchainBlockHashAtDepth() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeRegTestConstants.getBtcParams(),
            activationsBeforeForks
        );

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeRegTestConstants.getBtcParams());
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(track,
            bridgeRegTestConstants, provider, activations);
        BtcBlockStoreWithCache.Factory mockFactory = mock(RepositoryBtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track, bridgeRegTestConstants, provider, activations)).thenReturn(btcBlockStore);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withRepository(track)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        StoredBlock chainHead = btcBlockStore.getChainHead();
        assertEquals(0, chainHead.getHeight());
        assertEquals(btcParams.getGenesisBlock(), chainHead.getHeader());

        assertEquals(btcParams.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(0));

        assertThrows(IndexOutOfBoundsException.class, () -> bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bridgeSupport.getBtcBlockchainBlockHashAtDepth(1));

        List<BtcBlock> blocks = createBtcBlocks(btcParams, btcParams.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));

        assertEquals(btcParams.getGenesisBlock().getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(10));
        assertThrows(IndexOutOfBoundsException.class, () -> bridgeSupport.getBtcBlockchainBlockHashAtDepth(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bridgeSupport.getBtcBlockchainBlockHashAtDepth(11));

        for (int i = 0; i < 10; i++) {
            assertEquals(blocks.get(i).getHash(), bridgeSupport.getBtcBlockchainBlockHashAtDepth(9 - i));
        }
    }

    private BridgeStorageProvider getBridgeStorageProviderMockWithProcessedHashes() throws IOException {
        BridgeStorageProvider providerMock = mock(BridgeStorageProvider.class);

        for (int i = 0; i < 10; i++) {
            when(providerMock.getHeightIfBtcTxhashIsAlreadyProcessed(Sha256Hash.of(("hash_" + i).getBytes()))).thenReturn(
                Optional.of((long) i)
            );
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
        Block executionBlock) {

        BridgeConstants constantsMock = mock(BridgeConstants.class);
        FederationConstants federationConstantsMock = mock(FederationConstants.class);

        if (mockedGenesisFederation != null) {
            when(federationConstantsMock.getGenesisFederationCreationTime()).thenReturn(mockedGenesisFederation.getCreationTime());
            when(federationConstantsMock.getGenesisFederationPublicKeys()).thenReturn(mockedGenesisFederation.getBtcPublicKeys());
        }

        when(federationConstantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(federationConstantsMock.getFederationChangeAuthorizer()).thenReturn(federationConstants.getFederationChangeAuthorizer());

        long federationActivationAge = federationConstants.getFederationActivationAge(activations);
        when(federationConstantsMock.getFederationActivationAge(any())).thenReturn(federationActivationAge);

        class FederationHolder {
            private PendingFederation pendingFederation;
            private Federation activeFederation;
            private Federation retiringFederation;
            private ABICallElection federationElection;

            public final List<UTXO> retiringUTXOs = new ArrayList<>();
            public final List<UTXO> activeUTXOs = new ArrayList<>();

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
        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);

        when(federationStorageProvider.getOldFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.retiringUTXOs);
        when(federationStorageProvider.getNewFederationBtcUTXOs(any(), any())).then((InvocationOnMock m) -> holder.activeUTXOs);

        holder.setActiveFederation(genesis ? null : mockedNewFederation);
        holder.setRetiringFederation(mockedOldFederation);
        when(federationStorageProvider.getNewFederation(any(), any())).then((InvocationOnMock m) -> holder.getActiveFederation());
        when(federationStorageProvider.getOldFederation(any(), any())).then((InvocationOnMock m) -> holder.getRetiringFederation());
        when(federationStorageProvider.getPendingFederation()).then((InvocationOnMock m) -> holder.getPendingFederation());
        when(federationStorageProvider.getFederationElection(any())).then((InvocationOnMock m) -> {
            if (mockedFederationElection != null) {
                holder.setFederationElection(mockedFederationElection);
            }

            if (holder.getFederationElection() == null) {
                AddressBasedAuthorizer auth = m.getArgument(0);
                holder.setFederationElection(new ABICallElection(auth));
            }

            return holder.getFederationElection();
        });
        doAnswer((InvocationOnMock m) -> {
            holder.setActiveFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setNewFederation(any());
        doAnswer((InvocationOnMock m) -> {
            holder.setRetiringFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setOldFederation(any());
        doAnswer((InvocationOnMock m) -> {
            holder.setPendingFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setPendingFederation(any());

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMock)
            .withRskExecutionBlock(executionBlock)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .build();

        return bridgeSupportBuilder
            .withBridgeConstants(constantsMock)
            .withProvider(providerMock)
            .withFederationSupport(federationSupport)
            .withExecutionBlock(executionBlock)
            .build();
    }

    private BridgeSupport getBridgeSupportWithMocksForFederationTests(
        FederationStorageProvider federationStorageProvider,
        Federation mockedNewFederation,
        PendingFederation mockedPendingFederation,
        ABICallElection mockedFederationElection,
        Block executionBlock,
        BridgeEventLogger eventLogger) {

        BridgeConstants constantsMock = mock(BridgeConstants.class);
        FederationConstants federationConstantsMock = mock(FederationConstants.class);

        when(federationConstantsMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        when(federationConstantsMock.getFederationChangeAuthorizer()).thenReturn(federationConstants.getFederationChangeAuthorizer());

        long federationActivationAge = federationConstants.getFederationActivationAge(activations);
        when(federationConstantsMock.getFederationActivationAge(any())).thenReturn(federationActivationAge);

        class FederationHolder {
            private PendingFederation pendingFederation;
            private Federation activeFederation;
            private Federation retiringFederation;
            private ABICallElection federationElection;

            public final List<UTXO> retiringUTXOs = new ArrayList<>();
            public final List<UTXO> activeUTXOs = new ArrayList<>();

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

        when(federationStorageProvider.getOldFederationBtcUTXOs()).then((InvocationOnMock m) -> holder.retiringUTXOs);
        when(federationStorageProvider.getNewFederationBtcUTXOs(any(), any())).then((InvocationOnMock m) -> holder.activeUTXOs);

        holder.setActiveFederation(mockedNewFederation);
        when(federationStorageProvider.getNewFederation(any(), any())).then((InvocationOnMock m) -> holder.getActiveFederation());
        when(federationStorageProvider.getOldFederation(any(), any())).then((InvocationOnMock m) -> holder.getRetiringFederation());
        when(federationStorageProvider.getPendingFederation()).then((InvocationOnMock m) -> holder.getPendingFederation());
        when(federationStorageProvider.getFederationElection(any())).then((InvocationOnMock m) -> {
            if (mockedFederationElection != null) {
                holder.setFederationElection(mockedFederationElection);
            }

            if (holder.getFederationElection() == null) {
                AddressBasedAuthorizer auth = m.getArgument(0);
                holder.setFederationElection(new ABICallElection(auth));
            }

            return holder.getFederationElection();
        });
        doAnswer((InvocationOnMock m) -> {
            holder.setActiveFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setNewFederation(any());
        doAnswer((InvocationOnMock m) -> {
            holder.setRetiringFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setOldFederation(any());
        doAnswer((InvocationOnMock m) -> {
            holder.setPendingFederation(m.getArgument(0));
            return null;
        }).when(federationStorageProvider).setPendingFederation(any());

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMock)
            .withRskExecutionBlock(executionBlock)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationsBeforeForks)
            .build();

        return bridgeSupportBuilder
            .withBridgeConstants(constantsMock)
            .withProvider(providerMock)
            .withEventLogger(eventLogger)
            .withFederationSupport(federationSupport)
            .withExecutionBlock(executionBlock)
            .build();
    }

    private BtcTransaction createTransaction() {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(new TransactionInput(btcParams, btcTx, new byte[0]));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams)));
        return btcTx;
    }

    private UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
            BitcoinTestUtils.createHash(1),
            1,
            value,
            0,
            false,
            ScriptBuilder.createOutputScript(address)
        );
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

    private FederationStorageProvider createFederationStorageProvider(Repository repository) {
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        return new FederationStorageProviderImpl(bridgeStorageAccessor);
    }
}