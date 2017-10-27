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

import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.core.Repository;
import co.rsk.db.RepositoryImpl;
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

import javax.xml.crypto.Data;
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

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);
        Assert.assertTrue(confirmations.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getActiveFederationBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());

        Wallet wallet = provider.getActiveFederationWallet();

        Assert.assertNotNull(wallet);
        Assert.assertNotNull(wallet.getCoinSelector());
    }

    @Test
    public void createSaveAndRecreateInstance() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcTxHashesAlreadyProcessed();
        provider0.getRskTxsWaitingForConfirmations();
        provider0.getRskTxsWaitingForSignatures();
        provider0.getActiveFederationBtcUTXOs();
        provider0.save();
        track.commit();

        track = repository.startTracking();

        byte[] contractAddress = Hex.decode(PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertNotNull(repository.getContractDetails(contractAddress));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcTxHashesAP".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFC".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFS".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);
        Assert.assertTrue(confirmations.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getActiveFederationBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());

        Wallet wallet = provider.getActiveFederationWallet();

        Assert.assertNotNull(wallet);
        Assert.assertNotNull(wallet.getCoinSelector());
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
    public void createSaveAndRecreateInstanceWithTxsWaitingForConfirmations() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);

        Assert.assertTrue(confirmations.containsKey(hash1));
        Assert.assertTrue(confirmations.containsKey(hash2));
        Assert.assertTrue(confirmations.containsKey(hash3));

        Assert.assertEquals(tx1.getHash(), confirmations.get(hash1).getHash());
        Assert.assertEquals(tx2.getHash(), confirmations.get(hash2).getHash());
        Assert.assertEquals(tx3.getHash(), confirmations.get(hash3).getHash());
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
        provider0.getActiveFederationBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.getActiveFederationBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(federation.getAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        List<UTXO> utxos = provider.getActiveFederationBtcUTXOs();

        Assert.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assert.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getActiveFederation() throws IOException {
        List<Integer> calls = new ArrayList<>();
        Context contextMock = mock(Context.class);
        Federation activeFederation = new Federation(1, Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
            Assert.assertEquals(new DataWord("bridgeActiveFederation".getBytes(StandardCharsets.UTF_8)), address);
            return new byte[]{(byte)0xaa};
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(Context.class))).then((InvocationOnMock invocation) -> {
            calls.add(0);
            byte[] data = invocation.getArgumentAt(0, byte[].class);
            Context btcContext = invocation.getArgumentAt(1, Context.class);
            // Make sure we're deserializing what just came from the repo with the correct BTC context
            Assert.assertTrue(Arrays.equals(new byte[]{(byte)0xaa}, data));
            Assert.assertEquals(contextMock, btcContext);
            return activeFederation;
        });

        Assert.assertEquals(activeFederation, storageProvider.getActiveFederation());
        Assert.assertEquals(activeFederation, storageProvider.getActiveFederation());
        Assert.assertEquals(2, calls.size()); // 1 for each call to deserializeFederation & getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void getActiveFederation_nullBytes() throws IOException {
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
            Assert.assertEquals(new DataWord("bridgeActiveFederation".getBytes(StandardCharsets.UTF_8)), address);
            return null;
        });
        PowerMockito.when(BridgeSerializationUtils.deserializeFederation(any(byte[].class), any(Context.class))).then((InvocationOnMock invocation) -> {
            deserializeCalls.add(0);
            return null;
        });

        Assert.assertEquals(null, storageProvider.getActiveFederation());
        Assert.assertEquals(null, storageProvider.getActiveFederation());
        Assert.assertEquals(2, storageBytesCalls.size()); // 2 for the calls to getStorageBytes
        Assert.assertEquals(0, deserializeCalls.size()); // 2 for the calls to getStorageBytes
    }

    @PrepareForTest({ BridgeSerializationUtils.class })
    @Test
    public void saveNewFederation() throws IOException {
        Federation newFederation = new Federation(1, Arrays.asList(new BtcECKey[]{BtcECKey.fromPrivate(BigInteger.valueOf(100))}), Instant.ofEpochMilli(1000), NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
            Assert.assertEquals(new DataWord("bridgeActiveFederation".getBytes(StandardCharsets.UTF_8)), address);
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

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }
}
