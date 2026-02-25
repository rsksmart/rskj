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

import static co.rsk.peg.BridgeSerializationUtils.deserializeOutpointsValues;
import static co.rsk.peg.BridgeStorageIndexKey.*;
import static org.ethereum.TestUtils.assertThrows;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.constants.*;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static final NetworkParameters testnetBtcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
    private static final NetworkParameters mainnetBtcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    private final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

    private final ActivationConfig.ForBlock activationsBeforeFork = ActivationConfigsForTest.genesis().forBlock(0L);
    private final ActivationConfig.ForBlock activationsAllForks = ActivationConfigsForTest.all().forBlock(0);

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
        Sha256Hash hash1 = BitcoinTestUtils.createHash(1);
        Sha256Hash hash2 = BitcoinTestUtils.createHash(2);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(
            track,
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

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("save, set and get svp fund transaction hash unsigned tests")
    class SvpFundTxHashUnsignedTests {
        private final Sha256Hash svpFundTxHash = BitcoinTestUtils.createHash(123_456_789);
        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setup() {
            repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, activationsAllForks);
        }

        @Test
        void saveSvpFundTxHashUnsigned_preLovell700_shouldNotSaveInStorage() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Act
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpFundTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey());
            assertNull(actualSvpFundTxHashSerialized);
        }

        @Test
        void saveSvpFundTxHashUnsigned_postLovell700_shouldSaveInStorage() {
            // Act
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);
            bridgeStorageProvider.save();

            // Assert
            byte[] svpFundTxHashSerialized = BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash);
            byte[] actualSvpFundTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey());
            assertArrayEquals(svpFundTxHashSerialized, actualSvpFundTxHashSerialized);
        }

        @Test
        void saveSvpFundTxHashUnsigned_postLovell700AndResettingToNull_shouldSaveNullInStorage() {
            // Initially setting a valid hash in storage
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);
            bridgeStorageProvider.save();

            // Act
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpFundTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey());
            assertNull(actualSvpFundTxHashSerialized);
        }

        @Test
        void getSvpFundTxHashUnsigned_preLovell700_shouldReturnEmpty() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Manually setting the value in storage to then assert that pre fork the method doesn't access the storage
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash));

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenThereIsNoSvpFundTxHashUnsignedSavedNorSet_shouldReturnEmpty() {
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenHashSetButNotSavedToStorage_shouldReturnTheHash() {
            // Arrange
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());
        }

        @Test
        void getSvpFundTxHashUnsigned_whenDifferentHashIsInStorageAndAnotherIsSetButNotSaved_shouldReturnTheSetHash() {
            // Arrange
            Sha256Hash anotherSvpFundTxHash = BitcoinTestUtils.createHash(987_654_321);
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(anotherSvpFundTxHash));
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());
        }

        @Test
        void getSvpFundTxHashUnsigned_whenStorageIsNotEmptyAndHashSetToNullButNotSaved_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash));
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenHashSetAndSaved_shouldReturnTheHash() {
            // Arrange
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);
            bridgeStorageProvider.save();

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());
        }

        @Test
        void getSvpFundTxHashUnsigned_whenHashDirectlySavedInStorage_shouldReturnTheHash() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash));

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());
        }

        @Test
        void getSvpFundTxHashUnsigned_whenSetToNull_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenHashIsNullInStorage_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), null);

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenNullHashIsSetAndSaved_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();
            bridgeStorageProvider.save();

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxHashUnsigned);
        }

        @Test
        void getSvpFundTxHashUnsigned_whenHashIsCached_shouldReturnTheCachedHash() {
            // Arrange
            // Manually saving a hash in storage to then cache it
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash));

            // Calling method, so it retrieves the hash from storage and caches it
            bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Setting a different hash in storage to make sure that when calling the method again it returns the cached one, not this one
            Sha256Hash anotherSvpFundTxHash = BitcoinTestUtils.createHash(987_654_321);
            repository.addStorageBytes(bridgeAddress, SVP_FUND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(anotherSvpFundTxHash));

            // Act
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();

            // Assert
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());
        }

        @Test
        void clearSvpFundTxHashUnsigned() {
            // Arrange
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHash);

            // Ensure it is set
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());

            // Act
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();

            // Assert
            svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTxHashUnsigned.isEmpty());
        }

        @Test
        void clearSvpFundTxHashUnsigned_whenHashIsCached_shouldClearTheCachedHash() {
            // Arrange
            // Manually saving a hash in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_HASH_UNSIGNED.getKey(),
                BridgeSerializationUtils.serializeSha256Hash(svpFundTxHash)
            );

            // Calling method, so it retrieves the hash from storage and caches it
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertEquals(svpFundTxHash, svpFundTxHashUnsigned.get());

            // Act
            bridgeStorageProvider.clearSvpFundTxHashUnsigned();

            // Assert
            svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTxHashUnsigned.isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("save, set and get svp fund transaction signed tests")
    class SvpFundTxSignedTests {
        private final BtcTransaction svpFundTx = new BtcTransaction(mainnetBtcParams);
        private final BtcTransaction anotherSvpFundTx = new BtcTransaction(mainnetBtcParams);
        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setup() {
            repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, activationsAllForks);

            BtcTransaction prevTx = new BtcTransaction(mainnetBtcParams);
            Address address = BitcoinTestUtils.createP2PKHAddress(mainnetBtcParams, "address");
            prevTx.addOutput(Coin.FIFTY_COINS, address);
            svpFundTx.addInput(prevTx.getOutput(0));
        }

        @Test
        void saveSvpFundTxSigned_preLovell700_shouldNotSaveInStorage() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Act
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpFundTxSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_SIGNED.getKey());
            assertNull(actualSvpFundTxSerialized);
        }

        @Test
        void saveSvpFundTxSigned_postLovell700_shouldSaveInStorage() {
            // Act
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);
            bridgeStorageProvider.save();

            // Assert
            byte[] svpFundTxSerialized = BridgeSerializationUtils.serializeBtcTransaction(svpFundTx);
            byte[] actualSvpFundTxSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_SIGNED.getKey());
            assertArrayEquals(svpFundTxSerialized, actualSvpFundTxSerialized);
        }

        @Test
        void saveSvpFundTxSigned_postLovell700AndResettingToNull_shouldSaveNullInStorage() {
            // Initially setting a valid tx in storage
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);
            bridgeStorageProvider.save();

            // Act
            bridgeStorageProvider.clearSvpFundTxSigned();
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpFundTxSerialized = repository.getStorageBytes(bridgeAddress, SVP_FUND_TX_SIGNED.getKey());
            assertNull(actualSvpFundTxSerialized);
        }

        @Test
        void getSvpFundTxSigned_preLovell700_whenHashSet_shouldReturnEmpty() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_preLovell700_whenHashSaved_shouldReturnEmpty() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Manually setting the value in storage to then assert that pre fork the method doesn't access the storage
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_whenThereIsNosvpFundTxSignedSavedNorSet_shouldReturnEmpty() {
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_whenHashSet_shouldReturnTheHash() {
            // Arrange
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(svpFundTx, svpFundTxSigned.get());
        }

        @Test
        void getSvpFundTxSigned_whenHashSetToNull_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpFundTxSigned();

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_whenHashSavedAndHashSet_shouldReturnTheSetHash() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );
            bridgeStorageProvider.setSvpFundTxSigned(anotherSvpFundTx);

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(anotherSvpFundTx, svpFundTxSigned.get());
        }

        @Test
        void getSvpFundTxSigned_whenHashSavedAndHashSetToNull_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );
            bridgeStorageProvider.clearSvpFundTxSigned();

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_whenHashSaved_shouldReturnTheHash() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(svpFundTx, svpFundTxSigned.get());
        }

        @Test
        void getSvpFundTxSigned_whenNullHashSaved_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                null
            );

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertEquals(Optional.empty(), svpFundTxSigned);
        }

        @Test
        void getSvpFundTxSigned_whenHashIsCached_shouldReturnTheCachedHash() {
            // Arrange
            // Manually saving a tx in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );

            // Calling method, so it retrieves the tx from storage and caches it
            bridgeStorageProvider.getSvpFundTxSigned();

            // Setting a different tx in storage to make sure that when calling the method again it returns the cached one, not this one
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(anotherSvpFundTx)
            );

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(svpFundTx, svpFundTxSigned.get());
        }

        @Test
        void getSvpFundTxSigned_whenNullHashIsCached_shouldReturnNewSavedHash() {
            // Arrange
            // Manually saving a null tx in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                null
            );

            // Calling method, so it retrieves the tx from storage and caches it
            bridgeStorageProvider.getSvpFundTxSigned();

            // Setting a tx in storage
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(anotherSvpFundTx)
            );

            // Act
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();

            // Assert
            // since null tx was directly saved and not set, method returns new saved tx
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(anotherSvpFundTx, svpFundTxSigned.get());
        }

        @Test
        void clearSvpFundTxSigned() {
            // Arrange
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);

            // Ensure it is set
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(svpFundTx, svpFundTxSigned.get());

            // Act
            bridgeStorageProvider.clearSvpFundTxSigned();

            // Assert
            svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTxSigned.isEmpty());
        }

        @Test
        void clearSvpFundTxSigned_whenHashIsCached_shouldClearTheCachedHash() {
            // Arrange
            // Manually saving a hash in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_FUND_TX_SIGNED.getKey(),
                BridgeSerializationUtils.serializeBtcTransaction(svpFundTx)
            );

            // Calling method, so it retrieves the hash from storage and caches it
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTxSigned.isPresent());
            assertEquals(svpFundTx, svpFundTxSigned.get());

            // Act
            bridgeStorageProvider.clearSvpFundTxSigned();

            // Assert
            svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTxSigned.isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("save, set and get svp spend transaction hash unsigned tests")
    class SvpSpendTxHashUnsignedTests {
        private final Sha256Hash svpSpendTxHash = BitcoinTestUtils.createHash(123_456_789);
        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setup() {
            repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, activationsAllForks);
        }

        @Test
        void saveSvpSpendTxHashUnsigned_preLovell700_shouldNotSaveInStorage() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Act
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpSpendTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey());
            assertNull(actualSvpSpendTxHashSerialized);
        }

        @Test
        void saveSvpSpendTxHashUnsigned_postLovell700_shouldSaveInStorage() {
            // Act
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);
            bridgeStorageProvider.save();

            // Assert
            byte[] svpSpendTxHashSerialized = BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash);
            byte[] actualSvpSpendTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey());
            assertArrayEquals(svpSpendTxHashSerialized, actualSvpSpendTxHashSerialized);
        }

        @Test
        void saveSvpSpendTxHashUnsigned_postLovell700AndResettingToNull_shouldSaveNullInStorage() {
            // Initially setting a valid hash in storage
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);
            bridgeStorageProvider.save();

            // Act
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpSpendTxHashSerialized = repository.getStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey());
            assertNull(actualSvpSpendTxHashSerialized);
        }

        @Test
        void getSvpSpendTxHashUnsigned_preLovell700_shouldReturnEmpty() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Manually setting the value in storage to then assert that pre fork the method doesn't access the storage
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash));

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenThereIsNoSvpSpendTxHashUnsignedSavedNorSet_shouldReturnEmpty() {
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenHashSetButNotSavedToStorage_shouldReturnTheHash() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenDifferentHashIsInStorageAndAnotherIsSetButNotSaved_shouldReturnTheSetHash() {
            // Arrange
            Sha256Hash anotherSvpSpendTxHash = BitcoinTestUtils.createHash(987_654_321);
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(anotherSvpSpendTxHash));
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());
        }

        @Test
        void getSvpFundTxHashUnsigned_whenStorageIsNotEmptyAndHashSetToNullButNotSaved_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash));
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenHashSetAndSaved_shouldReturnTheHash() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);
            bridgeStorageProvider.save();

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenHashDirectlySavedInStorage_shouldReturnTheHash() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash));

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenSetToNull_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenHashIsNullInStorage_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), null);

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenNullHashIsSetAndSaved_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();
            bridgeStorageProvider.save();

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertEquals(Optional.empty(), svpSpendTxHashUnsigned);
        }

        @Test
        void getSvpSpendTxHashUnsigned_whenHashIsCached_shouldReturnTheCachedHash() {
            // Arrange
            // Manually saving a hash in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_HASH_UNSIGNED.getKey(),
                BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash)
            );

            // Calling method, so it retrieves the hash from storage and caches it
            bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Setting a different hash in storage to make sure that when calling the method again it returns the cached one, not this one
            Sha256Hash anotherSvpSpendTxHash = BitcoinTestUtils.createHash(987_654_321);
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_HASH_UNSIGNED.getKey(), BridgeSerializationUtils.serializeSha256Hash(anotherSvpSpendTxHash));

            // Act
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();

            // Assert
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());
        }

        @Test
        void clearSvpSpendTxHashUnsigned() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxHash);

            // Ensure it is set
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());

            // Act
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();

            // Assert
            svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTxHashUnsigned.isEmpty());
        }

        @Test
        void clearSvpSpendTxHashUnsigned_whenHashIsCached_shouldClearTheCachedHash() {
            // Arrange
            // Manually saving a hash in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_HASH_UNSIGNED.getKey(),
                BridgeSerializationUtils.serializeSha256Hash(svpSpendTxHash)
            );

            // Calling method, so it retrieves the hash from storage and caches it
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTxHashUnsigned.isPresent());
            assertEquals(svpSpendTxHash, svpSpendTxHashUnsigned.get());

            // Act
            bridgeStorageProvider.clearSvpSpendTxHashUnsigned();

            // Assert
            svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTxHashUnsigned.isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("save, set and get svp spend transaction waiting for signatures tests")
    class SvpSpendTxWaitingForSignaturesTests {
        private static final Keccak256 spendTxCreationHash = RskTestUtils.createHash(1);
        private static final BtcTransaction svpSpendTx = new BtcTransaction(mainnetBtcParams);
        private final Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures =
            new AbstractMap.SimpleEntry<>(spendTxCreationHash, svpSpendTx);
        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setup() {
            repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(
                repository,
                mainnetBtcParams,
                activationsAllForks
            );
        }

        @Test
        void saveSvpSpendTxWaitingForSignatures_preLovell700_shouldNotSaveInStorage() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Act
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpSpendTxWaitingForSignatures = repository.getStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey()
            );
            assertNull(actualSvpSpendTxWaitingForSignatures);
        }

        private static Stream<Arguments> invalidEntryArgs() {
            return Stream.of(
                Arguments.of(null, null),
                Arguments.of(spendTxCreationHash, null),
                Arguments.of(null, svpSpendTx)
            );
        }

        @ParameterizedTest
        @MethodSource("invalidEntryArgs")
        void setSvpSpendTxWaitingForSignatures_postLovell700AndInvalidEntry_shouldThrowIllegalArgumentException(Keccak256 spendTxCreationHash, BtcTransaction svpSpendTx) {
            // Arrange
            Map.Entry<Keccak256, BtcTransaction> invalidSvpSpendTxWaitingForSignatures =
                new AbstractMap.SimpleEntry<>(spendTxCreationHash, svpSpendTx);

            // Act
            assertThrows(
                IllegalArgumentException.class,
                () -> bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(invalidSvpSpendTxWaitingForSignatures)
            );

            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpSpendTxWaitingForSignatures = repository.getStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey()
            );
            assertNull(actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void saveSvpSpendTxWaitingForSignatures_postLovell700_shouldSaveInStorage() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);

            // Act
            bridgeStorageProvider.save();

            // Assert
            byte[] svpSpendTxWaitingForSignaturesSerialized = 
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures);
            byte[] actualSvpSpendTxWaitingForSignaturesSerialized =
                repository.getStorageBytes(bridgeAddress, SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey());
            assertArrayEquals(svpSpendTxWaitingForSignaturesSerialized, actualSvpSpendTxWaitingForSignaturesSerialized);
        }

        @Test
        void saveSvpSpendTxWaitingForSignatures_postLovell700AndNullSvpSpendTxWaitingForSignatures_shouldSaveInStorage() {
            // Initially setting a valid entry in storage
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);
            bridgeStorageProvider.save();

            // Act
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();
            bridgeStorageProvider.save();

            // Assert
            byte[] actualSvpSpendTxWaitingForSignatures = repository.getStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey()
            );
            assertNull(actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_preLovell700_shouldReturnEmpty() {
            // Arrange
            ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, arrowheadActivations);

            // Manually setting the value in storage to then assert that pre fork the method doesn't access the storage
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures));

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenThereIsNoSvpSpendTxWaitingForSignaturesSaved_shouldReturnEmpty() {
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenEntrySetButNotSavedToStorage_shouldReturnTheSetEntry() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenDifferentEntryIsInStorageAndAnotherIsSetButNotSaved_shouldReturnTheSetEntry() {
            // Arrange
            Keccak256 anotherSvpSpendTxCreationHash = RskTestUtils.createHash(2);
            BtcTransaction anotherSvpSpendTx = new BtcTransaction(mainnetBtcParams);
            Map.Entry<Keccak256, BtcTransaction> anotherSvpSpendTxWaitingForSignatures =
              new AbstractMap.SimpleEntry<>(anotherSvpSpendTxCreationHash, anotherSvpSpendTx);
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(anotherSvpSpendTxWaitingForSignatures));
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenDifferentEntryIsInStorageAndEntrySetToNullButNotSaved_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures));
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenEntrySetAndSaved_shouldReturnTheEntry() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);
            bridgeStorageProvider.save();

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenEntryDirectlySavedInStorage_shouldReturnTheEntry() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures));

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenSetToNull_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenNullEntryIsSetAndSaved_shouldReturnEmpty() {
            // Arrange
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();
            bridgeStorageProvider.save();

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenEntryIsNullInStorage_shouldReturnEmpty() {
            // Arrange
            repository.addStorageBytes(bridgeAddress, SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(), null);

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertEquals(Optional.empty(), actualSvpSpendTxWaitingForSignatures);
        }

        @Test
        void getSvpSpendTxWaitingForSignatures_whenEntryIsCached_shouldReturnTheCachedEntry() {
            // Arrange
            // Manually saving a entry in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures));

            // Calling method, so it retrieves the entry from storage and caches it
            bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Setting a different entry in storage to make sure that when calling
            // the method again it returns the cached one, not this one
            Keccak256 anotherSvpSpendTxCreationHash = RskTestUtils.createHash(2);
            BtcTransaction anotherSvpSpendTx = new BtcTransaction(mainnetBtcParams);
            Map.Entry<Keccak256, BtcTransaction> anotherSvpSpendTxWaitingForSignatures =
              new AbstractMap.SimpleEntry<>(anotherSvpSpendTxCreationHash, anotherSvpSpendTx);
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(anotherSvpSpendTxWaitingForSignatures)
            );

            // Act
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures =
                bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();

            // Assert
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());
        }

        @Test
        void clearSvpSpendTxWaitingForSignatures() {
            // Arrange
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);

            // Ensure it is set
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());

            // Act
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();

            // Assert
            actualSvpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(actualSvpSpendTxWaitingForSignatures.isEmpty());
        }

        @Test
        void clearSvpSpendTxWaitingForSignatures_whenValueIsCached_shouldClearTheCachedValue() {
            // Arrange
            // Manually saving a value in storage to then cache it
            repository.addStorageBytes(
                bridgeAddress,
                SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWaitingForSignatures)
            );

            // Calling method, so it retrieves the value from storage and caches it
            Optional<Map.Entry<Keccak256, BtcTransaction>> actualSvpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(actualSvpSpendTxWaitingForSignatures.isPresent());
            assertEquals(svpSpendTxWaitingForSignatures, actualSvpSpendTxWaitingForSignatures.get());

            // Act
            bridgeStorageProvider.clearSvpSpendTxWaitingForSignatures();

            // Assert
            actualSvpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(actualSvpSpendTxWaitingForSignatures.isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("clear svp values tests")
    class ClearSvpValuesTests {
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setup() {
            Repository repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, activationsAllForks);
        }

        @Test
        void clearSvpValues_whenFundTxHashUnsigned_shouldClearValue() {
            // arrange
            Sha256Hash svpFundTxHashUnsigned = BitcoinTestUtils.createHash(1);
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTxHashUnsigned);

            // act
            bridgeStorageProvider.clearSvpValues();

            // assert
            assertNoSVPValues();
        }

        @Test
        void clearSvpValues_whenFundTxSigned_shouldClearValue() {
            // arrange
            BtcTransaction svpFundTxSigned = new BtcTransaction(mainnetBtcParams);
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTxSigned);

            // act
            bridgeStorageProvider.clearSvpValues();

            // assert
            assertNoSVPValues();
        }

        @Test
        void clearSvpValues_whenSpendTxWFS_shouldClearSpendTxValues() {
            // arrange
            Keccak256 svpSpendTxCreationHash = RskTestUtils.createHash(1);
            BtcTransaction svpSpendTx = new BtcTransaction(mainnetBtcParams);
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTx);
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWFS);

            // act
            bridgeStorageProvider.clearSvpValues();

            // assert
            assertNoSVPValues();
        }

        @Test
        void clearSvpValues_whenSpendTxHashUnsigned_shouldClearValue() {
            // arrange
            Sha256Hash svpSpendTxCreationHash = BitcoinTestUtils.createHash(1);
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTxCreationHash);

            // act
            bridgeStorageProvider.clearSvpValues();

            // assert
            assertNoSVPValues();
        }

        private void assertNoSVPValues() {
            assertFalse(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
            assertFalse(bridgeStorageProvider.getSvpFundTxSigned().isPresent());
            assertFalse(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isPresent());
            assertFalse(bridgeStorageProvider.getSvpSpendTxHashUnsigned().isPresent());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("save, set and get releases outpoints tests")
    class ReleasesOutpointsValues {
        private static final Sha256Hash releaseTxHash1 = BitcoinTestUtils.createHash(1);
        private static final List<Coin> outpointsValues1 = Arrays.asList(
            Coin.valueOf(12345), Coin.SATOSHI, Coin.COIN
        );
        private static final Sha256Hash releaseTxHash2 = BitcoinTestUtils.createHash(2);
        private static final List<Coin> outpointsValues2 = Arrays.asList(
            Coin.valueOf(123456), Coin.COIN, Coin.SATOSHI
        );

        private BridgeStorageProvider bridgeStorageProvider;
        private Repository repository;

        @BeforeEach
        void setup() {
            repository = createRepository();
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, activationsAllForks);
        }

        @Test
        void setAndSaveReleaseOutpointsValues_preReed800_shouldNotSaveInStorage() {
            // Arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, lovellActivations);

            // Act
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues1);
            bridgeStorageProvider.save();

            // Assert
            byte[] actualReleaseOutpointsValues = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1)
            );
            assertNull(actualReleaseOutpointsValues);
        }

        @Test
        void outpointsValues_immutable() {
            // arrange
            List<Coin> outpointsValues = new ArrayList<>(
                Arrays.asList(Coin.MILLICOIN, Coin.SATOSHI, Coin.COIN)
            );
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues);
            Optional<List<Coin>> actualReleaseOutpointsValuesBeforeModifyingOpt = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            assertTrue(actualReleaseOutpointsValuesBeforeModifyingOpt.isPresent());
            List<Coin> actualReleaseOutpointsValuesBeforeModifying = List.copyOf(actualReleaseOutpointsValuesBeforeModifyingOpt.get());

            // Act
            outpointsValues.add(Coin.FIFTY_COINS);

            // assert
            Optional<List<Coin>> actualReleaseOutpointsValues = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            assertTrue(actualReleaseOutpointsValues.isPresent());
            assertEquals(actualReleaseOutpointsValuesBeforeModifying, actualReleaseOutpointsValues.get());
        }

        private static Stream<Arguments> invalidReleaseOutpointsEntryArgs() {
            return Stream.of(
                Arguments.of(null, null),
                Arguments.of(releaseTxHash1, null),
                Arguments.of(releaseTxHash1, new ArrayList<>()),
                Arguments.of(null, outpointsValues1)
            );
        }

        @ParameterizedTest
        @MethodSource("invalidReleaseOutpointsEntryArgs")
        void setAndSaveReleaseOutpointsValues_invalidEntry_shouldThrowIllegalArgumentExceptionAndNotSaveInStorage(
            Sha256Hash releaseTxHash,
            List<Coin> outpointsValues
        ) {
            // Act & assert
            assertThrows(
                IllegalArgumentException.class,
                () -> bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash, outpointsValues)
            );

            bridgeStorageProvider.save();
            byte[] actualReleaseOutpointsValues = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1)
            );
            assertNull(actualReleaseOutpointsValues);
        }

        @Test
        void setReleaseOutpointsValues_whenEntryAlreadySaved_shouldThrowIllegalArgumentException() {
            // arrange
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues1)
            );

            // Act & assert
            assertThrows(
                IllegalArgumentException.class,
                () -> bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues1)
            );
        }

        @Test
        void setAndSaveReleaseOutpointsValues_forTwoDifferentEntries_shouldSaveBothInStorage() {
            // Arrange
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues1);
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash2, outpointsValues2);

            // Act
            bridgeStorageProvider.save();

            // Assert
            byte[] actualReleaseOutpointsValues1 = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1)
            );
            assertNotNull(actualReleaseOutpointsValues1);
            assertEquals(outpointsValues1, deserializeOutpointsValues(actualReleaseOutpointsValues1));

            byte[] actualReleaseOutpointsValues2 = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash2)
            );
            assertNotNull(actualReleaseOutpointsValues2);
            assertEquals(outpointsValues2, deserializeOutpointsValues(actualReleaseOutpointsValues2));
        }

        @Test
        void setAndSaveReleaseOutpointsValues_forNewEntry_whenAnotherEntryIsInStorage_shouldHaveBothInStorage() {
            // Arrange
            // save entry in storage
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues1)
            );

            // Act
            // add new entry
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash2, outpointsValues2);
            bridgeStorageProvider.save();

            // Assert
            // both entries are saved
            byte[] actualReleaseOutpointsValues1 = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1)
            );
            assertNotNull(actualReleaseOutpointsValues1);

            byte[] actualReleaseOutpointsValues2 = repository.getStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash2)
            );
            assertNotNull(actualReleaseOutpointsValues2);
        }

        private DataWord getStorageKeyForReleaseOutpointsValues(Sha256Hash releaseTxHash) {
            return RELEASES_OUTPOINTS_VALUES.getCompoundKey("-", releaseTxHash.toString());
        }

        @Test
        void getReleaseOutpointsValues_preReed800_shouldThrowIllegalStateException() {
            // Arrange
            ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700().forBlock(0L);
            bridgeStorageProvider = createBridgeStorageProvider(repository, mainnetBtcParams, lovell700);

            // Act & assert
            assertThrows(IllegalStateException.class, () -> bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1));
            assertThrows(IllegalStateException.class, () -> bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash2));
        }

        @Test
        void getReleaseOutpointsValues_whenEntryNotSetNorSaved_shouldReturnEmpty() {
            Optional<List<Coin>> actualReleaseOutpointsValues = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            assertTrue(actualReleaseOutpointsValues.isEmpty());
        }

        @Test
        void getReleaseOutpointsValues_whenEntrySetButNotSaved_shouldReturnValues() {
            // Arrange
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues1);
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash2, outpointsValues2);

            // Act
            Optional<List<Coin>> actualReleaseOutpointsValues1 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            Optional<List<Coin>> actualReleaseOutpointsValues2 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash2);

            // Assert
            assertTrue(actualReleaseOutpointsValues1.isPresent());
            assertEquals(outpointsValues1, actualReleaseOutpointsValues1.get());

            assertTrue(actualReleaseOutpointsValues2.isPresent());
            assertEquals(outpointsValues2, actualReleaseOutpointsValues2.get());
        }

        @Test
        void getReleaseOutpointsValues_whenEntrySetAndSaved_shouldReturnValues() {
            // Arrange
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash1, outpointsValues1);
            bridgeStorageProvider.setReleaseOutpointsValues(releaseTxHash2, outpointsValues2);
            bridgeStorageProvider.save();

            // Act
            Optional<List<Coin>> actualReleaseOutpointsValues1 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            Optional<List<Coin>> actualReleaseOutpointsValues2 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash2);

            // Assert
            assertTrue(actualReleaseOutpointsValues1.isPresent());
            assertEquals(outpointsValues1, actualReleaseOutpointsValues1.get());

            assertTrue(actualReleaseOutpointsValues2.isPresent());
            assertEquals(outpointsValues2, actualReleaseOutpointsValues2.get());
        }

        @Test
        void getReleaseOutpointsValues_whenEntriesSavedInStorage_shouldReturnValues() {
            // Arrange
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash1),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues1)
            );
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTxHash2),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues2)
            );

            // Act
            Optional<List<Coin>> savedReleaseOutpointsValues1 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash1);
            assertTrue(savedReleaseOutpointsValues1.isPresent());
            assertEquals(outpointsValues1, savedReleaseOutpointsValues1.get());

            Optional<List<Coin>> savedReleaseOutpointsValues2 = bridgeStorageProvider.getReleaseOutpointsValues(releaseTxHash2);
            assertTrue(savedReleaseOutpointsValues2.isPresent());
            assertEquals(outpointsValues2, savedReleaseOutpointsValues2.get());
        }
    }

    @Test
    void getReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, testnetBtcParams, activationsBeforeFork);

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

        when(repositoryMock.getStorageBytes(any(),eq(RELEASE_REQUEST_QUEUE.getKey()))).thenReturn(
            BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Collections.singletonList(oldEntry))))
        );

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            testnetBtcParams,
            activations
        );

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(
            Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0)
        );

        ReleaseRequestQueue result = storageProvider.getReleaseRequestQueue();

        assertEquals(2, result.getEntries().size());
        assertEquals(result.getEntries().get(0), oldEntry);
        assertEquals(result.getEntries().get(1), newEntry);
    }

    @Test
    void saveReleaseRequestQueue_before_rskip_146_activation() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, testnetBtcParams, activationsBeforeFork);

        List<ReleaseRequestQueue.Entry> oldEntriesList = new ArrayList<>(Collections.singletonList(
            new ReleaseRequestQueue.Entry(
                Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"),
                Coin.COIN)));

        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();
        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mmWJhA74Pd6peL39V3AmtGHdGdJ4PyeXvL"), Coin.COIN);

        doAnswer(i -> {
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

        ReleaseRequestQueue.Entry newEntry = new ReleaseRequestQueue.Entry(
            Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0)
        );

        ReleaseRequestQueue.Entry oldEntry = new ReleaseRequestQueue.Entry(
            Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN
        );

        Repository repositoryMock = mock(Repository.class);
        when(repositoryMock.getStorageBytes(any(),eq(RELEASE_REQUEST_QUEUE.getKey()))).thenReturn(
            BridgeSerializationUtils.serializeReleaseRequestQueue(new ReleaseRequestQueue(new ArrayList<>(Collections.singletonList(oldEntry))))
        );

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, testnetBtcParams, activations);
        ReleaseRequestQueue releaseRequestQueue = storageProvider.getReleaseRequestQueue();

        releaseRequestQueue.add(Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mseEsMLuzaEdGbyAv9c9VRL9qGcb49qnxB"),
            Coin.COIN,
            PegTestUtils.createHash3(0)
        );

        doAnswer(i -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), testnetBtcParams);
            Assertions.assertEquals(entries, new ArrayList<>(Collections.singletonList(oldEntry)));
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(RELEASE_REQUEST_QUEUE.getKey()), any(byte[].class));

        doAnswer(i -> {
            List<ReleaseRequestQueue.Entry> entries = BridgeSerializationUtils.deserializeReleaseRequestQueue(i.getArgument(2), testnetBtcParams, true);
            Assertions.assertEquals(entries, new ArrayList<>(Collections.singletonList(newEntry)));
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
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock,
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

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repositoryMock,
            testnetBtcParams,
            activations
        );

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();

        pegoutsWaitingForConfirmations.add(new SimpleBtcTransaction(
            testnetBtcParams,
            BitcoinTestUtils.createHash(0)),
            1L,
            PegTestUtils.createHash3(0)
        );

        PegoutsWaitingForConfirmations result = storageProvider.getPegoutsWaitingForConfirmations();

        assertEquals(2, result.getEntries().size());
    }

    @Test
    void savePegoutsWaitingForConfirmations_before_rskip_146_activations() throws IOException {
        Repository repositoryMock = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, testnetBtcParams, activationsBeforeFork);

        Set<PegoutsWaitingForConfirmations.Entry> oldEntriesSet = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(testnetBtcParams), 1L)
        ));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();
        pegoutsWaitingForConfirmations.add(new BtcTransaction(testnetBtcParams), 1L);

        doAnswer(i -> {
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

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(repositoryMock, testnetBtcParams, activations);
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = storageProvider.getPegoutsWaitingForConfirmations();

        pegoutsWaitingForConfirmations.add(new SimpleBtcTransaction(
            testnetBtcParams,
            BitcoinTestUtils.createHash(1)),
            1L,
            PegTestUtils.createHash3(0)
        );

        doAnswer(i -> {
            Set<PegoutsWaitingForConfirmations.Entry> entries = BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(i.getArgument(2), testnetBtcParams).getEntries();
            Assertions.assertEquals(entries, oldEntriesSet);
            return true;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), eq(PEGOUTS_WAITING_FOR_CONFIRMATIONS.getKey()), any(byte[].class));

        doAnswer(i -> {
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
            testnetBtcParams,
            activations
        );

        Assertions.assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForSignatures().size());
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
            testnetBtcParams,
            activationsAllForks
        );

        assertNull(provider.getCoinbaseInformation(hash));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(hash, coinbaseInformation);

        assertEquals(coinbaseInformation, provider.getCoinbaseInformation(hash));
    }

    @Test
    void saveCoinBaseInformation_before_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
    void saveCoinBaseInformation_after_RSKIP143() {
        Repository repository = mock(Repository.class);

        Sha256Hash hash = Sha256Hash.ZERO_HASH;

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
            testnetBtcParams,
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertFalse(hashOptional.isPresent());
    }

    @Test
    void getBtcBestBlockHashByHeight_afterRskip199() {
        Sha256Hash blockHash = BitcoinTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);
        when(repository.getStorageBytes(any(), any())).thenReturn(serializedHash);

        int blockHeight = 100;
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            testnetBtcParams,
            activationsAllForks
        );

        Optional<Sha256Hash> hashOptional = provider.getBtcBestBlockHashByHeight(blockHeight);

        Assertions.assertTrue(hashOptional.isPresent());
        Assertions.assertEquals(blockHash, hashOptional.get());
    }

    @Test
    void saveBtcBlocksIndex_beforeRskip199() {
        int blockHeight = 100;
        Sha256Hash blockHash = BitcoinTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
    void saveBtcBlocksIndex_afterRskip199() {
        int blockHeight = 100;
        DataWord storageKey = DataWord.fromLongString("btcBlockHeight-" + blockHeight);

        Sha256Hash blockHash = BitcoinTestUtils.createHash(2);
        byte[] serializedHash = BridgeSerializationUtils.serializeSha256Hash(blockHash);

        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(null);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(new byte[]{});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        when(repository.getStorageBytes(
            bridgeAddress,
            DataWord.fromLongString("fastBridgeHashUsedInBtcTx-" + btcTxHash + derivationHash))
        ).thenReturn(new byte[]{(byte) 0});

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            testnetBtcParams,
            activations
        );

        boolean result = provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash);
        Assertions.assertFalse(result);
    }

    @Test
    void saveDerivationArgumentsScriptHash_afterRSKIP176_ok() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
    void saveDerivationArgumentsScriptHash_afterRSKIP176_nullBtcTxHash_notSaved() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(null, derivationHash);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    void saveDerivationArgumentsScriptHash_afterRSKIP176_nullDerivationHash_notSaved() {
        Repository repository = mock(Repository.class);

        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(1);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            testnetBtcParams,
            activations
        );

        provider.markFlyoverDerivationHashAsUsed(btcTxHash, null);

        provider.save();

        verifyNoInteractions(repository);
    }

    @Test
    void saveDerivationArgumentsScriptHash_beforeRSKIP176_ok() {
        Repository repository = mock(Repository.class);

        Keccak256 derivationHash = PegTestUtils.createHash3(1);
        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(2);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(false);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
            testnetBtcParams,
            activations
        );

        Optional<FlyoverFederationInformation> result = provider.getFlyoverFederationInformation(new byte[]{});
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void saveFlyoverFederationInformation_afterRSKIP176_ok() {
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
    void saveFlyoverFederationInformation_beforeRSKIP176_ok() {
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
    void saveFlyoverFederationInformation_alreadySet_dont_set_again() {
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
            repository,
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
            repository,
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
            repository,
            testnetBtcParams, activationsAllForks
        );

        assertFalse(provider.getReceiveHeadersLastTimestamp().isPresent());
    }

    @Test
    void saveReceiveHeadersLastTimestamp_before_RSKIP200() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
    void saveReceiveHeadersLastTimestamp_after_RSKIP200() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
    void saveReceiveHeadersLastTimestamp_not_set() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
            repository,
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
            repository,
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
            track,
            testnetBtcParams, activationsAllForks
        );

        provider1.setNextPegoutHeight(1L);
        provider1.saveNextPegoutHeight();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(
            track,
            testnetBtcParams, activationsAllForks
        );

        MatcherAssert.assertThat(provider2.getNextPegoutHeight(), is(Optional.of(1L)));
    }

    @Test
    void saveNextPegoutHeight_before_RSKIP271() {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
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
            repository,
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
            repository,
            testnetBtcParams, activationsAllForks
        );

        Assertions.assertEquals(0, storageProvider.getReleaseRequestQueueSize());
    }

    @Test
    void getReleaseRequestQueueSize_when_releaseRequestQueue_is_not_null() throws IOException {
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
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
        tx.addInput(
            BitcoinTestUtils.createHash(1),
            transactionOffset++,
            ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN))
        );

        return tx;
    }

    private static Repository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie(trieStore))));
    }

    private BridgeStorageProvider createBridgeStorageProvider(Repository repository, NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {
        return new BridgeStorageProvider(repository, networkParameters, activations);
    }
}
