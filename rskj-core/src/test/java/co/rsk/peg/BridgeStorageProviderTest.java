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
import co.rsk.config.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/7/2016.
 */
@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to some setup generalizations
@MockitoSettings(strictness = Strictness.LENIENT)
public class BridgeStorageProviderTest {
    private static final byte FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST = (byte) 1;
    private static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY = DataWord.fromString("newFederationBtcUTXOs");
    private static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP = DataWord.fromString("newFederationBtcUTXOsForTestnet");
    private static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP = DataWord.fromString("newFedBtcUTXOsForTestnetPostHop");
    private static final DataWord NEXT_PEGOUT_HEIGHT_KEY = DataWord.fromString("nextPegoutHeight");

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig.ForBlock activationsBeforeFork = ActivationConfigsForTest.genesis().forBlock(0L);
    private final ActivationConfig.ForBlock activationsAllForks = ActivationConfigsForTest.all().forBlock(0);
    private final NetworkParameters networkParameters = config.getNetworkConstants().getBridgeConstants().getBtcParams();

    private int transactionOffset;

    @Test
    public void createInstance() throws IOException {
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assertions.assertNotNull(releaseRequestQueue);
        Assertions.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assertions.assertNotNull(releaseTransactionSet);
        Assertions.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assertions.assertNotNull(signatures);
        Assertions.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assertions.assertNotNull(utxos);
        Assertions.assertTrue(utxos.isEmpty());
    }

    @Test
    public void createSaveAndRecreateInstance() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );
        provider0.getReleaseRequestQueue();
        provider0.getReleaseTransactionSet();
        provider0.getRskTxsWaitingForSignatures();
        provider0.getNewFederationBtcUTXOs();
        provider0.getOldFederationBtcUTXOs();
        provider0.save();
        track.commit();

        track = repository.startTracking();

        RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

        MatcherAssert.assertThat(repository.isContract(contractAddress), is(true));
        Assertions.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("releaseRequestQueue".getBytes())));
        Assertions.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("releaseTransactionSet".getBytes())));
        Assertions.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("rskTxsWaitingFS".getBytes())));
        Assertions.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("newFederationBtcUTXOs".getBytes())));
        Assertions.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("oldFederationBtcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assertions.assertNotNull(releaseRequestQueue);
        Assertions.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assertions.assertNotNull(releaseTransactionSet);
        Assertions.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assertions.assertNotNull(signatures);
        Assertions.assertTrue(signatures.isEmpty());

        List<UTXO> newUtxos = provider.getNewFederationBtcUTXOs();

        Assertions.assertNotNull(newUtxos);
        Assertions.assertTrue(newUtxos.isEmpty());

        List<UTXO> oldUtxos = provider.getOldFederationBtcUTXOs();

        Assertions.assertNotNull(oldUtxos);
        Assertions.assertTrue(oldUtxos.isEmpty());
    }

    @Test
    public void createSaveAndRecreateInstanceWithProcessedHashes() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );
        provider0.setHeightBtcTxhashAlreadyProcessed(hash1, 1L);
        provider0.setHeightBtcTxhashAlreadyProcessed(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1).isPresent());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(hash2).isPresent());
    }

    @Test
    public void createSaveAndRecreateInstanceWithTxsWaitingForSignatures() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Keccak256 hash1 = PegTestUtils.createHash3(1);
        Keccak256 hash2 = PegTestUtils.createHash3(2);
        Keccak256 hash3 = PegTestUtils.createHash3(3);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );
        provider0.getRskTxsWaitingForSignatures().put(hash1, tx1);
        provider0.getRskTxsWaitingForSignatures().put(hash2, tx2);
        provider0.getRskTxsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assertions.assertNotNull(signatures);

        Assertions.assertTrue(signatures.containsKey(hash1));
        Assertions.assertTrue(signatures.containsKey(hash2));
        Assertions.assertTrue(signatures.containsKey(hash3));

        Assertions.assertEquals(tx1.getHash(), signatures.get(hash1).getHash());
        Assertions.assertEquals(tx2.getHash(), signatures.get(hash2).getHash());
        Assertions.assertEquals(tx3.getHash(), signatures.get(hash3).getHash());
    }

    @Test
    public void createSaveAndRecreateInstanceWithUTXOS() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash(1);
        Sha256Hash hash2 = PegTestUtils.createHash(2);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assertions.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assertions.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    @Test
    public void getNewFederation_initialVersion() {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation newFederation = buildMockFederation(100, 200, 300);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            mockAddress("aabbccdd"),
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("newFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters networkParameters = invocation.getArgument(1);

                // Make sure we're deserializing what just came from the repo with the correct BTC context
                assertArrayEquals(new byte[]{(byte) 0xaa}, data);
                Assertions.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
                return newFederation;
            });

            Assertions.assertEquals(newFederation, storageProvider.getNewFederation());
            Assertions.assertEquals(newFederation, storageProvider.getNewFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    @Test
    public void getNewFederation_initialVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                    repositoryMock,
                    mockAddress("aabbccdd"),
                    config.getNetworkConstants().getBridgeConstants(),
                    activationsBeforeFork
            );

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                    // First call is storage version getter
                    return new byte[0];
                } else {
                    // Second and third calls are actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(DataWord.fromString("newFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getNewFederation());
            assertNull(storageProvider.getNewFederation());
            Assertions.assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class)), never());
        }
    }

    @Test
    public void getNewFederation_multiKeyVersion() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        testGetNewFederationPostMultiKey(newFederation, activationsBeforeFork);
    }

    @Test
    public void getNewFederation_RSKIP_201_active_erp_fed() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        ErpFederation erpFederation = new ErpFederation(
            newFederation.getMembers(),
            newFederation.getCreationTime(),
            newFederation.getCreationBlockNumber(),
            newFederation.getBtcParams(),
            config.getNetworkConstants().getBridgeConstants().getErpFedPubKeysList(),
            config.getNetworkConstants().getBridgeConstants().getErpFedActivationDelay(),
            activationsAllForks
        );

        testGetNewFederationPostMultiKey(erpFederation, activationsAllForks);
    }

    @Test
    public void getNewFederation_multiKeyVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                    repositoryMock,
                    mockAddress("aabbccdd"),
                    config.getNetworkConstants().getBridgeConstants(),
                    activationsBeforeFork
            );

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                    // First call is storage version getter
                    return RLP.encodeBigInteger(BigInteger.valueOf(1234));
                } else {
                    // Second and third calls are the actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(DataWord.fromString("newFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getNewFederation());
            assertNull(storageProvider.getNewFederation());
            Assertions.assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class)), never());
        }
    }

    @Test
    public void saveNewFederation_preMultikey() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            mockAddress("aabbccdd"),
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)))
                    .then((InvocationOnMock invocation) -> {
                        Federation federation = invocation.getArgument(0);
                        Assertions.assertEquals(newFederation, federation);
                        serializeCalls.add(0);
                        return new byte[]{(byte) 0xbb};
                    });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                assertArrayEquals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd},
                        contractAddress.getBytes());
                Assertions.assertEquals(DataWord.valueOf("newFederation".getBytes(StandardCharsets.UTF_8)), address);
                assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.saveNewFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setNewFederation(newFederation);
            storageProvider.saveNewFederation();
            Assertions.assertEquals(1, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    public void saveNewFederation_postMultiKey() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        testSaveNewFederationPostMultiKey(newFederation, 1000);
    }

    @Test
    public void saveNewFederation_postMultiKey_RSKIP_201_active_erp_fed() {
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        Federation newFederation = buildMockFederation(100, 200, 300);

        ErpFederation erpFederation = new ErpFederation(
            newFederation.getMembers(),
            newFederation.getCreationTime(),
            newFederation.getCreationBlockNumber(),
            newFederation.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activationsAllForks
        );

        testSaveNewFederationPostMultiKey(erpFederation, 2000);
    }

    @Test
    public void getOldFederation_initialVersion() {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation oldFederation = buildMockFederation(100, 200, 300);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            mockAddress("aabbccdd"),
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters networkParameters = invocation.getArgument(1);
                // Make sure we're deserializing what just came from the repo with the correct BTC context
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa}, data));
                Assertions.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
                return oldFederation;
            });

            Assertions.assertEquals(oldFederation, storageProvider.getOldFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    @Test
    public void getOldFederation_initialVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                    repositoryMock,
                    mockAddress("aabbccdd"),
                    config.getNetworkConstants().getBridgeConstants(),
                    activationsBeforeFork
            );

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                    // First call is storage version getter
                    return new byte[0];
                } else {
                    // Second and third calls are actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getOldFederation());
            assertNull(storageProvider.getOldFederation());
            Assertions.assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class)), never());
        }
    }

    @Test
    public void getOldFederation_multiKeyVersion() {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        testGetOldFederation(oldFederation, activationsBeforeFork);
    }

    @Test
    public void getOldFederation_RSKIP_201_active_erp_fed() {
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        Federation oldFederation = buildMockFederation(100, 200, 300);
        ErpFederation erpFederation = new ErpFederation(
            oldFederation.getMembers(),
            oldFederation.getCreationTime(),
            oldFederation.getCreationBlockNumber(),
            oldFederation.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activationsAllForks
        );

        testGetOldFederation(erpFederation, activationsAllForks);
    }

    @Test
    public void getOldFederation_multiKeyVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                    repositoryMock,
                    mockAddress("aabbccdd"),
                    config.getNetworkConstants().getBridgeConstants(),
                    activationsBeforeFork
            );

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                    // First call is storage version getter
                    return RLP.encodeBigInteger(BigInteger.valueOf(1234));
                } else {
                    // Second and third calls are actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getOldFederation());
            assertNull(storageProvider.getOldFederation());
            Assertions.assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class)), never());
        }
    }

    @Test
    public void saveOldFederation_preMultikey() {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            mockAddress("aabbccdd"),
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class))).then((InvocationOnMock invocation) -> {
                Federation federation = invocation.getArgument(0);
                Assertions.assertEquals(oldFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });
            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes()));
                Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.saveOldFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setOldFederation(oldFederation);
            storageProvider.saveOldFederation();
            Assertions.assertEquals(1, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    public void saveOldFederation_postMultikey() {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        testSaveOldFederation(oldFederation, 1000);
    }

    @Test
    public void saveOldFederation_postMultikey_RSKIP_201_active_erp_fed() {
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        Federation oldFederation = buildMockFederation(100, 200, 300);
        ErpFederation erpFederation = new ErpFederation(
            oldFederation.getMembers(),
            oldFederation.getCreationTime(),
            oldFederation.getCreationBlockNumber(),
            oldFederation.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activationsAllForks
        );

        testSaveOldFederation(erpFederation, 2000);
    }

    @Test
    public void saveOldFederation_preMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                // Make sure the bytes are set to the correct address in the repo and that what's saved is null
                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                assertNull(data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            storageProvider.saveOldFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            storageProvider.setOldFederation(null);
            storageProvider.saveOldFederation();
            Assertions.assertEquals(1, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)), never());
        }
    }

    @Test
    public void saveOldFederation_postMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                    Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is null
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                    assertNull(data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            storageProvider.saveOldFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            storageProvider.setOldFederation(null);
            storageProvider.saveOldFederation();
            Assertions.assertEquals(2, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)), never());
        }
    }

    @Test
    public void getPendingFederation_initialVersion() {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializePendingFederationOnlyBtcKeys(any(byte[].class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                // Make sure we're deserializing what just came from the repo with the correct BTC context
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa}, data));
                return pendingFederation;
            });

            Assertions.assertEquals(pendingFederation, storageProvider.getPendingFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    @Test
    public void getPendingFederation_initialVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {

            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

                if (storageCalls.size() == 1) {
                    // First call is storage version getter
                    Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                    return new byte[0];
                } else {
                    // Second call is the actual storage getter
                    Assertions.assertEquals(2, storageCalls.size());
                    Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getPendingFederation());
            Assertions.assertEquals(2, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializePendingFederation(any(byte[].class)), never());
        }
    }

    @Test
    public void getPendingFederation_multiKeyVersion() {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializePendingFederation(any(byte[].class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                // Make sure we're deserializing what just came from the repo with the correct BTC context
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa}, data));
                return pendingFederation;
            });

            Assertions.assertEquals(pendingFederation, storageProvider.getPendingFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    @Test
    public void getPendingFederation_multiKeyVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

                if (storageCalls.size() == 1) {
                    // First call is storage version getter
                    Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                    return RLP.encodeBigInteger(BigInteger.valueOf(1234));
                } else {
                    // Second call is the actual storage getter
                    Assertions.assertEquals(2, storageCalls.size());
                    Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                    return null;
                }
            });

            assertNull(storageProvider.getPendingFederation());
            Assertions.assertEquals(2, storageCalls.size());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializePendingFederation(any(byte[].class)), never());
        }
    }

    @Test
    public void savePendingFederation_preMultikey() {
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class))).then((InvocationOnMock invocation) -> {
                PendingFederation federation = invocation.getArgument(0);
                Assertions.assertEquals(pendingFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes()));
                Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.savePendingFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setPendingFederation(pendingFederation);
            storageProvider.savePendingFederation();
            Assertions.assertEquals(1, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    public void savePendingFederation_preMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                assertArrayEquals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes());
                Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                assertNull(data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            storageProvider.savePendingFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            storageProvider.setPendingFederation(null);
            storageProvider.savePendingFederation();
            Assertions.assertEquals(1, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class)), never());
        }
    }

    @Test
    public void savePendingFederation_postMultikey() {
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, Mockito.CALLS_REAL_METHODS)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class))).then((InvocationOnMock invocation) -> {
                PendingFederation federation = invocation.getArgument(0);
                Assertions.assertEquals(pendingFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

                if (storageBytesCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                    Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                    Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.savePendingFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setPendingFederation(pendingFederation);
            storageProvider.savePendingFederation();
            Assertions.assertEquals(2, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    public void savePendingFederation_postMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, Mockito.CALLS_REAL_METHODS)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

                if (storageBytesCalls.size() == 1) {
                    Assertions.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                    Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    Assertions.assertEquals(DataWord.fromString("pendingFederation"), address);
                    assertNull(data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            storageProvider.savePendingFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            storageProvider.setPendingFederation(null);
            storageProvider.savePendingFederation();
            Assertions.assertEquals(2, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class)), never());
        }
    }

    @Test
    public void getFederationElection_nonNullBytes() {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assertions.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assertions.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeElection(any(byte[].class), any(AddressBasedAuthorizer.class))).then((InvocationOnMock invocation) -> {
                calls.add(0);
                byte[] data = invocation.getArgument(0);
                AddressBasedAuthorizer authorizer = invocation.getArgument(1);
                // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa}, data));
                Assertions.assertEquals(authorizerMock, authorizer);
                return electionMock;
            });

            Assertions.assertSame(electionMock, storageProvider.getFederationElection(authorizerMock));
            Assertions.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
        }
    }

    @Test
    public void getFederationElection_nullBytes() {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assertions.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assertions.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeElection(any(byte[].class), any(AddressBasedAuthorizer.class))).then((InvocationOnMock invocation) -> {
                calls.add(0);
                return null;
            });
        }

        ABICallElection result = storageProvider.getFederationElection(authorizerMock);
        Assertions.assertSame(authorizerMock, TestUtils.getInternalState(result, "authorizer"));
        Assertions.assertEquals(0, result.getVotes().size());
        Assertions.assertEquals(1, calls.size()); // getStorageBytes is the only one called (can't be the other way around)
    }

    @Test
    public void saveFederationElection() {
        ABICallElection electionMock = mock(ABICallElection.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeElection(any(ABICallElection.class))).then((InvocationOnMock invocation) -> {
                ABICallElection election = invocation.getArgument(0);
                Assertions.assertSame(electionMock, election);
                serializeCalls.add(0);
                return Hex.decode("aabb");
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd}, contractAddress.getBytes()));
                Assertions.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
                Assertions.assertTrue(Arrays.equals(Hex.decode("aabb"), data));
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.saveFederationElection();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            TestUtils.setInternalState(storageProvider, "federationElection", electionMock);
            storageProvider.saveFederationElection();
            Assertions.assertEquals(1, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    public void getLockWhitelist_nonNullBytes() {
        List<Integer> calls = new ArrayList<>();
        LockWhitelist whitelistMock = new LockWhitelist(new HashMap<>());
        LockWhitelistEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);
        LockWhitelistEntry unlimitedEntry = new UnlimitedWhiteListEntry(getBtcAddress("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        whitelistMock.put(oneOffEntry.address(), oneOffEntry);
        whitelistMock.put(unlimitedEntry.address(), unlimitedEntry);
        Repository repositoryMock = mock(Repository.class);
        // Overriding Activation to make sure it serializes the unlimited whitelist data
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    // Make sure the bytes are got from the correct address in the repo
                    Assertions.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                    Assertions.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                    return new byte[]{(byte)0xaa};
                });
        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    // Make sure the bytes are got from the correct address in the repo
                    Assertions.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                    Assertions.assertEquals(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                    return new byte[]{(byte)0xbb};
                });
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                    .then((InvocationOnMock invocation) -> {
                        calls.add(0);
                        byte[] data = invocation.getArgument(0);
                        NetworkParameters parameters = invocation.getArgument(1);
                        Assertions.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
                        // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                        Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xaa}, data));
                        HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                        map.put(oneOffEntry.address(), oneOffEntry);
                        return Pair.of(map, 0);
                    });
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                    .then((InvocationOnMock invocation) -> {
                        calls.add(0);
                        byte[] unlimitedData = invocation.getArgument(0);
                        NetworkParameters parameters = invocation.getArgument(1);
                        Assertions.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
                        // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                        Assertions.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, unlimitedData));
                        HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                        map.put(unlimitedEntry.address(), unlimitedEntry);
                        return map;
                    });

            Assertions.assertEquals(whitelistMock.getAll(), storageProvider.getLockWhitelist().getAll());
            Assertions.assertEquals(4, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes (we call getStorageBytes twice)
        }
    }

    @Test
    public void getLockWhitelist_nullBytes() {
        List<Integer> calls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    // Make sure the bytes are got from the correct address in the repo
                    Assertions.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                    Assertions.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                    return new byte[]{(byte)0xee};
                });
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                    .then((InvocationOnMock invocation) -> {
                        calls.add(0);
                        return null;
                    });
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                    .then((InvocationOnMock invocation) -> {
                        calls.add(0); // THIS ONE WON'T BE CALLED BECAUSE ONEOFF IS EMPTY
                        Assertions.fail("As we don't have data for one-off, we shouldn't have called deserialize unlimited");
                        return null;
                    });

            LockWhitelist result = storageProvider.getLockWhitelist();
            Assertions.assertNotNull(result);
            Assertions.assertEquals(0, result.getSize().intValue());
            Assertions.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
        }
    }

    @Test
    public void saveLockWhitelist() {
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        // Overriding activation to make sure it serializes the unlimited whitelist data
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            // Mock the One-Off serialization
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
                    .then((InvocationOnMock invocation) -> {
                        Pair<List<OneOffWhiteListEntry>, Integer> data = invocation.getArgument(0);
                        Assertions.assertEquals(whitelistMock.getAll(OneOffWhiteListEntry.class), data.getLeft());
                        Assertions.assertSame(whitelistMock.getDisableBlockHeight(), data.getRight());
                        serializeCalls.add(0);
                        return Hex.decode("ccdd");
                    });

            Mockito
                    .doAnswer((InvocationOnMock invocation) -> {
                        storageBytesCalls.add(0);
                        RskAddress contractAddress = invocation.getArgument(0);
                        DataWord address = invocation.getArgument(1);
                        byte[] data = invocation.getArgument(2);
                        // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                        Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                        Assertions.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                        Assertions.assertTrue(Arrays.equals(Hex.decode("ccdd"), data));
                        return null;
                    })
                    .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

            // Mock the Unlimited serialization
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.serializeUnlimitedLockWhitelist(any(List.class)))
                    .then((InvocationOnMock invocation) -> {
                        List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = invocation.getArgument(0);
                        Assertions.assertEquals(whitelistMock.getAll(UnlimitedWhiteListEntry.class), unlimitedWhiteListEntries);
                        serializeCalls.add(0);
                        return Hex.decode("bbcc");
                    });

            Mockito
                    .doAnswer((InvocationOnMock invocation) -> {
                        storageBytesCalls.add(0);
                        RskAddress contractAddress = invocation.getArgument(0);
                        DataWord address = invocation.getArgument(1);
                        byte[] data = invocation.getArgument(2);
                        // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                        Assertions.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                        Assertions.assertEquals(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                        Assertions.assertTrue(Arrays.equals(Hex.decode("bbcc"), data));
                        return null;
                    })
                    .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

            storageProvider.saveLockWhitelist();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            TestUtils.setInternalState(storageProvider, "lockWhitelist", whitelistMock);
            storageProvider.saveLockWhitelist();
            Assertions.assertEquals(2, storageBytesCalls.size());
            Assertions.assertEquals(2, serializeCalls.size());
        }
    }

    @Test
    public void saveLockWhiteListAfterGetWithData() {
        AtomicReference<Boolean> storageCalled = new AtomicReference<>();
        storageCalled.set(Boolean.FALSE);
        Repository repositoryMock = mock(Repository.class);
        OneOffWhiteListEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig().forBlock(500L));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> new byte[]{(byte)0xaa});

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                    .then((InvocationOnMock invocation) -> {
                        HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                        map.put(oneOffEntry.address(), oneOffEntry);
                        return Pair.of(map, 0);
                    });

            bridgeSerializationUtilsMocked
                    .when(() -> BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
                    .thenReturn(new byte[]{(byte) 0xee});

            Mockito
                    .doAnswer((InvocationOnMock invocation) -> {
                        storageCalled.set(Boolean.TRUE);
                        return null;
                    })
                    .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8))), eq(new byte[]{(byte) 0xee}));

            Assertions.assertTrue(storageProvider.getLockWhitelist().getSize() > 0);

            storageProvider.saveLockWhitelist();

            Assertions.assertTrue(storageCalled.get());
        }
    }

    @Test
    public void getReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        List<ReleaseRequestQueue.Entry> oldEntriesList = new ArrayList<>(Collections.singletonList(
                new ReleaseRequestQueue.Entry(
                        Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                        Coin.COIN)));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueue"))))
                .then((InvocationOnMock invocation) ->
                        BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(oldEntriesList)));

        ReleaseRequestQueue result = storageProvider.getReleaseRequestQueue();

        verify(repositoryMock, never()).getStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueueWithTxHash")));

        Assertions.assertEquals(1, result.getEntries().size());
        Assertions.assertTrue(result.getEntries().containsAll(oldEntriesList));
    }

    @Test
    public void getReleaseRequestQueue_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        ReleaseRequestQueue.Entry oldEntry = new ReleaseRequestQueue.Entry(
                Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN);

        ReleaseRequestQueue.Entry newEntry = new ReleaseRequestQueue.Entry(
                Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN,
                PegTestUtils.createHash3(0)
        );

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(),eq(DataWord.fromString("releaseRequestQueue")))).
                thenReturn(BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Arrays.asList(oldEntry)))));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activations);

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN,
                PegTestUtils.createHash3(0));

        ReleaseRequestQueue result = storageProvider.getReleaseRequestQueue();

        Assertions.assertEquals(2, result.getEntries().size());
        Assertions.assertEquals(result.getEntries().get(0), oldEntry);
        Assertions.assertEquals(result.getEntries().get(1), newEntry);
    }

    @Test
    public void saveReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        List<ReleaseRequestQueue.Entry> oldEntriesList = new ArrayList<>(Collections.singletonList(
                new ReleaseRequestQueue.Entry(
                        Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                        Coin.COIN)));

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();
        releaseRequestQueue.add(Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN);

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), networkParameters);
            Assertions.assertEquals(oldEntriesList, entries);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueue")), any(byte[].class));

        storageProvider.saveReleaseRequestQueue();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueue")), any(byte[].class));
        verify(repositoryMock, never()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueueWithTxHash")), any(byte[].class));
    }

    @Test
    public void saveReleaseRequestQueue_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        ReleaseRequestQueue.Entry newEntry =
                new ReleaseRequestQueue.Entry(
                        Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                        Coin.COIN,
                        PegTestUtils.createHash3(0)
                );

        ReleaseRequestQueue.Entry oldEntry =
                new ReleaseRequestQueue.Entry(
                        Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                        Coin.COIN
                );

        Repository repositoryMock = mock(Repository.class);
        when(repositoryMock.getStorageBytes(any(),eq(DataWord.fromString("releaseRequestQueue")))).
                thenReturn(BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Arrays.asList(oldEntry)))));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activations);
        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN,
                PegTestUtils.createHash3(0)
        );

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), networkParameters);
            Assertions.assertEquals(entries, new ArrayList<>(Arrays.asList(oldEntry)));
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueue")), any(byte[].class));

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), networkParameters, true);
            Assertions.assertEquals(entries, new ArrayList<>(Arrays.asList(newEntry)));
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueueWithTxHash")), any(byte[].class));

        storageProvider.saveReleaseRequestQueue();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueue")), any(byte[].class));
        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseRequestQueueWithTxHash")), any(byte[].class));
        Assertions.assertEquals(2, storageProvider.getReleaseRequestQueue().getEntries().size());
    }

    @Test
    public void getReleaseTransactionSet_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"),
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Set<ReleaseTransactionSet.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet"))))
                .then((InvocationOnMock invocation) ->
                        BridgeSerializationUtils.serializeReleaseTransactionSet(new ReleaseTransactionSet(oldEntriesSet)));

        ReleaseTransactionSet result = storageProvider.getReleaseTransactionSet();

        verify(repositoryMock, never()).getStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSetWithTxHash")));

        Assertions.assertEquals(1, result.getEntries().size());
        Assertions.assertTrue(result.getEntries().containsAll(oldEntriesSet));
    }

    @Test
    public void getReleaseTransactionSet_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Set<ReleaseTransactionSet.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        Set<ReleaseTransactionSet.Entry> newEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                        1L,
                        PegTestUtils.createHash3(0)
                )));

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet"))))
                .thenReturn(BridgeSerializationUtils.serializeReleaseTransactionSet(new ReleaseTransactionSet(oldEntriesSet)));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"),
                config.getNetworkConstants().getBridgeConstants(), activations);

        ReleaseTransactionSet releaseTransactionSet = storageProvider.getReleaseTransactionSet();

        releaseTransactionSet.add(new SimpleBtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams(), PegTestUtils.createHash(0)),
                1L,
                PegTestUtils.createHash3(0));

        ReleaseTransactionSet result = storageProvider.getReleaseTransactionSet();

        Assertions.assertEquals(2, result.getEntries().size());
    }

    @Test
    public void saveReleaseTransactionSet_before_rskip_146_activations() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Set<ReleaseTransactionSet.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        ReleaseTransactionSet releaseTransactionSet = storageProvider.getReleaseTransactionSet();
        releaseTransactionSet.add(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L);

        doAnswer((i) -> {
            Set<ReleaseTransactionSet.Entry> entries = BridgeSerializationUtils.deserializeReleaseTransactionSet(i.getArgument(2), networkParameters).getEntries();
            Assertions.assertEquals(oldEntriesSet, entries);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet")), any(byte[].class));

        storageProvider.saveReleaseTransactionSet();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet")), any(byte[].class));
        verify(repositoryMock, never()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSetWithTxHash")), any(byte[].class));
    }

    @Test
    public void saveReleaseTransactionSet_after_rskip_146_activations() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Set<ReleaseTransactionSet.Entry> newEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L, PegTestUtils.createHash3(0))
        ));

        Set<ReleaseTransactionSet.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
                new ReleaseTransactionSet.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(),eq(DataWord.fromString("releaseTransactionSet")))).
                thenReturn(BridgeSerializationUtils.serializeReleaseTransactionSet(new ReleaseTransactionSet(oldEntriesSet)));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activations);
        ReleaseTransactionSet releaseTransactionSet = storageProvider.getReleaseTransactionSet();

        releaseTransactionSet.add(new SimpleBtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams(), PegTestUtils.createHash(1)),
                1L,
                PegTestUtils.createHash3(0));

        doAnswer((i) -> {
            Set<ReleaseTransactionSet.Entry> entries = BridgeSerializationUtils.deserializeReleaseTransactionSet(i.getArgument(2), networkParameters).getEntries();
            Assertions.assertEquals(entries, oldEntriesSet);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet")), any(byte[].class));

        doAnswer((i) -> {
            Set<ReleaseTransactionSet.Entry> entries = BridgeSerializationUtils.deserializeReleaseTransactionSet(i.getArgument(2), networkParameters, true).getEntries();
            Assertions.assertEquals(entries, newEntriesSet);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSetWithTxHash")), any(byte[].class));

        storageProvider.saveReleaseTransactionSet();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSet")), any(byte[].class));
        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(DataWord.fromString("releaseTransactionSetWithTxHash")), any(byte[].class));
        Assertions.assertEquals(2, storageProvider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void getReleaseTransaction_after_rskip_146_activations() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activations);

        provider0.getReleaseTransactionSet().add(tx1, 1L, PegTestUtils.createHash3(0));
        provider0.getReleaseTransactionSet().add(tx2, 2L, PegTestUtils.createHash3(1));
        provider0.getReleaseTransactionSet().add(tx3, 3L, PegTestUtils.createHash3(2));

        provider0.save();

        track.commit();

        //Reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activations);

        Assertions.assertEquals(3, provider.getReleaseTransactionSet().getEntries().size());
        Assertions.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void setFeePerKb_savedAndRecreated() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Coin expectedCoin = Coin.valueOf(5325);
        provider0.setFeePerKb(expectedCoin);
        provider0.saveFeePerKb();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        MatcherAssert.assertThat(provider.getFeePerKb(), is(expectedCoin));
    }

    @Test
    public void getFeePerKbElection_emptyVotes() {
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        HashMap<ABICallSpec, List<RskAddress>> electionVotes = new HashMap<>();
        byte[] serializedElection = BridgeSerializationUtils.serializeElection(
                new ABICallElection(authorizerMock, electionVotes));
        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
                .thenReturn(serializedElection);

        ABICallElection result = storageProvider.getFeePerKbElection(authorizerMock);
        MatcherAssert.assertThat(result.getVotes().isEmpty(), is(true));
        MatcherAssert.assertThat(result.getWinner(), nullValue());
    }

    @Test
    public void getFeePerKbElection_withVotes() {
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        Repository repositoryMock = mock(Repository.class);
        when(authorizerMock.getRequiredAuthorizedKeys())
                .thenReturn(1);
        when(authorizerMock.isAuthorized(any(RskAddress.class)))
                .thenReturn(true);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        byte[] electionFee = new byte[] {0x43, 0x19};
        ABICallSpec expectedWinner = new ABICallSpec("setFeePerKb", new byte[][]{electionFee});
        List<RskAddress> voters = new ArrayList<>();
        voters.add(new RskAddress("0000000000000000000000000000000000001321"));
        voters.add(new RskAddress("0000000000000000000000000000000000004049"));
        HashMap<ABICallSpec, List<RskAddress>> electionVotes = new HashMap<>();
        electionVotes.put(expectedWinner, voters);
        byte[] serializedElection = BridgeSerializationUtils.serializeElection(
                new ABICallElection(authorizerMock, electionVotes));
        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
                .thenReturn(serializedElection);

        ABICallElection result = storageProvider.getFeePerKbElection(authorizerMock);
        MatcherAssert.assertThat(result.getVotes(), is(electionVotes));
        MatcherAssert.assertThat(result.getWinner(), is(expectedWinner));
    }

    @Test
    public void setLockingCap_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider0.setLockingCap(Coin.ZERO);
        provider0.saveLockingCap();

        // If the network upgrade is not enabled we shouldn't be writing in the repository
        verify(repository, never()).addStorageBytes(any(), any(), any());
    }

    @Test
    public void setLockingCap_after_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        provider0.setLockingCap(Coin.ZERO);
        provider0.saveLockingCap();

        // Once the network upgrade is active, we will store the locking cap in the repository
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromString("lockingCap"),
            BridgeSerializationUtils.serializeCoin(Coin.ZERO)
        );
    }

    @Test
    public void getLockingCap_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        assertNull(provider0.getLockingCap());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lockingCap"));
    }

    @Test
    public void getLockingCap_after_fork() {
        Repository repository = mock(Repository.class);
        // If by chance the repository is called I want to force the tests to fail
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lockingCap"))).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        assertEquals(Coin.SATOSHI, provider0.getLockingCap());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lockingCap"));
    }

    @Test
    public void setLockingCapAndGetLockingCap() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        Coin expectedCoin = Coin.valueOf(666);

        // We store the locking cap
        provider0.setLockingCap(expectedCoin);
        provider0.saveLockingCap();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        // And then we get it back
        MatcherAssert.assertThat(provider.getLockingCap(), is(expectedCoin));
    }

    @Test
    public void getHeightIfBtcTxhashIsAlreadyProcessed_before_RSKIP134_does_not_use_new_storage()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        HashMap<Sha256Hash, Long> hashes = new HashMap<>();
        hashes.put(hash, 1L);
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP")))
                .thenReturn(BridgeSerializationUtils.serializeMapOfHashesToLong(hashes));

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());

        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("btcTxHashAP-" + hash.toString()));
    }

    @Test
    public void getHeightIfBtcTxhashIsAlreadyProcessed_after_RSKIP134_uses_new_storage()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        HashMap<Sha256Hash, Long> hashes = new HashMap<>();
        hashes.put(hash1, 1L);
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP")))
                .thenReturn(BridgeSerializationUtils.serializeMapOfHashesToLong(hashes));

        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("btcTxHashAP-" + hash2.toString())))
                .thenReturn(BridgeSerializationUtils.serializeLong(2L));

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        // Get hash1 which is stored in old storage
        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash1);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());

        // old storage was accessed and new storage not
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("btcTxHashAP-" + hash2.toString()));

        // Get hash2 which is stored in new storage
        result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash2);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(2), result.get());

        // old storage wasn't accessed anymore (because it is cached) and new storage was accessed
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("btcTxHashAP-" + hash2.toString()));

        // Get hash2 again
        result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash2);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(2), result.get());

        // No more accesses to repository, as both values are in cache
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("btcTxHashAP-" + hash2.toString()));
    }

    @Test
    public void setHeightBtcTxhashAlreadyProcessed_before_RSKIP134_does_not_use_new_storage()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        // The repository is accessed once to set the value
        verify(repository, times(1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    public void setHeightBtcTxhashAlreadyProcessed_before_RSKIP134_uses_new_storage()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        // The repository is never accessed as the new storage keeps the values in cache until save
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    public void saveHeightBtcTxHashAlreadyProcessed() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        provider0.saveHeightBtcTxHashAlreadyProcessed();

        // The repository is never accessed as the new storage keeps the values in cache until save
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("btcTxHashesAP"));

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    public void getCoinBaseInformation_before_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        CoinbaseInformation result = provider.getCoinbaseInformation(hash);
        assertNull(result);

        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("coinbaseInformation-" + hash.toString()));
    }

    @Test
    public void getCoinBaseInformation_after_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromLongString("coinbaseInformation-" + hash.toString())))
                .thenReturn(BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        CoinbaseInformation result = provider.getCoinbaseInformation(hash);
        assertEquals(coinbaseInformation.getWitnessMerkleRoot(),result.getWitnessMerkleRoot());
    }

    @Test
    public void setCoinBaseInformation_before_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertNull(provider.getCoinbaseInformation(hash));
    }

    @Test
    public void setCoinBaseInformation_after_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertEquals(coinbaseInformation, provider.getCoinbaseInformation(hash));
    }

    @Test
    public void saveCoinBaseInformation_before_RSKIP143() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertNull(provider.getCoinbaseInformation(hash));

        provider.save();

        verify(repository, never()).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("coinbaseInformation" + hash.toString()),
                BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation)
        );
    }

    @Test
    public void saveCoinBaseInformation_after_RSKIP143() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertEquals(coinbaseInformation, provider.getCoinbaseInformation(hash));

        provider.save();

        verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("coinbaseInformation-" + hash.toString()),
                BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation)
        );
    }

    @Test
    public void getBtcBestBlockHashByHeight_beforeRskip199() {
        Repository repository = mock(Repository.class);
        int blockHeight = 100;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertFalse(hashOptional.isPresent());
    }

    @Test
    public void getBtcBestBlockHashByHeight_afterRskip199_hashNotFound() {
        Repository repository = mock(Repository.class);
        int blockHeight = 100;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertFalse(hashOptional.isPresent());
    }

    @Test
    public void getBtcBestBlockHashByHeight_afterRskip199() {
        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);
        when(repository.getStorageBytes(any(), any())).thenReturn(serializedHash);

        int blockHeight = 100;
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertTrue(hashOptional.isPresent());
        Assertions.assertEquals(blockHash, hashOptional.get());
    }

    @Test
    public void saveBtcBlocksIndex_beforeRskip199() throws IOException {
        int blockHeight = 100;
        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsBeforeFork
        );

        provider.setBtcBestBlockHashByHeight(blockHeight, blockHash);
        provider.save();

        verify(repository, never()).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            storageKey,
            serializedHash
        );
    }

    @Test
    public void saveBtcBlocksIndex_afterRskip199() throws IOException {
        int blockHeight = 100;
        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activationsAllForks
        );

        provider.setBtcBestBlockHashByHeight(blockHeight, blockHash);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            storageKey,
            serializedHash
        );
    }

    @Test
    public void getActiveFederationCreationBlockHeight_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activationsBeforeFork
        );

        assertEquals(Optional.empty(), provider0.getActiveFederationCreationBlockHeight());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("activeFedCreationBlockHeight"));
    }

    @Test
    public void getActiveFederationCreationBlockHeight_after_fork() {
        Repository repository = mock(Repository.class);
        // If by chance the repository is called I want to force the tests to fail
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("activeFedCreationBlockHeight"))).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        assertEquals(Optional.of(1L), provider0.getActiveFederationCreationBlockHeight());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("activeFedCreationBlockHeight"));
    }

    @Test
    public void setActiveFederationCreationBlockHeightAndGetActiveFederationCreationBlockHeight() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // We store the value
        provider0.setActiveFederationCreationBlockHeight(1L);
        provider0.saveActiveFederationCreationBlockHeight();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // And then we get it back
        MatcherAssert.assertThat(provider.getActiveFederationCreationBlockHeight(), is(Optional.of(1L)));
    }

    @Test
    public void saveActiveFederationCreationBlockHeight_after_RSKIP186() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks
        );

        provider0.setActiveFederationCreationBlockHeight(10L);
        provider0.saveActiveFederationCreationBlockHeight();

        // Once the network upgrade is active, we will store it in the repository
        verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("activeFedCreationBlockHeight"),
                BridgeSerializationUtils.serializeLong(10L)
        );
    }

    @Test
    public void saveActiveFederationCreationBlockHeight_before_RSKIP186() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider0.setActiveFederationCreationBlockHeight(10L);
        provider0.saveActiveFederationCreationBlockHeight();

        // If the network upgrade is not enabled we shouldn't be saving to the repository
        verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("activeFedCreationBlockHeight")),
                any()
        );
    }

    @Test
    public void getNextFederationCreationBlockHeight_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        assertEquals(Optional.empty(), provider0.getNextFederationCreationBlockHeight());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("nextFedCreationBlockHeight"));
    }

    @Test
    public void getNextFederationCreationBlockHeight_after_fork() {
        Repository repository = mock(Repository.class);
        // If by chance the repository is called I want to force the tests to fail
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("nextFedCreationBlockHeight"))).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        assertEquals(Optional.of(1L), provider0.getNextFederationCreationBlockHeight());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("nextFedCreationBlockHeight"));
    }

    @Test
    public void setNextFederationCreationBlockHeightAndGetNextFederationCreationBlockHeight() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // We store the value
        provider0.setNextFederationCreationBlockHeight(1L);
        provider0.saveNextFederationCreationBlockHeight();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // And then we get it back
        MatcherAssert.assertThat(provider.getNextFederationCreationBlockHeight(), is(Optional.of(1L)));
    }

    @Test
    public void saveNextFederationCreationBlockHeight_after_RSKIP186() {
        Repository repository1 = mock(Repository.class);

        BridgeStorageProvider provider1 = new BridgeStorageProvider(
                repository1, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        provider1.setNextFederationCreationBlockHeight(10L);
        provider1.saveNextFederationCreationBlockHeight();

        // Once the network upgrade is active, we will store it in the repository
        verify(repository1, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("nextFedCreationBlockHeight"),
                BridgeSerializationUtils.serializeLong(10L)
        );

        Repository repository2 = mock(Repository.class);

        BridgeStorageProvider provider2 = new BridgeStorageProvider(
                repository2, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        provider2.clearNextFederationCreationBlockHeight();
        provider2.saveNextFederationCreationBlockHeight();

        // Once the network upgrade is active, we will store it in the repository
        verify(repository2, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("nextFedCreationBlockHeight"),
                null
        );
    }

    @Test
    public void isFlyoverFederationDerivationHashUsed_afterRSKIP176_returnTrue() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        when(repository.getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash.toString() + derivationHash.toString()))
        ).thenReturn(new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST});

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertTrue(result);
    }

    @Test
    public void isFlyoverFederationDerivationHashUsed_beforeRSKIP176_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    public void isFlyoverFederationDerivationHashUsed_storageReturnsNull_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash.toString() + derivationHash.toString()))
        ).thenReturn(null);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    public void isFlyoverFederationDerivationHashUsed_storageReturnsEmpty_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash.toString() + derivationHash.toString()))
        ).thenReturn(new byte[]{});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    public void isFlyoverFederationDerivationHashUsed_storageReturnsWrongValue_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash.toString() + derivationHash.toString()))
        ).thenReturn(new byte[]{(byte) 0});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    public void saveNextFederationCreationBlockHeight_before_RSKIP186() {
        Repository repository1 = mock(Repository.class);

        BridgeStorageProvider provider1 = new BridgeStorageProvider(
                repository1, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider1.setNextFederationCreationBlockHeight(10L);
        provider1.saveNextFederationCreationBlockHeight();

        // If the network upgrade is not enabled we shouldn't be saving to the repository
        verify(repository1, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("nextFedCreationBlockHeight")),
                any()
        );

        Repository repository2 = mock(Repository.class);

        BridgeStorageProvider provider2 = new BridgeStorageProvider(
                repository2, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider2.clearNextFederationCreationBlockHeight();
        provider2.saveNextFederationCreationBlockHeight();

        // If the network upgrade is not enabled we shouldn't be saving to the repository
        verify(repository2, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("nextFedCreationBlockHeight")),
                any()
        );
    }

    @Test
    public void getLastRetiredFederationP2SHScript_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        assertEquals(Optional.empty(), provider0.getLastRetiredFederationP2SHScript());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lastRetiredFedP2SHScript"));
    }

    @Test
    public void getLastRetiredFederationP2SHScript_after_fork() {
        Repository repository = mock(Repository.class);
        Script script = new Script(new byte[] {});
        // If by chance the repository is called I want to force the tests to fail
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lastRetiredFedP2SHScript")))
                .thenReturn(BridgeSerializationUtils.serializeScript(script));

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        assertEquals(Optional.of(script), provider0.getLastRetiredFederationP2SHScript());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("lastRetiredFedP2SHScript"));
    }

    @Test
    public void setLastRetiredFederationP2SHScriptAndGetLastRetiredFederationP2SHScript() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();
        Script script = new Script(new byte[] {});

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // We store the value
        provider0.setLastRetiredFederationP2SHScript(script);
        provider0.saveLastRetiredFederationP2SHScript();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        // And then we get it back
        MatcherAssert.assertThat(provider.getLastRetiredFederationP2SHScript(), is(Optional.of(script)));
    }

    @Test
    public void saveLastRetiredFederationP2SHScript_after_RSKIP186() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks
        );

        Script script = new Script(new byte[]{});

        provider0.setLastRetiredFederationP2SHScript(script);
        provider0.saveLastRetiredFederationP2SHScript();

        // Once the network upgrade is active, we will store it in the repository
        verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("lastRetiredFedP2SHScript"),
                BridgeSerializationUtils.serializeScript(script)
        );
    }

    @Test
    public void saveLastRetiredFederationP2SHScript_before_RSKIP186() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(),
                activationsBeforeFork
        );

        Script script = new Script(new byte[]{});

        provider0.setLastRetiredFederationP2SHScript(script);
        provider0.saveLastRetiredFederationP2SHScript();

        // If the network upgrade is not enabled we shouldn't be saving to the repository
        verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("lastRetiredFedP2SHScript")),
                any()
        );
    }

    @Test
    public void saveDerivationArgumentsScriptHash_afterRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);

        provider.save();

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash),
            new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST}
        );
        verifyNoMoreInteractions(repository);
    }

    @Test
    public void saveDerivationArgumentsScriptHash_afterRSKIP176_nullBtcTxHash_notSaved() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(null, derivationHash);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    public void saveDerivationArgumentsScriptHash_afterRSKIP176_nullDerivationHash_notSaved()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash btcTxHash = PegTestUtils.createHash(1);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, null);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    public void saveDerivationArgumentsScriptHash_beforeRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);

        provider.save();

        verify(repository, never()).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash),
            new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST}
        );
    }

    @Test
    public void getFlyoverFederationInformation_afterRSKIP176_ok() {
        Repository repository = mock(Repository.class);

        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte) 0xbb};
        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        byte[] federationRedeemScriptHash = new byte[]{(byte) 0xaa};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            derivationHash,
            federationRedeemScriptHash,
            flyoverFederationRedeemScriptHash
        );

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        Optional <FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);

        Assertions.assertTrue((result.isPresent()));
        Assertions.assertArrayEquals(federationRedeemScriptHash, result.get().getFederationRedeemScriptHash());
        Assertions.assertArrayEquals(derivationHash.getBytes(), result.get().getDerivationHash().getBytes());
        Assertions.assertArrayEquals(flyoverFederationRedeemScriptHash, result.get().getFlyoverFederationRedeemScriptHash());
    }

    @Test
    public void getFlyoverFederationInformation_beforeRSKIP176_ok() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(2);
        byte[] federationRedeemScriptHash = new byte[]{(byte) 0xaa};
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            derivationHash,
            federationRedeemScriptHash,
            flyoverFederationRedeemScriptHash
        );

        lenient().when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    public void getFlyoverFederationInformation_notFound() {
        Repository repository = mock(Repository.class);

        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte) 0xaa};

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(null);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);
        assertFalse(result.isPresent());
    }

    @Test
    public void getFlyoverFederationInformation_nullParameter_returnEmpty() {
        Repository repository = mock(Repository.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(null);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    public void getFlyoverFederationInformation_arrayEmpty_returnEmpty() {
        Repository repository = mock(Repository.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(new byte[]{});
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    public void saveFlyoverFederationInformation_afterRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(2);
        byte[] federationRedeemScriptHash = new byte[]{(byte) 0xaa};
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            derivationHash,
            federationRedeemScriptHash,
            flyoverFederationRedeemScriptHash
        );

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    public void saveFlyoverFederationInformation_beforeRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(2);
        byte[] federationRedeemScriptHash = new byte[]{(byte) 0xaa};
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            derivationHash,
            federationRedeemScriptHash,
            flyoverFederationRedeemScriptHash
        );

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, never()).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    public void saveFlyoverFederationInformation_alreadySet_dont_set_again() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(2);
        byte[] federationRedeemScriptHash = new byte[]{(byte) 0xaa};
        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            derivationHash,
            federationRedeemScriptHash,
            flyoverFederationRedeemScriptHash
        );

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            config.getNetworkConstants().getBridgeConstants(),
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);

        //Set again
        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    public void getReceiveHeadersLastTimestamp_before_RSKIP200() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        assertFalse(provider.getReceiveHeadersLastTimestamp().isPresent());
    }

    @Test
    public void getReceiveHeadersLastTimestamp_after_RSKIP200() {
        Repository repository = mock(Repository.class);

        long actualTimeStamp = System.currentTimeMillis();
        byte[] encodedTimeStamp = RLP.encodeBigInteger(BigInteger.valueOf(actualTimeStamp));
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, DataWord.fromString("receiveHeadersLastTimestamp")))
                .thenReturn(encodedTimeStamp);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        Optional<Long> result = provider.getReceiveHeadersLastTimestamp();

        assertTrue(result.isPresent());
        assertEquals(actualTimeStamp, (long) result.get());
    }

    @Test
    public void getReceiveHeadersLastTimestamp_not_in_repository() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        assertFalse(provider.getReceiveHeadersLastTimestamp().isPresent());
    }

    @Test
    public void saveReceiveHeadersLastTimestamp_before_RSKIP200() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider.setReceiveHeadersLastTimestamp(System.currentTimeMillis());

        provider.save();
        verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("receiveHeadersLastTimestamp")),
                any(byte[].class)
        );
    }

    @Test
    public void saveReceiveHeadersLastTimestamp_after_RSKIP200() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        Long timeInMillis = System.currentTimeMillis();
        provider.setReceiveHeadersLastTimestamp(timeInMillis);

        provider.save();
        verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("receiveHeadersLastTimestamp"),
                BridgeSerializationUtils.serializeLong(timeInMillis)
        );
    }

    @Test
    public void saveReceiveHeadersLastTimestamp_not_set() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        provider.save();
        verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("receiveHeadersLastTimestamp")),
                any(byte[].class)
        );
    }

    @Test
    public void getNextPegoutHeight_before_RSKIP271_activation() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        assertEquals(Optional.empty(), provider.getNextPegoutHeight());

        verify(repository, never()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, NEXT_PEGOUT_HEIGHT_KEY);
    }

    @Test
    public void getNextPegoutHeight_after_RSKIP271_activation() {
        Repository repository = mock(Repository.class);

        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, NEXT_PEGOUT_HEIGHT_KEY)).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        assertEquals(Optional.of(1L), provider.getNextPegoutHeight());

        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, NEXT_PEGOUT_HEIGHT_KEY);
    }

    @Test
    public void setNextPegoutHeightAndGetNextPegoutHeight_after_RSKIP271_activation() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider1 = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        provider1.setNextPegoutHeight(1L);
        provider1.saveNextPegoutHeight();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(
                track, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        MatcherAssert.assertThat(provider2.getNextPegoutHeight(), is(Optional.of(1L)));
    }

    @Test
    public void saveNextPegoutHeight_before_RSKIP271() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork
        );

        provider.setNextPegoutHeight(10L);
        provider.saveNextPegoutHeight();

        verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(DataWord.fromString("nextPegoutHeight")),
                any()
        );
    }

    @Test
    public void saveNextPegoutHeight_after_RSKIP271() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        provider.setNextPegoutHeight(10L);
        provider.saveNextPegoutHeight();

        verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                DataWord.fromString("nextPegoutHeight"),
                BridgeSerializationUtils.serializeLong(10L)
        );
    }

    @Test
    public void getNewFederationBtcUTXOs_before_RSKIP284_before_RSKIP293_testnet() throws IOException {
        testGetNewFederationBtcUTXOs(false, false, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_before_RSKIP284_before_RSKIP293_mainnet() throws IOException {
        testGetNewFederationBtcUTXOs(false, false, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_before_RSKIP284_after_RSKIP293_testnet() throws IOException {
        testGetNewFederationBtcUTXOs(false, true, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_before_RSKIP284_after_RSKIP293_mainnet() throws IOException {
        testGetNewFederationBtcUTXOs(false, true, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_after_RSKIP284_before_RSKIP293_testnet() throws IOException {
        testGetNewFederationBtcUTXOs(true, false, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_after_RSKIP284_before_RSKIP293_mainnet() throws IOException {
        testGetNewFederationBtcUTXOs(true, false, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_after_RSKIP284_after_RSKIP293_testnet() throws IOException {
        testGetNewFederationBtcUTXOs(true, true, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void getNewFederationBtcUTXOs_after_RSKIP284_after_RSKIP293_mainnet() throws IOException {
        testGetNewFederationBtcUTXOs(true, true, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void saveNewFederationBtcUTXOs_no_data() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeTestNetConstants.getInstance(),
            activations
        );

        provider.saveNewFederationBtcUTXOs();

        verify(repository, times(0)).addStorageBytes(
            eq(PrecompiledContracts.BRIDGE_ADDR),
            eq(NEW_FEDERATION_BTC_UTXOS_KEY),
            any()
        );
    }

    @Test
    public void saveNewFederationBtcUTXOs_before_RSKIP284_activation_testnet() throws IOException {
        testSaveNewFederationBtcUTXOs(false, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void saveNewFederationBtcUTXOs_after_RSKIP284_activation_testnet() throws IOException {
        testSaveNewFederationBtcUTXOs(true, NetworkParameters.ID_TESTNET);
    }

    @Test
    public void saveNewFederationBtcUTXOs_before_RSKIP284_activation_mainnet() throws IOException {
        testSaveNewFederationBtcUTXOs(false, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void saveNewFederationBtcUTXOs_after_RSKIP284_activation_mainnet() throws IOException {
        testSaveNewFederationBtcUTXOs(true, NetworkParameters.ID_MAINNET);
    }

    @Test
    public void getReleaseRequestQueueSize_when_releaseRequestQueue_is_null() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        Assertions.assertEquals(0, storageProvider.getReleaseRequestQueueSize());
    }

    @Test
    public void getReleaseRequestQueueSize_when_releaseRequestQueue_is_not_null() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                config.getNetworkConstants().getBridgeConstants(), activationsAllForks
        );

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN,
                PegTestUtils.createHash3(0));

        releaseRequestQueue.add(Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN,
                PegTestUtils.createHash3(1));

        Assertions.assertEquals(2, storageProvider.getReleaseRequestQueueSize());
    }

    private void testGetOldFederation(Federation oldFederation, ForBlock activations) {
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider =
            new BridgeStorageProvider(
                repositoryMock,
                mockAddress("aabbccdd"),
                bridgeConstants,
                activations
            );

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters networkParameters = invocation.getArgument(1);
                // Make sure we're deserializing what just came from the repo with the correct BTC context
                assertArrayEquals(new byte[]{(byte) 0xaa}, data);
                Assertions.assertEquals(networkParameters, bridgeConstants.getBtcParams());
                return oldFederation;
            });

            Assertions.assertEquals(oldFederation, storageProvider.getOldFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    private void testSaveOldFederation(Federation oldFederation, int version) {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            useOriginalIntegerSerialization(bridgeSerializationUtilsMocked);

            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
                Federation federation = invocation.getArgument(0);
                Assertions.assertEquals(oldFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                    Assertions.assertEquals(BigInteger.valueOf(version), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("oldFederation"), address);
                    assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.saveOldFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setOldFederation(oldFederation);
            storageProvider.saveOldFederation();
            Assertions.assertEquals(2, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    private void testGetNewFederationPostMultiKey(Federation federation, ForBlock activations) {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        BridgeStorageProvider storageProvider =
            new BridgeStorageProvider(
                repositoryMock,
                mockAddress("aabbccdd"),
                bridgeConstants,
                activations
            );

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(DataWord.fromString("newFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters networkParameters = invocation.getArgument(1);
                // Make sure we're deserializing what just came from the repo with the correct BTC context
                assertArrayEquals(new byte[]{(byte) 0xaa}, data);
                Assertions.assertEquals(networkParameters, bridgeConstants.getBtcParams());
                return federation;
            });


            Assertions.assertEquals(federation, storageProvider.getNewFederation());
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    private void testSaveNewFederationPostMultiKey(Federation newFederation, int version) {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider =
            new BridgeStorageProvider(
                repositoryMock,
                mockAddress("aabbccdd"),
                config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks
            );

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            useOriginalIntegerSerialization(bridgeSerializationUtilsMocked);

            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
                Federation federation = invocation.getArgument(0);
                Assertions.assertEquals(newFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                    Assertions.assertEquals(BigInteger.valueOf(version), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertArrayEquals(Hex.decode("aabbccdd"), contractAddress.getBytes());
                    Assertions.assertEquals(DataWord.fromString("newFederation"), address);
                    assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            storageProvider.saveNewFederation();
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            storageProvider.setNewFederation(newFederation);
            storageProvider.saveNewFederation();
            Assertions.assertEquals(2, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    private void testGetNewFederationBtcUTXOs(boolean isRskip284Active, boolean isRskip293Active, String networkId) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = networkId.equals(NetworkParameters.ID_MAINNET) ?
            BridgeMainNetConstants.getInstance() :
            BridgeTestNetConstants.getInstance();

        Repository repository = mock(Repository.class);
        List<UTXO> federationUtxos = Arrays.asList(
            PegTestUtils.createUTXO(1, 0, Coin.COIN),
            PegTestUtils.createUTXO(2, 2, Coin.COIN.divide(2)),
            PegTestUtils.createUTXO(3, 0, Coin.COIN.multiply(3))
        );
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_BTC_UTXOS_KEY
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxos));

        List<UTXO> federationUtxosAfterRskip284Activation = Arrays.asList(
            PegTestUtils.createUTXO(4, 0, Coin.FIFTY_COINS),
            PegTestUtils.createUTXO(5, 2, Coin.COIN.multiply(2))
        );
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskip284Activation));

        List<UTXO> federationUtxosAfterRskip293Activation = Arrays.asList(
            PegTestUtils.createUTXO(6, 1, Coin.valueOf(150_000)),
            PegTestUtils.createUTXO(7, 3, Coin.COIN.multiply(3))
        );
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskip293Activation));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        List<UTXO> obtainedUtxos = provider.getNewFederationBtcUTXOs();

        if (networkId.equals(NetworkParameters.ID_TESTNET) && (isRskip284Active || isRskip293Active)) {
            if (isRskip293Active) {
                Assertions.assertEquals(federationUtxosAfterRskip293Activation, obtainedUtxos);
            } else {
                Assertions.assertEquals(federationUtxosAfterRskip284Activation, obtainedUtxos);
            }
        } else {
            Assertions.assertEquals(federationUtxos, obtainedUtxos);
        }
    }

    private void testSaveNewFederationBtcUTXOs(boolean isRskip284Active, String networkId) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        BridgeConstants bridgeConstants = networkId.equals(NetworkParameters.ID_MAINNET) ?
            BridgeMainNetConstants.getInstance() :
            BridgeTestNetConstants.getInstance();

        Repository repository = mock(Repository.class);
        List<UTXO> federationUtxos = Arrays.asList(
            PegTestUtils.createUTXO(1, 0, Coin.COIN),
            PegTestUtils.createUTXO(2, 2, Coin.COIN.divide(2)),
            PegTestUtils.createUTXO(3, 0, Coin.COIN.multiply(3))
        );
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_BTC_UTXOS_KEY
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxos));

        List<UTXO> federationUtxosAfterRskipActivation = Arrays.asList(
            PegTestUtils.createUTXO(4, 0, Coin.FIFTY_COINS),
            PegTestUtils.createUTXO(5, 2, Coin.COIN.multiply(2))
        );
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskipActivation));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        provider.getNewFederationBtcUTXOs(); // Ensure there are elements in the UTXOs list
        provider.saveNewFederationBtcUTXOs();

        if (isRskip284Active && networkId.equals(NetworkParameters.ID_TESTNET)) {
            verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(NEW_FEDERATION_BTC_UTXOS_KEY),
                any()
            );
            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP,
                BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskipActivation)
            );
        } else {
            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                NEW_FEDERATION_BTC_UTXOS_KEY,
                BridgeSerializationUtils.serializeUTXOList(federationUtxos)
            );
            verify(repository, never()).addStorageBytes(
                eq(PrecompiledContracts.BRIDGE_ADDR),
                eq(NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP),
                any()
            );
        }
    }

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }

    private RskAddress mockAddress(String addr) {
        RskAddress mock = mock(RskAddress.class);
        when(mock.getBytes()).thenReturn(Hex.decode(addr));
        return mock;
    }

    private Address getBtcAddress(String addr) {
        return new Address(config.getNetworkConstants().getBridgeConstants().getBtcParams(), Hex.decode(addr));
    }

    private Federation buildMockFederation(Integer... pks) {
        return new Federation(
                FederationTestUtils.getFederationMembersFromPks(pks),
                Instant.ofEpochMilli(1000),
                0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
    }

    private PendingFederation buildMockPendingFederation(Integer... pks) {
        return new PendingFederation(FederationTestUtils.getFederationMembersFromPks(pks));
    }

    private void useOriginalIntegerSerialization(MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked) {
        bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeInteger(any(Integer.class))).thenCallRealMethod();
        bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeInteger(any(byte[].class))).thenCallRealMethod();
    }

    private static Repository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie(trieStore))));
    }
}
