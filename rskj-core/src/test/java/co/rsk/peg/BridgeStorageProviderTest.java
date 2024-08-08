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

import static co.rsk.peg.BridgeStorageIndexKey.*;
import static org.ethereum.TestUtils.mockAddress;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.constants.*;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Created by ajlopez on 6/7/2016.
 */
@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to some setup generalizations
@MockitoSettings(strictness = Strictness.LENIENT)
class BridgeStorageProviderTest {
    private static final byte FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST = (byte) 1;

    private final ActivationConfig.ForBlock activationsBeforeFork = ActivationConfigsForTest.genesis().forBlock(0L);
    private final ActivationConfig.ForBlock activationsAllForks = ActivationConfigsForTest.all().forBlock(0);
    private final NetworkParameters testnetBtcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

    private final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

    private int transactionOffset;

    @Test
    void createInstance() throws IOException {
        Repository repository = createRepository();

        BridgeStorageProvider bridgeStorageProvider = createBridgeStorageProvider(repository, testnetBtcParams, activationsBeforeFork);

        ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();

        Assertions.assertNotNull(releaseRequestQueue);
        Assertions.assertEquals(0, releaseRequestQueue.getEntries().size());

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();

        Assertions.assertNotNull(pegoutsWaitingForConfirmations);
        Assertions.assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());

        SortedMap<Keccak256, BtcTransaction> signatures = bridgeStorageProvider.getPegoutsWaitingForSignatures();

        Assertions.assertNotNull(signatures);
        Assertions.assertTrue(signatures.isEmpty());
    }

    @Test
    void createSaveAndRecreateInstanceWithProcessedHashes() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );
        provider0.setHeightBtcTxhashAlreadyProcessed(hash1, 1L);
        provider0.setHeightBtcTxhashAlreadyProcessed(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1).isPresent());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(hash2).isPresent());
    }

    @Test
    void createSaveAndRecreateInstanceWithTxsWaitingForSignatures() throws IOException {
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
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );
        provider0.getPegoutsWaitingForSignatures().put(hash1, tx1);
        provider0.getPegoutsWaitingForSignatures().put(hash2, tx2);
        provider0.getPegoutsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            track,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        SortedMap<Keccak256, BtcTransaction> signatures = provider.getPegoutsWaitingForSignatures();

        Assertions.assertNotNull(signatures);

        assertTrue(signatures.containsKey(hash1));
        assertTrue(signatures.containsKey(hash2));
        assertTrue(signatures.containsKey(hash3));

        assertEquals(tx1.getHash(), signatures.get(hash1).getHash());
        assertEquals(tx2.getHash(), signatures.get(hash2).getHash());
        assertEquals(tx3.getHash(), signatures.get(hash3).getHash());
    }

    @Test
    void getReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams, activationsBeforeFork);

        List<ReleaseRequestQueue.Entry> oldEntriesList = new ArrayList<>(Collections.singletonList(
            new ReleaseRequestQueue.Entry(
                Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN)));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey())))
            .then((InvocationOnMock invocation) ->
                BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(oldEntriesList)));

        ReleaseRequestQueue result = storageProvider.getReleaseRequestQueue();

        verify(repositoryMock, never()).getStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE_WITH_TXHASH.getKey()));

        Assertions.assertEquals(1, result.getEntries().size());
        Assertions.assertTrue(result.getEntries().containsAll(oldEntriesList));
    }

    @Test
    void getReleaseRequestQueue_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        ReleaseRequestQueue.Entry oldEntry = new ReleaseRequestQueue.Entry(
            Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
            Coin.COIN);

        ReleaseRequestQueue.Entry newEntry = new ReleaseRequestQueue.Entry(
            Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0)
        );

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(),eq(RELEASE_REQUEST_QUEUE.getKey()))).
            thenReturn(BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Arrays.asList(oldEntry)))));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams,
            activations);

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0));

        ReleaseRequestQueue result = storageProvider.getReleaseRequestQueue();

        Assertions.assertEquals(2, result.getEntries().size());
        Assertions.assertEquals(result.getEntries().get(0), oldEntry);
        Assertions.assertEquals(result.getEntries().get(1), newEntry);
    }

    @Test
    void saveReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams, activationsBeforeFork);

        List<ReleaseRequestQueue.Entry> oldEntriesList = new ArrayList<>(Collections.singletonList(
            new ReleaseRequestQueue.Entry(
                Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN)));

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();
        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
            Coin.COIN);

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), testnetBtcParams);
            Assertions.assertEquals(oldEntriesList, entries);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey()), any(byte[].class));

        storageProvider.saveReleaseRequestQueue();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey()), any(byte[].class));
        verify(repositoryMock, never()).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE_WITH_TXHASH.getKey()), any(byte[].class));
    }

    @Test
    void saveReleaseRequestQueue_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        ReleaseRequestQueue.Entry newEntry =
            new ReleaseRequestQueue.Entry(
                Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN,
                PegTestUtils.createHash3(0)
            );

        ReleaseRequestQueue.Entry oldEntry =
            new ReleaseRequestQueue.Entry(
                Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
                Coin.COIN
            );

        Repository repositoryMock = mock(Repository.class);
        when(repositoryMock.getStorageBytes(any(),eq(RELEASE_REQUEST_QUEUE.getKey()))).
            thenReturn(BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Arrays.asList(oldEntry)))));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams, activations);
        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0)
        );

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), testnetBtcParams);
            Assertions.assertEquals(entries, new ArrayList<>(Arrays.asList(oldEntry)));
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey()), any(byte[].class));

        doAnswer((i) -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), testnetBtcParams, true);
            Assertions.assertEquals(entries, new ArrayList<>(Arrays.asList(newEntry)));
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE_WITH_TXHASH.getKey()), any(byte[].class));

        storageProvider.saveReleaseRequestQueue();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey()), any(byte[].class));
        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE_WITH_TXHASH.getKey()), any(byte[].class));
        Assertions.assertEquals(2, storageProvider.getReleaseRequestQueue().getEntries().size());
    }

    @Test
    void getPegoutsWaitingForConfirmations_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"),
            testnetBtcParams, activationsBeforeFork);

        Set<PegoutsWaitingForConfirmations.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L)
        ));

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey())))
            .then((InvocationOnMock invocation) ->
                BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(new PegoutsWaitingForConfirmations(oldEntriesSet)));

        PegoutsWaitingForConfirmations result = storageProvider.getPegoutsWaitingForConfirmations();

        verify(repositoryMock, never()).getStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY.getKey()));

        Assertions.assertEquals(1, result.getEntries().size());
        Assertions.assertTrue(result.getEntries().containsAll(oldEntriesSet));
    }

    @Test
    void getPegoutsWaitingForConfirmations_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Set<PegoutsWaitingForConfirmations.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L)
        ));

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey())))
            .thenReturn(BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(new PegoutsWaitingForConfirmations(oldEntriesSet)));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"),
            testnetBtcParams, activations);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();

        pegoutsWaitingForConfirmations.add(new SimpleBtcTransaction(testnetBtcParams, PegTestUtils.createHash(0)),
            1L,
            PegTestUtils.createHash3(0));

        PegoutsWaitingForConfirmations result = storageProvider.getPegoutsWaitingForConfirmations();

        Assertions.assertEquals(2, result.getEntries().size());
    }

    @Test
    void savePegoutsWaitingForConfirmations_before_rskip_146_activations() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams, activationsBeforeFork);

        Set<PegoutsWaitingForConfirmations.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L)
        ));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();
        pegoutsWaitingForConfirmations.add(new BtcTransaction(testnetBtcParams), 1L);

        doAnswer((i) -> {
            Set<PegoutsWaitingForConfirmations.Entry> entries = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(i.getArgument(2), testnetBtcParams).getEntries();
            Assertions.assertEquals(oldEntriesSet, entries);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()), any(byte[].class));

        storageProvider.savePegoutsWaitingForConfirmations();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()), any(byte[].class));
        verify(repositoryMock, never()).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY.getKey()), any(byte[].class));
    }

    @Test
    void savePegoutsWaitingForConfirmations_after_rskip_146_activations() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Set<PegoutsWaitingForConfirmations.Entry> newEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L, PegTestUtils.createHash3(0))
        ));

        Set<PegoutsWaitingForConfirmations.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L)
        ));

        Repository repositoryMock = mock(Repository.class);

        when(repositoryMock.getStorageBytes(any(),eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()))).
            thenReturn(BridgeSerializationUtils.serializePegoutsWaitingForConfirmations(new PegoutsWaitingForConfirmations(oldEntriesSet)));

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, mockAddress("aabbccdd"), testnetBtcParams, activations);
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();

        pegoutsWaitingForConfirmations.add(new SimpleBtcTransaction(testnetBtcParams, PegTestUtils.createHash(1)),
            1L,
            PegTestUtils.createHash3(0));

        doAnswer((i) -> {
            Set<PegoutsWaitingForConfirmations.Entry> entries = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(i.getArgument(2), testnetBtcParams).getEntries();
            Assertions.assertEquals(entries, oldEntriesSet);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()), any(byte[].class));

        doAnswer((i) -> {
            Set<PegoutsWaitingForConfirmations.Entry> entries = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(i.getArgument(2), testnetBtcParams, true).getEntries();
            Assertions.assertEquals(entries, newEntriesSet);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY.getKey()), any(byte[].class));

        storageProvider.savePegoutsWaitingForConfirmations();

        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()), any(byte[].class));
        verify(repositoryMock, atLeastOnce()).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY.getKey()), any(byte[].class));
        Assertions.assertEquals(2, storageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void getReleaseTransaction_after_rskip_146_activations() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider0.getPegoutsWaitingForConfirmations().add(tx1, 1L, PegTestUtils.createHash3(0));
        provider0.getPegoutsWaitingForConfirmations().add(tx2, 2L, PegTestUtils.createHash3(1));
        provider0.getPegoutsWaitingForConfirmations().add(tx3, 3L, PegTestUtils.createHash3(2));

        provider0.save();

        track.commit();

        //Reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Assertions.assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
    }

    @Test
    void setLockingCap_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsBeforeFork
        );

        provider0.setLockingCap(Coin.ZERO);
        provider0.saveLockingCap();

        // If the network upgrade is not enabled we shouldn't be writing in the repository
        verify(repository, never()).addStorageBytes(any(), any(), any());
    }

    @Test
    void setLockingCap_after_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        provider0.setLockingCap(Coin.ZERO);
        provider0.saveLockingCap();

        // Once the network upgrade is active, we will store the locking cap in the repository
        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            LOCKING_CAP_KEY.getKey(),
            BridgeSerializationUtils.serializeCoin(Coin.ZERO)
        );
    }

    @Test
    void getLockingCap_before_fork() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        assertNull(provider0.getLockingCap());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, never()).getStorageBytes(bridgeAddress, LOCKING_CAP_KEY.getKey());
    }

    @Test
    void getLockingCap_after_fork() {
        Repository repository = mock(Repository.class);
        // If by chance the repository is called I want to force the tests to fail
        when(repository.getStorageBytes(bridgeAddress, LOCKING_CAP_KEY.getKey())).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        assertEquals(Coin.SATOSHI, provider0.getLockingCap());

        // If the network upgrade is not enabled we shouldn't be reading the repository
        verify(repository, atLeastOnce()).getStorageBytes(bridgeAddress, LOCKING_CAP_KEY.getKey());
    }

    @Test
    void setLockingCapAndGetLockingCap() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
            bridgeAddress,
            testnetBtcParams,
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
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        // And then we get it back
        assertEquals(expectedCoin, provider.getLockingCap());
    }

    @Test
    void getHeightIfBtcTxhashIsAlreadyProcessed_before_RSKIP134_does_not_use_new_storage() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        HashMap<Sha256Hash, Long> hashes = new HashMap<>();
        hashes.put(hash, 1L);
        when(repository.getStorageBytes(
            bridgeAddress,
            BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey()
        )).thenReturn(BridgeSerializationUtils.serializeMapOfHashesToLong(hashes));

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());

        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());
        verify(repository, never()).getStorageBytes(bridgeAddress, BTC_TX_HASH_AP.getCompoundKey("-", hash.toString()));
    }

    @Test
    void getHeightIfBtcTxhashIsAlreadyProcessed_after_RSKIP134_uses_new_storage()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        HashMap<Sha256Hash, Long> hashes = new HashMap<>();
        hashes.put(hash1, 1L);
        when(repository.getStorageBytes(
            bridgeAddress,
            BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey()
        )).thenReturn(BridgeSerializationUtils.serializeMapOfHashesToLong(hashes));

        when(repository.getStorageBytes(
            bridgeAddress,
            BTC_TX_HASH_AP.getCompoundKey("-", hash2.toString())
        )).thenReturn(BridgeSerializationUtils.serializeLong(2L));

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        // Get hash1 which is stored in old storage
        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash1);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());

        // old storage was accessed and new storage not
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());
        verify(repository, never()).getStorageBytes(bridgeAddress, BTC_TX_HASH_AP.getCompoundKey("-", hash2.toString()));

        // Get hash2 which is stored in new storage
        result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash2);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(2), result.get());

        // old storage wasn't accessed anymore (because it is cached) and new storage was accessed
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASH_AP.getCompoundKey("-", hash2.toString()));

        // Get hash2 again
        result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash2);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(2), result.get());

        // No more accesses to repository, as both values are in cache
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASH_AP.getCompoundKey("-", hash2.toString()));
    }

    @Test
    void setHeightBtcTxhashAlreadyProcessed_before_RSKIP134_does_not_use_new_storage() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        // The repository is accessed once to set the value
        verify(repository, times(1)).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    void setHeightBtcTxhashAlreadyProcessed_before_RSKIP134_uses_new_storage() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        // The repository is never accessed as the new storage keeps the values in cache until save
        verify(repository, never()).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    void saveHeightBtcTxHashAlreadyProcessed() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        provider0.setHeightBtcTxhashAlreadyProcessed(hash, 1L);

        provider0.saveHeightBtcTxHashAlreadyProcessed();

        // The repository is never accessed as the new storage keeps the values in cache until save
        verify(repository, never()).getStorageBytes(bridgeAddress, BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getKey());

        Optional<Long> result = provider0.getHeightIfBtcTxhashIsAlreadyProcessed(hash);
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(1), result.get());
    }

    @Test
    void getCoinBaseInformation_before_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        CoinbaseInformation result = provider.getCoinbaseInformation(hash);
        assertNull(result);

        verify(repository, never()).getStorageBytes(bridgeAddress, DataWord.fromLongString("coinbaseInformation-" + hash));
    }

    @Test
    void getCoinBaseInformation_after_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        when(repository.getStorageBytes(bridgeAddress, DataWord.fromLongString("coinbaseInformation-" + hash)))
            .thenReturn(BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        CoinbaseInformation result = provider.getCoinbaseInformation(hash);
        assertEquals(coinbaseInformation.getWitnessMerkleRoot(),result.getWitnessMerkleRoot());
    }

    @Test
    void setCoinBaseInformation_before_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertNull(provider.getCoinbaseInformation(hash));
    }

    @Test
    void setCoinBaseInformation_after_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertEquals(coinbaseInformation, provider.getCoinbaseInformation(hash));
    }

    @Test
    void saveCoinBaseInformation_before_RSKIP143() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertNull(provider.getCoinbaseInformation(hash));

        provider.save();

        verify(repository, never()).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("coinbaseInformation" + hash),
            BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation)
        );
    }

    @Test
    void saveCoinBaseInformation_after_RSKIP143() throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertEquals(coinbaseInformation, provider.getCoinbaseInformation(hash));

        provider.save();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("coinbaseInformation-" + hash),
            BridgeSerializationUtils.serializeCoinbaseInformation(coinbaseInformation)
        );
    }

    @Test
    void getBtcBestBlockHashByHeight_beforeRskip199() {
        Repository repository = mock(Repository.class);
        int blockHeight = 100;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertFalse(hashOptional.isPresent());
    }

    @Test
    void getBtcBestBlockHashByHeight_afterRskip199_hashNotFound() {
        Repository repository = mock(Repository.class);
        int blockHeight = 100;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertFalse(hashOptional.isPresent());
    }

    @Test
    void getBtcBestBlockHashByHeight_afterRskip199() {
        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);
        when(repository.getStorageBytes(any(), any())).thenReturn(serializedHash);

        int blockHeight = 100;
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertTrue(hashOptional.isPresent());
        Assertions.assertEquals(blockHash, hashOptional.get());
    }

    @Test
    void saveBtcBlocksIndex_beforeRskip199() throws IOException {
        int blockHeight = 100;
        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsBeforeFork
        );

        provider.setBtcBestBlockHashByHeight(blockHeight, blockHash);
        provider.save();

        verify(repository, never()).addStorageBytes(
            bridgeAddress,
            storageKey,
            serializedHash
        );
    }

    @Test
    void saveBtcBlocksIndex_afterRskip199() throws IOException {
        int blockHeight = 100;
        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Sha256Hash blockHash = PegTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activationsAllForks
        );

        provider.setBtcBestBlockHashByHeight(blockHeight, blockHash);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            storageKey,
            serializedHash
        );
    }

    @Test
    void isFlyoverFederationDerivationHashUsed_afterRSKIP176_returnTrue() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST});

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertTrue(result);
    }

    @Test
    void isFlyoverFederationDerivationHashUsed_beforeRSKIP176_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    void isFlyoverFederationDerivationHashUsed_storageReturnsNull_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(null);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    void isFlyoverFederationDerivationHashUsed_storageReturnsEmpty_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(new byte[]{});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    void isFlyoverFederationDerivationHashUsed_storageReturnsWrongValue_returnFalse() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(new byte[]{(byte) 0});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    void saveDerivationArgumentsScriptHash_afterRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);

        provider.save();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash),
            new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST}
        );
        verifyNoMoreInteractions(repository);
    }

    @Test
    void saveDerivationArgumentsScriptHash_afterRSKIP176_nullBtcTxHash_notSaved() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(null, derivationHash);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    void saveDerivationArgumentsScriptHash_afterRSKIP176_nullDerivationHash_notSaved()
        throws IOException {
        Repository repository = mock(Repository.class);

        Sha256Hash btcTxHash = PegTestUtils.createHash(1);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, null);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    void saveDerivationArgumentsScriptHash_beforeRSKIP176_ok() throws IOException {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = PegTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);

        provider.save();

        verify(repository, never()).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash),
            new byte[]{FAST_BRIDGE_FEDERATION_SCRIPT_HASH_TRUE_VALUE_TEST}
        );
    }

    @Test
    void getFlyoverFederationInformation_afterRSKIP176_ok() {
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
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Optional <FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);

        Assertions.assertTrue((result.isPresent()));
        Assertions.assertArrayEquals(federationRedeemScriptHash, result.get().getFederationRedeemScriptHash());
        Assertions.assertArrayEquals(derivationHash.getBytes(), result.get().getDerivationHash().getBytes());
        Assertions.assertArrayEquals(flyoverFederationRedeemScriptHash, result.get().getFlyoverFederationRedeemScriptHash());
    }

    @Test
    void getFlyoverFederationInformation_beforeRSKIP176_ok() {
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
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void getFlyoverFederationInformation_notFound() {
        Repository repository = mock(Repository.class);

        byte[] flyoverFederationRedeemScriptHash = new byte[]{(byte) 0xaa};

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)))
        ).thenReturn(null);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(flyoverFederationRedeemScriptHash);
        assertFalse(result.isPresent());
    }

    @Test
    void getFlyoverFederationInformation_nullParameter_returnEmpty() {
        Repository repository = mock(Repository.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(null);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void getFlyoverFederationInformation_arrayEmpty_returnEmpty() {
        Repository repository = mock(Repository.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(new byte[]{});
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void saveFlyoverFederationInformation_afterRSKIP176_ok() throws IOException {
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
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    void saveFlyoverFederationInformation_beforeRSKIP176_ok() throws IOException {
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
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, never()).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    void saveFlyoverFederationInformation_alreadySet_dont_set_again() throws IOException {
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
            bridgeAddress,
            testnetBtcParams,
            activations
        );

        provider.setFlyoverFederationInformation(flyoverFederationInformation);

        //Set again
        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        provider.save();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeFederationInformation-" + Hex.toHexString(flyoverFederationRedeemScriptHash)),
            BridgeSerializationUtils.serializeFlyoverFederationInformation(flyoverFederationInformation)
        );
    }

    @Test
    void getReceiveHeadersLastTimestamp_before_RSKIP200() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsBeforeFork
        );

        assertFalse(provider.getReceiveHeadersLastTimestamp().isPresent());
    }

    @Test
    void getReceiveHeadersLastTimestamp_after_RSKIP200() {
        Repository repository = mock(Repository.class);

        long actualTimeStamp = System.currentTimeMillis();
        byte[] encodedTimeStamp = RLP.encodeBigInteger(BigInteger.valueOf(actualTimeStamp));
        when(repository.getStorageBytes(bridgeAddress, RECEIVE_HEADERS_TIMESTAMP.getKey()))
            .thenReturn(encodedTimeStamp);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        Optional<Long> result = provider.getReceiveHeadersLastTimestamp();

        assertTrue(result.isPresent());
        assertEquals(actualTimeStamp, (long) result.get());
    }

    @Test
    void getReceiveHeadersLastTimestamp_not_in_repository() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        assertFalse(provider.getReceiveHeadersLastTimestamp().isPresent());
    }

    @Test
    void saveReceiveHeadersLastTimestamp_before_RSKIP200() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsBeforeFork
        );

        provider.setReceiveHeadersLastTimestamp(System.currentTimeMillis());

        provider.save();
        verify(repository, never()).addStorageBytes(
            eq(bridgeAddress),
            eq(RECEIVE_HEADERS_TIMESTAMP.getKey()),
            any(byte[].class)
        );
    }

    @Test
    void saveReceiveHeadersLastTimestamp_after_RSKIP200() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        long timeInMillis = System.currentTimeMillis();
        provider.setReceiveHeadersLastTimestamp(timeInMillis);

        provider.save();
        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            RECEIVE_HEADERS_TIMESTAMP.getKey(),
            BridgeSerializationUtils.serializeLong(timeInMillis)
        );
    }

    @Test
    void saveReceiveHeadersLastTimestamp_not_set() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        provider.save();
        verify(repository, never()).addStorageBytes(
            eq(bridgeAddress),
            eq(RECEIVE_HEADERS_TIMESTAMP.getKey()),
            any(byte[].class)
        );
    }

    @Test
    void getNextPegoutHeight_before_RSKIP271_activation() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsBeforeFork
        );

        assertEquals(Optional.empty(), provider.getNextPegoutHeight());

        verify(repository, never()).getStorageBytes(bridgeAddress, NEXT_PEGOUT_HEIGHT_KEY.getKey());
    }

    @Test
    void getNextPegoutHeight_after_RSKIP271_activation() {
        Repository repository = mock(Repository.class);

        when(repository.getStorageBytes(bridgeAddress, NEXT_PEGOUT_HEIGHT_KEY.getKey())).thenReturn(new byte[] { 1 });

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        assertEquals(Optional.of(1L), provider.getNextPegoutHeight());

        verify(repository, atLeastOnce()).getStorageBytes(bridgeAddress, NEXT_PEGOUT_HEIGHT_KEY.getKey());
    }

    @Test
    void setNextPegoutHeightAndGetNextPegoutHeight_after_RSKIP271_activation() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider1 = new BridgeStorageProvider(
            track, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        provider1.setNextPegoutHeight(1L);
        provider1.saveNextPegoutHeight();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(
            track, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        MatcherAssert.assertThat(provider2.getNextPegoutHeight(), is(Optional.of(1L)));
    }

    @Test
    void saveNextPegoutHeight_before_RSKIP271() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsBeforeFork
        );

        provider.setNextPegoutHeight(10L);
        provider.saveNextPegoutHeight();

        verify(repository, never()).addStorageBytes(
            eq(bridgeAddress),
            eq(NEXT_PEGOUT_HEIGHT_KEY.getKey()),
            any()
        );
    }

    @Test
    void saveNextPegoutHeight_after_RSKIP271() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        provider.setNextPegoutHeight(10L);
        provider.saveNextPegoutHeight();

        verify(repository, times(1)).addStorageBytes(
            bridgeAddress,
            NEXT_PEGOUT_HEIGHT_KEY.getKey(),
            BridgeSerializationUtils.serializeLong(10L)
        );
    }

    @Test
    void getReleaseRequestQueueSize_when_releaseRequestQueue_is_null() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        Assertions.assertEquals(0, storageProvider.getReleaseRequestQueueSize());
    }

    @Test
    void getReleaseRequestQueueSize_when_releaseRequestQueue_is_not_null() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository, bridgeAddress,
            testnetBtcParams, activationsAllForks
        );

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0));

        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
            Coin.COIN,
            PegTestUtils.createHash3(1));

        Assertions.assertEquals(2, storageProvider.getReleaseRequestQueueSize());
    }

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(testnetBtcParams);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }

    private static Repository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie(trieStore))));
    }

    private BridgeStorageProvider createBridgeStorageProvider(Repository repository, NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {
        return new BridgeStorageProvider(repository, bridgeAddress, networkParameters, activations);
    }
}
