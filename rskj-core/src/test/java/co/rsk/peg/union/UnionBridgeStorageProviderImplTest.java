package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import java.util.Optional;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnionBridgeStorageProviderImplTest {

    private static final ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700()
        .forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all()
        .forBlock(0);

    private static final RskAddress storedUnionBridgeContractAddress = TestUtils.generateAddress(
        "unionBridgeContractAddress");
    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final Coin unionBridgeLockingCap = UnionBridgeMainNetConstants.getInstance().getInitialLockingCap();
    private static final Coin newUnionBridgeLockingCap = unionBridgeLockingCap.times(2);

    private static final co.rsk.core.Coin amountTransferredToUnionBridge = co.rsk.core.Coin.fromBitcoin(unionBridgeLockingCap.divide(2));
    private static final co.rsk.core.Coin newAmountTransferredToUnionBridge = co.rsk.core.Coin.fromBitcoin(newUnionBridgeLockingCap.divide(2));

    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProviderImpl unionBridgeStorageProvider;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
    }

    @Test
    void getAddress_whenNoAddressStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isEmpty());
    }

    @Test
    void getAddress_whenAddressStored_shouldReturnStoredAddress() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(storedUnionBridgeContractAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressWasSet_shouldReturnCachedAddress() {
        // Arrange
        unionBridgeStorageProvider.setAddress(storedUnionBridgeContractAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(storedUnionBridgeContractAddress, unionBridgeAddress.get());
        assertNoAddressIsStored();
    }

    @Test
    void getAddress_whenStoredAddress_shouldReturnStoredAddress() {
        // Arrange
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress(
            "unionBridgeContractAddress");
        unionBridgeStorageProvider.setAddress(unionBridgeContractAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Act
        Optional<RskAddress> actualUnionBridgeContractAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(actualUnionBridgeContractAddress.isPresent());
        assertEquals(unionBridgeContractAddress, actualUnionBridgeContractAddress.get());
    }

    @Test
    void setAddress_whenEmptyAddress_shouldNotSetEmptyAddress() {
        // Arrange
        RskAddress emptyAddress = new RskAddress(new byte[20]);

        // Act
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.setAddress(emptyAddress),
            "Union Bridge address cannot be empty");
    }

    @Test
    void setAndSaveAddress_whenNull_shouldNotStoreNull() {
        // Arrange
        // To simulate, there is an address already stored in the storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        // Check existing address is still stored
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isPresent());
        // Check stored address is the same as the one before
        assertGivenAddressIsStored(storedUnionBridgeContractAddress);
    }

    private void assertNoAddressIsStored() {
        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress);
        assertNull(retrievedAddress);
    }

    private void assertGivenAddressIsStored(RskAddress newUnionBridgeAddress) {
        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress);
        assertNotNull(savedAddress);
        assertEquals(newUnionBridgeAddress, savedAddress);
    }

    @Test
    void setAddress_withoutSaving_shouldCachedTheAddressButNotStore() {
        // Arrange
        Optional<RskAddress> initialAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(initialAddress.isEmpty());

        // Act
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isPresent());
        assertNoAddressIsStored();
    }

    @Test
    void save_whenAddressSetAndBeforeRSKIP502_shouldNotStoreAnything() {
        // Arrange
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Act
        unionBridgeStorageProvider.save(lovell700);

        // Assert
        assertNoAddressIsStored();
    }

    @Test
    void save_whenNoAddressSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isEmpty());

        assertNoAddressIsStored();

    }

    @Test
    void getSetAndSaveAddress_whenNoAddressIsSaved_shouldSaveNewAddress() {
        // Act & Assert

        // Check that the address is not present in the storage nor in the cache
        assertNoAddressIsStored();

        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isEmpty());

        // Set address
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeContractAddress, cachedAddress.get());

        // Save the value
        unionBridgeStorageProvider.save(allActivations);

        // Create a new instance of the storage provider to retrieve the address from the storage
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
    }

    @Test
    void getSetAndSaveAddress_whenStoredAddress_shouldOverrideWithTheNewOne() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress);

        // Act & Assert
        // Check that union address is present in the storage
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(storedUnionBridgeContractAddress, unionBridgeAddress.get());

        // Set the new union address
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Check the new union address is not stored yet but is present in the cache
        RskAddress originalAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress);
        assertEquals(storedUnionBridgeContractAddress, originalAddress);
        assertNotEquals(newUnionBridgeContractAddress, originalAddress);

        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeContractAddress, cachedAddress.get());

        // Save the new union address
        unionBridgeStorageProvider.save(allActivations);
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
    }

    @Test
    void getLockingCap_whenNoLockingCapStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();

        // Assert
        assertTrue(actualLockingCap.isEmpty());
    }

    @Test
    void getLockingCap_whenLockingCapStored_shouldReturnStoredLockingCap() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeCoin);

        // Act
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();

        // Assert
        assertTrue(actualLockingCap.isPresent());
        assertEquals(unionBridgeLockingCap, actualLockingCap.get());
    }

    @Test
    void getLockingCap_whenLockingCapWasSet_shouldReturnCachedLockingCap() {
        // Arrange
        unionBridgeStorageProvider.setLockingCap(unionBridgeLockingCap);

        // Act
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();

        // Assert
        assertTrue(actualLockingCap.isPresent());
        assertEquals(unionBridgeLockingCap, actualLockingCap.get());
        assertNoLockingCapIsStored();
    }

    private void assertNoLockingCapIsStored() {
        Coin retrievedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin);
        assertNull(retrievedLockingCap);
    }

    @Test
    void setLockingCap_whenZero_shouldNotStoreZero() {
        // Arrange
        Coin zeroLockingCap = Coin.ZERO;

        // Act
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.setLockingCap(zeroLockingCap),
            "Locking cap cannot be zero");
    }

    @Test
    void setLockingCap_withoutSaving_shouldNotStore() {
        // Act
        unionBridgeStorageProvider.setLockingCap(unionBridgeLockingCap);

        // Assert
        assertNoLockingCapIsStored();
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(actualLockingCap.isPresent());
    }

    @Test
    void setLockingCap_whenNull_shouldNotStoreNull() {
        // Arrange
        // To simulate, there is a locking cap already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeCoin
        );

        // Act
        unionBridgeStorageProvider.setLockingCap(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        // Check existing locking cap is still stored
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(actualLockingCap.isPresent());
        assertGivenLockingCapIsStored(unionBridgeLockingCap);
    }

    private void assertGivenLockingCapIsStored(Coin newUnionBridgeLockingCap) {
        Coin savedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin);
        assertNotNull(savedLockingCap);
        assertEquals(newUnionBridgeLockingCap, savedLockingCap);
    }

    @Test
    void save_whenNoLockingCapSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(actualLockingCap.isEmpty());

        assertNoLockingCapIsStored();
    }

    @Test
    void save_whenNoLockingCapIsSaved_shouldSaveNewLockingCap() {
        // Act & Assert

        // Check that the locking cap is not present nor in the cache
        assertNoLockingCapIsStored();

        Optional<Coin> retrievedUnionBridgeLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(retrievedUnionBridgeLockingCap.isEmpty());

        // Set locking cap
        unionBridgeStorageProvider.setLockingCap(unionBridgeLockingCap);

        // Check that the locking cap is now present in the cache but not in the storage
        Optional<Coin> cachedLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(cachedLockingCap.isPresent());
        assertEquals(unionBridgeLockingCap, cachedLockingCap.get());
        assertNoLockingCapIsStored();

        // Save the value
        unionBridgeStorageProvider.save(allActivations);

        // Create a new instance of the storage provider to retrieve the locking cap from the storage
        assertGivenLockingCapIsStored(unionBridgeLockingCap);
    }

    @Test
    void save_whenStoredLockingCap_shouldOverrideWithTheNewOne() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeCoin);

        // Act & Assert
        // Check that locking cap is stored
        Optional<Coin> retrievedUnionBridgeLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(retrievedUnionBridgeLockingCap.isPresent());
        assertEquals(unionBridgeLockingCap, retrievedUnionBridgeLockingCap.get());

        // Set the new locking cap
        unionBridgeStorageProvider.setLockingCap(newUnionBridgeLockingCap);

        // Check the new locking cap is not stored yet but is present in the cache
        Coin originalLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin);
        assertEquals(unionBridgeLockingCap, originalLockingCap);
        assertNotEquals(newUnionBridgeLockingCap, originalLockingCap);

        Optional<Coin> cachedLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(cachedLockingCap.isPresent());
        assertEquals(newUnionBridgeLockingCap, cachedLockingCap.get());

        // Save the new locking cap
        unionBridgeStorageProvider.save(allActivations);
        assertGivenLockingCapIsStored(newUnionBridgeLockingCap);
    }

    @Test
    void getWeisTransferredToUnionBridge_beforeRSKIP502_shouldReturnEmpty() {
        // Arrange
        // To simulate WEIS_TRANSFERRED_TO_UNION_BRIDGE's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Act
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(lovell700);

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isEmpty());
    }

    @Test
    void getWeisTransferredToUnionBridge_whenNoValueIsStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isEmpty());
    }

    @Test
    void getWeisTransferredToUnionBridge_whenAmountTransferredStored_shouldReturnStoredAmount() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Act
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(amountTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());
    }

    @Test
    void getWeisTransferredToUnionBridge_whenAmountTransferredSet_shouldReturnCachedAmount() {
        // Arrange
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Act
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(amountTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());
        assertNoWeisTransferredToUnionBridgeIsStored();
    }

    @Test
    void setWeisTransferredToUnionBridge_whenNegative_shouldNotSetNegative() {
        // Arrange
        co.rsk.core.Coin negativeAmount = co.rsk.core.Coin.valueOf(-1);

        // Act
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.setWeisTransferredToUnionBridge(negativeAmount),
            "Wei transferred to Union Bridge cannot be negative");
    }

    @Test
    void setWeisTransferredToUnionBridge_whenZero_shouldSetZero() {
        // Arrange
        co.rsk.core.Coin zeronAmount = co.rsk.core.Coin.ZERO;

        // Act
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(zeronAmount);

        // Assert
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(zeronAmount, actualWeisTransferredToUnionBridge.get());
    }

    @Test
    void setWeisTransferredToUnionBridge_withoutSaving_shouldNotStore() {
        // Act
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Assert
        assertNoWeisTransferredToUnionBridgeIsStored();
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
    }

    @Test
    void setAndSaveWeisTransferredToUnionBridge_whenNull_shouldNotStoreNull() {
        // Arrange
        // To simulate, there is already WEIS_TRANSFERRED_TO_UNION_BRIDGE's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin
        );

        // Act
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        // Check existing wei transferred to union bridge is still stored
        Optional<co.rsk.core.Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge(allActivations);
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertGivenWeisTransferredToUnionBridgeIsStored(amountTransferredToUnionBridge);
    }

    private void assertNoWeisTransferredToUnionBridgeIsStored() {
        co.rsk.core.Coin retrievedWeisTransferredToUnionBridge = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNull(retrievedWeisTransferredToUnionBridge);
    }

    @Test
    void save_whenUnionAddressAndLockingCapAreSetAndBeforeRSKIP502_shouldNotStoreAnything() {
        // Arrange
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);
        unionBridgeStorageProvider.setLockingCap(unionBridgeLockingCap);
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Act
        unionBridgeStorageProvider.save(lovell700);

        // Assert
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredToUnionBridgeIsStored();
    }

    @Test
    void save_whenUnionAddressAndLockingCapAreSet_shouldStoreBoth() {
        // Arrange
        // To simulate, there is an address already stored in the storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress
        );
        // To simulate, there is a locking cap already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeCoin
        );
        // To simulate, there is WEIS_TRANSFERRED_TO_UNION_BRIDGE's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Set the new values
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);
        unionBridgeStorageProvider.setLockingCap(newUnionBridgeLockingCap);
        unionBridgeStorageProvider.setWeisTransferredToUnionBridge(newAmountTransferredToUnionBridge);

        // Act
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
        assertGivenLockingCapIsStored(newUnionBridgeLockingCap);
        assertGivenWeisTransferredToUnionBridgeIsStored(newAmountTransferredToUnionBridge);
    }

    private void assertGivenWeisTransferredToUnionBridgeIsStored(
        co.rsk.core.Coin expectedTransferredToUnionBridge) {
        co.rsk.core.Coin savedWeisTransferredToUnionBridge = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNotNull(savedWeisTransferredToUnionBridge);
        assertEquals(expectedTransferredToUnionBridge, savedWeisTransferredToUnionBridge);
    }
}
