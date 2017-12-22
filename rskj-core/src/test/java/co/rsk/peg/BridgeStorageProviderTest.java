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
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.db.RepositoryImpl;
import org.ethereum.core.Repository;
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
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 6/7/2016.
 */
@RunWith(PowerMockRunner.class)
public class BridgeStorageProviderTest {
    private NetworkParameters networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();
    private int transactionOffset;

    @Test
    public void createInstance() throws IOException {
        Repository repository = new RepositoryImpl();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assert.assertNotNull(releaseRequestQueue);
        Assert.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assert.assertNotNull(releaseTransactionSet);
        Assert.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());
    }

    @Test
    public void createSaveAndRecreateInstance() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcTxHashesAlreadyProcessed();
        provider0.getReleaseRequestQueue();
        provider0.getReleaseTransactionSet();
        provider0.getRskTxsWaitingForSignatures();
        provider0.getNewFederationBtcUTXOs();
        provider0.getOldFederationBtcUTXOs();
        provider0.save();
        track.commit();

        track = repository.startTracking();

        byte[] contractAddress = Hex.decode(PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertNotNull(repository.getContractDetails(contractAddress));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcTxHashesAP".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("releaseRequestQueue".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("releaseTransactionSet".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFS".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("newFederationBtcUTXOs".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("oldFederationBtcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();

        Assert.assertNotNull(releaseRequestQueue);
        Assert.assertEquals(0, releaseRequestQueue.getEntries().size());

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();

        Assert.assertNotNull(releaseTransactionSet);
        Assert.assertEquals(0, releaseTransactionSet.getEntries().size());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

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

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash1, 1L);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

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
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getRskTxsWaitingForSignatures().put(hash1, tx1);
        provider0.getRskTxsWaitingForSignatures().put(hash2, tx2);
        provider0.getRskTxsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

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

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.getNewFederationBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        List<UTXO> utxos = provider.getNewFederationBtcUTXOs();

        Assert.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assert.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getNewFederation() throws IOException {
        List<Integer> calls = new ArrayList<>();
        Context contextMock = mock(Context.class);
        Federation newFederation = new Federation(Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");
        Whitebox.setInternalState(storageProvider, "btcContext", contextMock);

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are get from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(Context.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            Context btcContext = invocation.getArgumentAt(1, Context.class);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(contextMock, btcContext);
            return newFederation;
        });

        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(newFederation, storageProvider.getNewFederation());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getNewFederation_nullBytes() throws IOException {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Context contextMock = mock(Context.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");
        Whitebox.setInternalState(storageProvider, "btcContext", contextMock);

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are get from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(Context.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            return null;
        });

        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(null, storageProvider.getNewFederation());
        Assert.assertEquals(2, storageBytesCalls.size()); // 2 for the calls to getStorageBytes
        Assert.assertEquals(0, deserializeCalls.size()); // 2 for the calls to getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void saveNewFederation() throws IOException {
        Federation newFederation = new Federation(Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        PowerMockito.when(BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
            Federation federation = invocation.getArgumentAt(0, Federation.class);
            Assert.assertEquals(newFederation, federation);
            serializeCalls.add(0);
            return new byte[]{(byte)0xbb};
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            byte[] data = invocation.getArgumentAt(2, byte[].class);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("newFederation".getBytes(StandardCharsets.UTF_8)), address);
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xbb}, data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(byte[].class), any(DataWord.class), any(byte[].class));

        storageProvider.saveNewFederation();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        storageProvider.setNewFederation(newFederation);
        storageProvider.saveNewFederation();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getFederationElection_nonNullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
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

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getFederationElection_nullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        AddressBasedAuthorizer authorizerMock = mock(AddressBasedAuthorizer.class);
        ABICallElection electionMock = mock(ABICallElection.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
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

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void saveFederationElection() throws IOException {
        ABICallElection electionMock = mock(ABICallElection.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        PowerMockito.when(BridgeSerializationUtils.serializeElection(any(ABICallElection.class))).then((InvocationOnMock invocation) -> {
            ABICallElection election = invocation.getArgumentAt(0, ABICallElection.class);
            Assert.assertSame(electionMock, election);
            serializeCalls.add(0);
            return Hex.decode("aabb");
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            byte[] data = invocation.getArgumentAt(2, byte[].class);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("federationElection".getBytes(StandardCharsets.UTF_8)), address);
            Assert.assertTrue(Arrays.equals(Hex.decode("aabb"), data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(byte[].class), any(DataWord.class), any(byte[].class));

        storageProvider.saveFederationElection();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        Whitebox.setInternalState(storageProvider, "federationElection", electionMock);
        storageProvider.saveFederationElection();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getLockWhitelist_nonNullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");
        Context contextMock = mock(Context.class);
        when(contextMock.getParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Whitebox.setInternalState(storageProvider, "btcContext", contextMock);

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeLockWhitelist(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            NetworkParameters parameters = invocation.getArgumentAt(1, NetworkParameters.class);
            Assert.assertEquals(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), parameters);
            // Make sure we're deserializing what just came from the repo with the correct AddressBasedAuthorizer
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            return whitelistMock;
        });

        Assert.assertSame(whitelistMock, storageProvider.getLockWhitelist());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getLockWhitelist_nullBytes() throws IOException {
        List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");
        Context contextMock = mock(Context.class);
        when(contextMock.getParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Whitebox.setInternalState(storageProvider, "btcContext", contextMock);

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
            Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeLockWhitelist(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            return null;
        });

        LockWhitelist result = storageProvider.getLockWhitelist();
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getSize().intValue());
        Assert.assertEquals(1, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void saveLockWhitelist() throws IOException {
        LockWhitelist whitelistMock = mock(LockWhitelist.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        PowerMockito.when(BridgeSerializationUtils.serializeLockWhitelist(any(LockWhitelist.class))).then((InvocationOnMock invocation) -> {
            LockWhitelist whitelist = invocation.getArgumentAt(0, LockWhitelist.class);
            Assert.assertSame(whitelistMock, whitelist);
            serializeCalls.add(0);
            return Hex.decode("ccdd");
        });
        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            byte[] data = invocation.getArgumentAt(2, byte[].class);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            Assert.assertTrue(Arrays.equals(Hex.decode("aabbccdd"), contractAddress));
            Assert.assertEquals(new DataWord("lockWhitelist".getBytes(StandardCharsets.UTF_8)), address);
            Assert.assertTrue(Arrays.equals(Hex.decode("ccdd"), data));
            return null;
        }).when(repositoryMock).addStorageBytes(any(byte[].class), any(DataWord.class), any(byte[].class));

        storageProvider.saveLockWhitelist();
        // Shouldn't have tried to save nor serialize anything
        Assert.assertEquals(0, storageBytesCalls.size());
        Assert.assertEquals(0, serializeCalls.size());
        Whitebox.setInternalState(storageProvider, "lockWhitelist", whitelistMock);
        storageProvider.saveLockWhitelist();
        Assert.assertEquals(1, storageBytesCalls.size());
        Assert.assertEquals(1, serializeCalls.size());
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getReleaseRequestQueue() throws IOException {
        List<Integer> calls = new ArrayList<>();
        ReleaseRequestQueue requestQueueMock = mock(ReleaseRequestQueue.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
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

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getReleaseTransactionSet() throws IOException {
        List<Integer> calls = new ArrayList<>();
        ReleaseTransactionSet transactionSetMock = mock(ReleaseTransactionSet.class);
        PowerMockito.mockStatic(BridgeSerializationUtils.class);
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, "aabbccdd");

        when(repositoryMock.getStorageBytes(any(byte[].class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] contractAddress = invocation.getArgumentAt(0, byte[].class);
            DataWord address = invocation.getArgumentAt(1, DataWord.class);
            // Make sure the bytes are got from the correct address in the repo
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd}, contractAddress));
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

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }
}
