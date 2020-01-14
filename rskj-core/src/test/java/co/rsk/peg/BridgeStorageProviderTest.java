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
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.TopRepository;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/7/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ BridgeSerializationUtils.class, RskAddress.class })
public class BridgeStorageProviderTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig.ForBlock activationsBeforeFork = ActivationConfigsForTest.genesis().forBlock(0L);
    private final ActivationConfig.ForBlock activationsAllForks = ActivationConfigsForTest.all().forBlock(0);
    private final NetworkParameters networkParameters = config.getNetworkConstants().getBridgeConstants().getBtcParams();

    private int transactionOffset;

    @Test
    public void createInstance() throws IOException {
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assert.assertNotNull(releaseRequestQueue);
        Assert.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assert.assertNotNull(releaseTransactionSet);
        Assert.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());
    }

    @Test
    public void createSaveAndRecreateInstance() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);
        provider0.getBtcTxHashesAlreadyProcessed();
        provider0.getReleaseRequestQueue();
        provider0.getReleaseTransactionSet();
        provider0.getRskTxsWaitingForSignatures();
        provider0.getNewFederationBtcUTXOs();
        provider0.getOldFederationBtcUTXOs();
        provider0.save();
        track.commit();

        track = repository.startTracking();

        RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

        Assert.assertThat(repository.isContract(contractAddress), is(true));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("btcTxHashesAP".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("releaseRequestQueue".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("releaseTransactionSet".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("rskTxsWaitingFS".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("newFederationBtcUTXOs".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, DataWord.valueOf("oldFederationBtcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assert.assertNotNull(releaseRequestQueue);
        Assert.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assert.assertNotNull(releaseTransactionSet);
        Assert.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> newUtxos = provider.getNewFederationBtcUTXOs();

        Assert.assertNotNull(newUtxos);
        Assert.assertTrue(newUtxos.isEmpty());

        List<UTXO> oldUtxos = provider.getOldFederationBtcUTXOs();

        Assert.assertNotNull(oldUtxos);
        Assert.assertTrue(oldUtxos.isEmpty());
    }

    @Test
    public void createSaveAndRecreateInstanceWithProcessedHashes() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash1, 1L);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();
        Set<Sha256Hash> processedHashes = processed.keySet();

        Assert.assertTrue(processedHashes.contains(hash1));
        Assert.assertTrue(processedHashes.contains(hash2));
    }

    @Test
    public void createSaveAndRecreateInstanceWithTxsWaitingForSignatures() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Keccak256 hash1 = PegTestUtils.createHash3();
        Keccak256 hash2 = PegTestUtils.createHash3();
        Keccak256 hash3 = PegTestUtils.createHash3();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);
        provider0.getRskTxsWaitingForSignatures().put(hash1, tx1);
        provider0.getRskTxsWaitingForSignatures().put(hash2, tx2);
        provider0.getRskTxsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);

        Assert.assertTrue(signatures.containsKey(hash1));
        Assert.assertTrue(signatures.containsKey(hash2));
        Assert.assertTrue(signatures.containsKey(hash3));

        Assert.assertEquals(tx1.getHash(), signatures.get(hash1).getHash());
        Assert.assertEquals(tx2.getHash(), signatures.get(hash2).getHash());
        Assert.assertEquals(tx3.getHash(), signatures.get(hash3).getHash());
    }

    @Test
    public void createSaveAndRecreateInstanceWithUTXOS() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assert.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assert.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    @Test
    public void getNewFederation_initialVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation newFederation = buildMockFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("newFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters networkParameters = invocation.getArgument(1);

            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
            return newFederation;
        });

        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getNewFederation_initialVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                // First call is storage version getter
                return new byte[0];
            } else {
                // Second and third calls are actual storage getters
                Assert.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                Assert.assertEquals(DataWord.fromString("newFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(3, storageCalls.size());
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class));
    }

    @Test
    public void getNewFederation_multiKeyVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation newFederation = buildMockFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("newFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters networkParameters = invocation.getArgument(1);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
            return newFederation;
        });

        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getNewFederation_multiKeyVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                // First call is storage version getter
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second and third calls are the actual storage getters
                Assert.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                Assert.assertEquals(DataWord.fromString("newFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(3, storageCalls.size());
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class));
    }

    @Test
    public void saveNewFederation_preMultikey() throws IOException {
        Federation newFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"),
                config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        PowerMockito.when(BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)))
        .then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgument(0);
            Assert.assertEquals(newFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.saveNewFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setNewFederation(newFederation);
        storageProvider.saveNewFederation();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void saveNewFederation_postMultikey() throws IOException {
        Federation newFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), 
                activationsAllForks);

        PowerMockito.when(BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgument(0);
            Assert.assertEquals(newFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            if (storageBytesCalls.size() == 1) {
                // First call is the version setting
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("newFederationFormatVersion"), address);
                Assert.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assert.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("newFederation"), address);
                Assert.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.saveNewFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setNewFederation(newFederation);
        storageProvider.saveNewFederation();
        Assert.assertEquals(2, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void getOldFederation_initialVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation oldFederation = buildMockFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters networkParameters = invocation.getArgument(1);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
            return oldFederation;
        });

        Assert.assertEquals(oldFederation, storageProvider.getOldFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getOldFederation_initialVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                // First call is storage version getter
                return new byte[0];
            } else {
                // Second and third calls are actual storage getters
                Assert.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getOldFederation());
        Assert.assertEquals(null, storageProvider.getOldFederation());
        Assert.assertEquals(3, storageCalls.size());
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class));
    }

    @Test
    public void getOldFederation_multiKeyVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation oldFederation = buildMockFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters networkParameters = invocation.getArgument(1);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(networkParameters, config.getNetworkConstants().getBridgeConstants().getBtcParams());
            return oldFederation;
        });

        Assert.assertEquals(oldFederation, storageProvider.getOldFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getOldFederation_multiKeyVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                // First call is storage version getter
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second and third calls are actual storage getters
                Assert.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getOldFederation());
        Assert.assertEquals(null, storageProvider.getOldFederation());
        Assert.assertEquals(3, storageCalls.size());
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class));
    }

    @Test
    public void saveOldFederation_preMultikey() throws IOException {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        PowerMockito.when(BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class))).then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgument(0);
            Assert.assertEquals(oldFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.fromString("oldFederation"), address);
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.saveOldFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setOldFederation(oldFederation);
        storageProvider.saveOldFederation();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void saveOldFederation_postMultikey() throws IOException {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        PowerMockito.when(BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgument(0);
            Assert.assertEquals(oldFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            if (storageBytesCalls.size() == 1) {
                // First call is the version setting
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                Assert.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assert.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                Assert.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.saveOldFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setOldFederation(oldFederation);
        storageProvider.saveOldFederation();
        Assert.assertEquals(2, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void saveOldFederation_preMultikey_setToNull() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            // Make sure the bytes are set to the correct address in the repo and that what's saved is null
            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
            Assert.assertEquals(DataWord.fromString("oldFederation"), address);
            Assert.assertNull(data);
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        storageProvider.saveOldFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        storageProvider.setOldFederation(null);
        storageProvider.saveOldFederation();
        Assert.assertEquals(1, storageBytesCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializeFederation(any(Federation.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class));
    }

    @Test
    public void saveOldFederation_postMultikey_setToNull() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            if (storageBytesCalls.size() == 1) {
                // First call is the version setting
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("oldFederationFormatVersion"), address);
                Assert.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assert.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is null
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.fromString("oldFederation"), address);
                Assert.assertNull(data);
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        storageProvider.saveOldFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        storageProvider.setOldFederation(null);
        storageProvider.saveOldFederation();
        Assert.assertEquals(2, storageBytesCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializeFederation(any(Federation.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class));
    }

    @Test
    public void getPendingFederation_initialVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializePendingFederationOnlyBtcKeys(any(byte[].class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return pendingFederation;
        });

        Assert.assertEquals(pendingFederation, storageProvider.getPendingFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getPendingFederation_initialVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getPendingFederation());
        Assert.assertEquals(2, storageCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializePendingFederation(any(byte[].class));
    }

    @Test
    public void getPendingFederation_multiKeyVersion() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        PowerMockito.when(BridgeSerializationUtils.deserializePendingFederation(any(byte[].class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            byte[] data = invocation.getArgument(0);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return pendingFederation;
        });

        Assert.assertEquals(pendingFederation, storageProvider.getPendingFederation());
        Assert.assertEquals(2, storageCalls.size());
        Assert.assertEquals(1, deserializeCalls.size());
    }

    @Test
    public void getPendingFederation_multiKeyVersion_nullBytes() throws IOException {
        List<Integer> storageCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                return RLP.encodeBigInteger(BigInteger.valueOf(1234));
            } else {
                // Second call is the actual storage getter
                Assert.assertEquals(2, storageCalls.size());
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                return null;
            }
        });

        Assert.assertEquals(null, storageProvider.getPendingFederation());
        Assert.assertEquals(2, storageCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.deserializePendingFederation(any(byte[].class));
    }

    @Test
    public void savePendingFederation_preMultikey() throws IOException {
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        PowerMockito.when(BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class))).then((InvocationOnMock invocation) -> {
            PendingFederation federation = invocation.getArgument(0);
            Assert.assertEquals(pendingFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.savePendingFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setPendingFederation(pendingFederation);
        storageProvider.savePendingFederation();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void savePendingFederation_preMultikey_setToNull() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
            Assert.assertNull(data);
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        storageProvider.savePendingFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        storageProvider.setPendingFederation(null);
        storageProvider.savePendingFederation();
        Assert.assertEquals(1, storageBytesCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class));
    }

    @Test
    public void savePendingFederation_postMultikey() throws IOException {
        PendingFederation pendingFederation = buildMockPendingFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        PowerMockito.when(BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class))).then((InvocationOnMock invocation) -> {
            PendingFederation federation = invocation.getArgument(0);
            Assert.assertEquals(pendingFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageBytesCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                Assert.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assert.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                Assert.assertTrue(Arrays.equals(new byte[]{(byte) 0xbb}, data));
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.savePendingFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setPendingFederation(pendingFederation);
        storageProvider.savePendingFederation();
        Assert.assertEquals(2, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void savePendingFederation_postMultikey_setToNull() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        useOriginalIntegerSerialization();
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsAllForks);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));

            if (storageBytesCalls.size() == 1) {
                Assert.assertEquals(DataWord.fromString("pendingFederationFormatVersion"), address);
                Assert.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assert.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertEquals(DataWord.fromString("pendingFederation"), address);
                Assert.assertNull(data);
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        storageProvider.savePendingFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        storageProvider.setPendingFederation(null);
        storageProvider.savePendingFederation();
        Assert.assertEquals(2, storageBytesCalls.size());

        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializePendingFederationOnlyBtcKeys(any(PendingFederation.class));
        PowerMockito.verifyStatic(never());
        BridgeSerializationUtils.serializePendingFederation(any(PendingFederation.class));
    }

    @Test
    public void getFederationElection_nonNullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeElection(any(byte[].class), any(AddressBasedAuthorizer.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgument(0);
            AddressBasedAuthorizer authorizer = invocation.getArgument(1);
            // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(authorizerMock, authorizer);
            return electionMock;
        });

        Assert.assertSame(electionMock, storageProvider.getFederationElection(authorizerMock));
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
    }

    @Test
    public void getFederationElection_nullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeElection(any(byte[].class), any(AddressBasedAuthorizer.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            return null;
        });

        ABICallElection result = storageProvider.getFederationElection(authorizerMock);
        Assert.assertSame(authorizerMock, Whitebox.getInternalState(result, "authorizer"));
        Assert.assertEquals(0, result.getVotes().size());
        Assert.assertEquals(1, calls.size()); // getStorageBytes is the only one called (can't be the other way around)
    }

    @Test
    public void saveFederationElection() throws IOException {
        ABICallElection electionMock = mock(ABICallElection.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        PowerMockito.when(BridgeSerializationUtils.serializeElection(any(ABICallElection.class))).then((InvocationOnMock invocation) -> {
            ABICallElection election = invocation.getArgument(0);
            Assert.assertSame(electionMock, election);
            serializeCalls.add(0);
            return Hex.decode("aabb");
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            Assert.assertTrue(Arrays.equals(Hex.decode("aabb"), data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        storageProvider.saveFederationElection();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        Whitebox.setInternalState(storageProvider, "federationElection", electionMock);
        storageProvider.saveFederationElection();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @Test
    public void getLockWhitelist_nonNullBytes() {
        List<Integer> calls = new ArrayList<>();
        LockWhitelist whitelistMock = new LockWhitelist(new HashMap<>());
        LockWhitelistEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);
        LockWhitelistEntry unlimitedEntry = new UnlimitedWhiteListEntry(getBtcAddress("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        whitelistMock.put(oneOffEntry.address(), oneOffEntry);
        whitelistMock.put(unlimitedEntry.address(), unlimitedEntry);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
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
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    RskAddress contractAddress = invocation.getArgument(0);
                    DataWord address = invocation.getArgument(1);
                    // Make sure the bytes are got from the correct address in the repo
                    Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                    Assert.assertEquals(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                    return new byte[]{(byte)0xbb};
                });
        PowerMockito
            .when(BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters parameters = invocation.getArgument(1);
                Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
                // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
                HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                map.put(oneOffEntry.address(), oneOffEntry);
                return Pair.of(map, 0);
        });
        PowerMockito
                .when(BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    byte[] unlimitedData = invocation.getArgument(0);
                    NetworkParameters parameters = invocation.getArgument(1);
                    Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
                    // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                    Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, unlimitedData));
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(unlimitedEntry.address(), unlimitedEntry);
                    return map;
                });

        Assert.assertEquals(whitelistMock.getAll(), storageProvider.getLockWhitelist().getAll());
        Assert.assertEquals(4, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes (we call getStorageBytes twice)
    }

    @Test
    public void getLockWhitelist_nullBytes() {
        List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                // Make sure the bytes are got from the correct address in the repo
                Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                Assert.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                return new byte[]{(byte)0xee};
            });
        PowerMockito
            .when(BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                return null;
            });
        PowerMockito
                .when(BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0); // THIS ONE WON'T BE CALLED BECAUSE ONEOFF IS EMPTY
                    Assert.fail("As we don't have data for one-off, we shouldn't have called deserialize unlimited");
                    return null;
                });

        LockWhitelist result = storageProvider.getLockWhitelist();
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getSize().intValue());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
    }

    @Test
    public void saveLockWhitelist() {
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        // Overriding activation to make sure it serializes the unlimited whitelist data
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                activationsAllForks);

        // Mock the One-Off serialization
        PowerMockito
            .when(BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
            .then((InvocationOnMock invocation) -> {
                Pair<List<OneOffWhiteListEntry>, Integer> data = invocation.getArgument(0);
                Assert.assertEquals(whitelistMock.getAll(OneOffWhiteListEntry.class), data.getLeft());
                Assert.assertSame(whitelistMock.getDisableBlockHeight(), data.getRight());
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
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                Assert.assertTrue(Arrays.equals(Hex.decode("ccdd"), data));
                return null;
            })
            .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

        // Mock the Unlimited serialization
        PowerMockito
            .when(BridgeSerializationUtils.serializeUnlimitedLockWhitelist(any(List.class)))
            .then((InvocationOnMock invocation) -> {
                List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = invocation.getArgument(0);
                Assert.assertEquals(whitelistMock.getAll(UnlimitedWhiteListEntry.class), unlimitedWhiteListEntries);
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
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                Assert.assertTrue(Arrays.equals(Hex.decode("bbcc"), data));
                return null;
            })
            .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

        storageProvider.saveLockWhitelist();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        Whitebox.setInternalState(storageProvider, "lockWhitelist", whitelistMock);
        storageProvider.saveLockWhitelist();
        Assert.assertEquals(2, storageBytesCalls.size());
        Assert.assertEquals(2, serializeCalls.size());
    }

    @Test
    public void saveLockWhiteListAfterGetWithData() {
        AtomicReference<Boolean> storageCalled = new AtomicReference<>();
        storageCalled.set(Boolean.FALSE);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        OneOffWhiteListEntry oneOffEntry = new OneOffWhiteListEntry(getBtcAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Coin.COIN);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig().forBlock(500L));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> new byte[]{(byte)0xaa});

        PowerMockito
                .when(BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(oneOffEntry.address(), oneOffEntry);
                    return Pair.of(map, 0);
                });

        PowerMockito
                .when(BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
                .thenReturn(new byte[]{(byte)0xee});

        Mockito
                .doAnswer((InvocationOnMock invocation) -> {
                    storageCalled.set(Boolean.TRUE);
                    return null;
                })
                .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(DataWord.valueOf("lockWhitelist".getBytes(StandardCharsets.UTF_8))), eq(new byte[]{(byte)0xee}));

        Assert.assertTrue(storageProvider.getLockWhitelist().getSize() > 0);

        storageProvider.saveLockWhitelist();

        Assert.assertTrue(storageCalled.get());
    }

    @Test
    public void getReleaseRequestQueue() throws IOException {
        List<Integer> calls = new ArrayList<>();
        ReleaseRequestQueue requestQueueMock = mock(ReleaseRequestQueue.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("releaseRequestQueue".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeReleaseRequestQueue(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters parameters = invocation.getArgument(1);
            Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
            // Make sure we're deserializing what just came from the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return requestQueueMock;
        });

        Assert.assertSame(requestQueueMock, storageProvider.getReleaseRequestQueue());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
    }

    @Test
    public void getReleaseTransactionSet() throws IOException {
        List<Integer> calls = new ArrayList<>();
        ReleaseTransactionSet transactionSetMock = mock(ReleaseTransactionSet.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getNetworkConstants().getBridgeConstants(), activationsBeforeFork);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(DataWord.valueOf("releaseTransactionSet".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeReleaseTransactionSet(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgument(0);
            NetworkParameters parameters = invocation.getArgument(1);
            Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
            // Make sure we're deserializing what just came from the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return transactionSetMock;
        });

        Assert.assertSame(transactionSetMock, storageProvider.getReleaseTransactionSet());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederationOnlyBtcKeys & getStorageBytes
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

        assertThat(provider.getFeePerKb(), is(expectedCoin));
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
        when(authorizerMock.getRequiredAuthorizedKeys())
                .thenReturn(1);

        ABICallElection result = storageProvider.getFeePerKbElection(authorizerMock);
        assertThat(result.getVotes().isEmpty(), is(true));
        assertThat(result.getWinner(), nullValue());
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
        assertThat(result.getVotes(), is(electionVotes));
        assertThat(result.getWinner(), is(expectedWinner));
    }

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }

    private RskAddress mockAddress(String addr) {
        RskAddress mock = PowerMockito.mock(RskAddress.class);
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

    private void useOriginalIntegerSerialization() {
        PowerMockito.when(BridgeSerializationUtils.serializeInteger(any(Integer.class))).thenCallRealMethod();
        PowerMockito.when(BridgeSerializationUtils.deserializeInteger(any(byte[].class))).thenCallRealMethod();
    }

    private static Repository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new TopRepository(new Trie(trieStore), trieStore);
    }
}
