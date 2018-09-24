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
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStoreImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 6/7/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ BridgeSerializationUtils.class, RskAddress.class })
public class BridgeStorageProviderTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final NetworkParameters networkParameters = config.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();
    private final BridgeStorageConfiguration bridgeStorageConfigurationAtHeightZero = BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(0));

    private int transactionOffset;

    @Test
    public void createInstance() throws IOException {
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);
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

        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcTxHashesAP".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("releaseRequestQueue".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("releaseTransactionSet".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFS".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("newFederationBtcUTXOs".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("oldFederationBtcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash1, 1L);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);
        provider0.getRskTxsWaitingForSignatures().put(hash1, tx1);
        provider0.getRskTxsWaitingForSignatures().put(hash2, tx2);
        provider0.getRskTxsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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

        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assert.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assert.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    @Test
    public void getNewFederation() throws IOException {
        List<Integer> calls = new ArrayList<>();
        Context contextMock = mock(Context.class);
        Federation newFederation = new Federation(Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are get from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            NetworkParameters networkParameters = invocation.getArgumentAt(1, NetworkParameters.class);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(networkParameters, config.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams());
            return newFederation;
        });

        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @Test
    public void getNewFederation_nullBytes() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are get from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            return null;
        });

        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(2, storageBytesCalls.size()); // 2 for the calls to getStorageBytes
        Assert.assertEquals(0, deserializeCalls.size()); // 2 for the calls to getStorageBytes
    }

    @Test
    public void saveNewFederation() throws IOException {
        Federation newFederation = new Federation(Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        PowerMockito.when(BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgumentAt(0, Federation.class);
            Assert.assertEquals(newFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            byte[] data = invocation.getArgumentAt(2, byte[].class);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
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
    public void getFederationElection_nonNullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeElection(any(byte[].class), any(AddressBasedAuthorizer.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            AddressBasedAuthorizer authorizer = invocation.getArgumentAt(1, AddressBasedAuthorizer.class);
            // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(authorizerMock, authorizer);
            return electionMock;
        });

        Assert.assertSame(electionMock, storageProvider.getFederationElection(authorizerMock));
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @Test
    public void getFederationElection_nullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("federationElection".getBytes(StandardCharsets.UTF_8)), address);
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
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        PowerMockito.when(BridgeSerializationUtils.serializeElection(any(ABICallElection.class))).then((InvocationOnMock invocation) -> {
            ABICallElection election = invocation.getArgumentAt(0, ABICallElection.class);
            Assert.assertSame(electionMock, election);
            serializeCalls.add(0);
            return Hex.decode("aabb");
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            byte[] data = invocation.getArgumentAt(2, byte[].class);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("federationElection".getBytes(StandardCharsets.UTF_8)), address);
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
        // Overriding BridgeStorageConfiguration to make sure it serializes the unlimited whitelist data
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(),
                BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(500)));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)))))
        .then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(new DataWord("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> {
                    calls.add(0);
                    RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
                    DataWord address = invocation.getArgumentAt(1, DataWord.class);
                    // Make sure the bytes are got from the correct address in the repo
                    Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                    Assert.assertEquals(new DataWord("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                    return new byte[]{(byte)0xbb};
                });
        PowerMockito
            .when(BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                byte[] data = invocation.getArgumentAt(0, byte[].class);
                NetworkParameters parameters = invocation.getArgumentAt(1, NetworkParameters.class);
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
                    byte[] unlimitedData = invocation.getArgumentAt(0, byte[].class);
                    NetworkParameters parameters = invocation.getArgumentAt(1, NetworkParameters.class);
                    Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
                    // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
                    Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, unlimitedData));
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(unlimitedEntry.address(), unlimitedEntry);
                    return map;
                });

        Assert.assertEquals(whitelistMock.getAll(), storageProvider.getLockWhitelist().getAll());
        Assert.assertEquals(4, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes (we call getStorageBytes twice)
    }

    @Test
    public void getLockWhitelist_nullBytes() {
        List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(),
                BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(500)));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class)))
            .then((InvocationOnMock invocation) -> {
                calls.add(0);
                RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
                DataWord address = invocation.getArgumentAt(1, DataWord.class);
                // Make sure the bytes are got from the correct address in the repo
                Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
                Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                return null;
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
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @Test
    public void saveLockWhitelist() {
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        // Overriding BridgeStorageConfiguration to make sure it serializes the unlimited whitelist data
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(),
                BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(500)));

        // Mock the One-Off serialization
        PowerMockito
            .when(BridgeSerializationUtils.serializeOneOffLockWhitelist(any(Pair.class)))
            .then((InvocationOnMock invocation) -> {
                Pair<List<OneOffWhiteListEntry>, Integer> data = invocation.getArgumentAt(0, Pair.class);
                Assert.assertEquals(whitelistMock.getAll(OneOffWhiteListEntry.class), data.getLeft());
                Assert.assertSame(whitelistMock.getDisableBlockHeight(), data.getRight());
                serializeCalls.add(0);
                return Hex.decode("ccdd");
            });

        Mockito
            .doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
                DataWord address = invocation.getArgumentAt(1, DataWord.class);
                byte[] data = invocation.getArgumentAt(2, byte[].class);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                Assert.assertTrue(Arrays.equals(Hex.decode("ccdd"), data));
                return null;
            })
            .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

        // Mock the Unlimited serialization
        PowerMockito
            .when(BridgeSerializationUtils.serializeUnlimitedLockWhitelist(any(List.class)))
            .then((InvocationOnMock invocation) -> {
                List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = invocation.getArgumentAt(0, List.class);
                Assert.assertEquals(whitelistMock.getAll(UnlimitedWhiteListEntry.class), unlimitedWhiteListEntries);
                serializeCalls.add(0);
                return Hex.decode("bbcc");
            });

        Mockito
            .doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
                DataWord address = invocation.getArgumentAt(1, DataWord.class);
                byte[] data = invocation.getArgumentAt(2, byte[].class);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress.getBytes()));
                Assert.assertEquals(new DataWord("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
                Assert.assertTrue(Arrays.equals(Hex.decode("bbcc"), data));
                return null;
            })
            .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(new DataWord("unlimitedLockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

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
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(),
                BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(500)));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)))))
                .then((InvocationOnMock invocation) -> new byte[]{(byte)0xaa});

        PowerMockito
                .when(BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(any(byte[].class), any(NetworkParameters.class)))
                .then((InvocationOnMock invocation) -> {
                    HashMap<Address, LockWhitelistEntry> map = new HashMap<>();
                    map.put(oneOffEntry.address(), oneOffEntry);
                    return Pair.of(map, 0);
                });

        Mockito
                .doAnswer((InvocationOnMock invocation) -> {
                    storageCalled.set(Boolean.TRUE);
                    return null;
                })
                .when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8))), any(byte[].class));

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
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("releaseRequestQueue".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeReleaseRequestQueue(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            NetworkParameters parameters = invocation.getArgumentAt(1, NetworkParameters.class);
            Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
            // Make sure we're deserializing what just came from the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return requestQueueMock;
        });

        Assert.assertSame(requestQueueMock, storageProvider.getReleaseRequestQueue());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @Test
    public void getReleaseTransactionSet() throws IOException {
        List<Integer> calls = new ArrayList<>();
        ReleaseTransactionSet transactionSetMock = mock(ReleaseTransactionSet.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            RskAddress contractAddress = invocation.getArgumentAt(0, RskAddress.class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress.getBytes()));
            Assert.assertEquals(new DataWord("releaseTransactionSet".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeReleaseTransactionSet(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            NetworkParameters parameters = invocation.getArgumentAt(1, NetworkParameters.class);
            Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
            // Make sure we're deserializing what just came from the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return transactionSetMock;
        });

        Assert.assertSame(transactionSetMock, storageProvider.getReleaseTransactionSet());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @Test
    public void setFeePerKb_savedAndRecreated() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        Coin expectedCoin = Coin.valueOf(5325);
        provider0.setFeePerKb(expectedCoin);
        provider0.saveFeePerKb();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

        assertThat(provider.getFeePerKb(), is(expectedCoin));
    }

    @Test
    public void getFeePerKbElection_emptyVotes() {
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtHeightZero);

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
        return new Address(config.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams(), Hex.decode(addr));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB()), true))));
    }
}
