package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeStorageProviderImplTest {

    private static final RskAddress storedUnionBridgeContractAddress = TestUtils.generateAddress(
        "unionBridgeContractAddress");
    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final Coin unionBridgeLockingCap = UnionBridgeMainNetConstants.getInstance().getInitialLockingCap();
    private static final Coin newUnionBridgeLockingCap = unionBridgeLockingCap.multiply(BigInteger.TWO);

    private static final Coin amountTransferredToUnionBridge = unionBridgeLockingCap.divide(BigInteger.TWO);
    private static final Coin newAmountTransferredToUnionBridge = newUnionBridgeLockingCap.divide(BigInteger.TWO);

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
        unionBridgeStorageProvider.save();

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
        unionBridgeStorageProvider.save();

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
    void save_whenNoAddressSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save();

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
        unionBridgeStorageProvider.save();

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
        unionBridgeStorageProvider.save();
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
            unionBridgeLockingCap, BridgeSerializationUtils::serializeRskCoin);

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
            BridgeSerializationUtils::deserializeRskCoin);
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
            unionBridgeLockingCap, BridgeSerializationUtils::serializeRskCoin
        );

        // Act
        unionBridgeStorageProvider.setLockingCap(null);
        unionBridgeStorageProvider.save();

        // Assert
        // Check existing locking cap is still stored
        Optional<Coin> actualLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(actualLockingCap.isPresent());
        assertGivenLockingCapIsStored(unionBridgeLockingCap);
    }

    private void assertGivenLockingCapIsStored(Coin newUnionBridgeLockingCap) {
        Coin savedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNotNull(savedLockingCap);
        assertEquals(newUnionBridgeLockingCap, savedLockingCap);
    }

    @Test
    void save_whenNoLockingCapSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save();

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
        unionBridgeStorageProvider.save();

        // Create a new instance of the storage provider to retrieve the locking cap from the storage
        assertGivenLockingCapIsStored(unionBridgeLockingCap);
    }

    @Test
    void save_whenStoredLockingCap_shouldOverrideWithTheNewOne() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeRskCoin);

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
            BridgeSerializationUtils::deserializeRskCoin);
        assertEquals(unionBridgeLockingCap, originalLockingCap);
        assertNotEquals(newUnionBridgeLockingCap, originalLockingCap);

        Optional<Coin> cachedLockingCap = unionBridgeStorageProvider.getLockingCap();
        assertTrue(cachedLockingCap.isPresent());
        assertEquals(newUnionBridgeLockingCap, cachedLockingCap.get());

        // Save the new locking cap
        unionBridgeStorageProvider.save();
        assertGivenLockingCapIsStored(newUnionBridgeLockingCap);
    }

    @Test
    void getWeisTransferredToUnionBridge_whenNoValueIsStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();

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
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(amountTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());
    }

    @Test
    void getWeisTransferredToUnionBridge_whenAmountTransferredSet_shouldReturnCachedAmount() {
        // Arrange
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Act
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();

        // Assert
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(amountTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());
        assertNoWeisTransferredToUnionBridgeIsStored();
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenAmountIsNegative_shouldThrowIllegalArgumentException() {
        // Arrange
        Coin negativeAmount = Coin.valueOf(-1);

        // Act
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(negativeAmount),
            "Amount requested to Union Bridge cannot be null or negative");
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenAmountIsZero_shouldSetZero() {
        // Arrange
        Coin zeroAmount = Coin.ZERO;

        // Act
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(zeroAmount);

        // Assert
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
        assertEquals(zeroAmount, actualWeisTransferredToUnionBridge.get());

        // save the value
        unionBridgeStorageProvider.save();

        // Create a new instance of the storage provider to retrieve the value from the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        Optional<Coin> savedWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();

        assertTrue(savedWeisTransferredToUnionBridge.isPresent());
        assertEquals(zeroAmount, savedWeisTransferredToUnionBridge.get());
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenNull_shouldThrowIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(null),
            "Amount requested to Union Bridge cannot be null or negative");
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenValidAmountRequest_shouldIncreasedWeisTransferred() {
        // Act
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Assert
        assertNoWeisTransferredToUnionBridgeIsStored();
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenStoredAmount_shouldIncreasedWeisTransferred() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Act
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(newAmountTransferredToUnionBridge);

        // Assert
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());

        // Check that the value is increased correctly
        Coin expectedWeisTransferredToUnionBridge = amountTransferredToUnionBridge.add(newAmountTransferredToUnionBridge);
        assertEquals(expectedWeisTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());

        // assert that before calling save, the value is not stored
        assertGivenWeisTransferredToUnionBridgeIsStored(amountTransferredToUnionBridge);

        // Call save to persist the value
        unionBridgeStorageProvider.save();

        // Check that the value is stored
        assertGivenWeisTransferredToUnionBridgeIsStored(expectedWeisTransferredToUnionBridge);
    }

    @Test
    void increaseWeisTransferredToUnionBridge_whenTwoBigValuesAddedTogether_shouldIncreasedWeisTransferred() {
        // Arrange
        Coin maxLongValue = Coin.valueOf(Long.MAX_VALUE);
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            maxLongValue, BridgeSerializationUtils::serializeRskCoin);

        Coin amountToRequest = maxLongValue.multiply(BigInteger.TWO);

        // Act
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(amountToRequest);

        // Assert
        Optional<Coin> actualWeisTransferredToUnionBridge = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferredToUnionBridge.isPresent());

        Coin expectedWeisTransferredToUnionBridge = maxLongValue.add(amountToRequest);
        assertEquals(expectedWeisTransferredToUnionBridge, actualWeisTransferredToUnionBridge.get());
    }

    private void assertNoWeisTransferredToUnionBridgeIsStored() {
        Coin retrievedWeisTransferredToUnionBridge = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNull(retrievedWeisTransferredToUnionBridge);
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenNull_shouldThrowIllegalArgumentException() {
        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(null),
            "Amount released cannot be null or negative");
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenNegative_shouldThrowIllegalArgumentException() {
        // Arrange
        Coin negativeAmount = Coin.valueOf(-1);

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(negativeAmount),
            "Amount released cannot be null or negative");
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenAmountGreaterThanStored_shouldThrowIllegalArgumentException() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);
        Coin tooLarge = amountTransferredToUnionBridge.add(Coin.valueOf(1));

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(tooLarge),
            "Cannot decrease weis transferred to Union Bridge below zero");
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenNoStoredValueAndDecreaseNonZero_shouldThrowIllegalArgumentException() {
        Coin nonZero = Coin.valueOf(1);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(nonZero),
            "Cannot decrease weis transferred to Union Bridge below zero");
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenAmountEqualsStored_shouldSetZero() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Act
        unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(amountTransferredToUnionBridge);

        // Assert
        Optional<Coin> actualWeisTransferred = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferred.isPresent());
        assertEquals(Coin.ZERO, actualWeisTransferred.get());

        // assert that before calling save, the value is not stored
        assertGivenWeisTransferredToUnionBridgeIsStored(amountTransferredToUnionBridge);

        // Call save to persist the value
        unionBridgeStorageProvider.save();

        // Check that the value is stored
        assertGivenWeisTransferredToUnionBridgeIsStored(Coin.ZERO);
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenAmountLessThanStored_shouldDecreaseCorrectly() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Act
        Coin amountToReleaseLessThanStored = amountTransferredToUnionBridge.divide(BigInteger.TWO);
        unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(amountToReleaseLessThanStored);

        // Assert
        Optional<Coin> actualWeisTransferred = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferred.isPresent());

        Coin expectedWeisTransferred = amountTransferredToUnionBridge.subtract(amountToReleaseLessThanStored);
        assertEquals(expectedWeisTransferred, actualWeisTransferred.get());

        // assert that before calling save, the value is not stored
        assertGivenWeisTransferredToUnionBridgeIsStored(amountTransferredToUnionBridge);

        // Call save to persist the value
        unionBridgeStorageProvider.save();

        // Check that the value is stored
        assertGivenWeisTransferredToUnionBridgeIsStored(expectedWeisTransferred);
    }

    @Test
    void decreaseWeisTransferredToUnionBridge_whenNoStoredValueAndDecreaseZero_shouldSetZero() {
        // Act
        unionBridgeStorageProvider.decreaseWeisTransferredToUnionBridge(Coin.ZERO);

        // Assert
        Optional<Coin> actualWeisTransferred = unionBridgeStorageProvider.getWeisTransferredToUnionBridge();
        assertTrue(actualWeisTransferred.isPresent());
        assertEquals(Coin.ZERO, actualWeisTransferred.get());

        // assert that before calling save, the value is not stored
        assertNoWeisTransferredToUnionBridgeIsStored();

        // Call save to persist the value
        unionBridgeStorageProvider.save();

        // Check that the value is stored
        assertGivenWeisTransferredToUnionBridgeIsStored(Coin.ZERO);
    }

    @ParameterizedTest
    @MethodSource("saveParametersProvider")
    void save_shouldStoreEachValueCorrectly(
        RskAddress newAddress,
        Coin newLockingCap,
        Coin newTransferAmount) {

        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress
        );
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeRskCoin
        );
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin
        );


        // Act
        unionBridgeStorageProvider.setAddress(newAddress);
        unionBridgeStorageProvider.setLockingCap(newLockingCap);
        if (newTransferAmount != null) {
            unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(newTransferAmount);
        }
        unionBridgeStorageProvider.save();

        // Assert
        // Check address - if a new address was set, it should be stored, otherwise the original remains
        RskAddress expectedAddress = newAddress != null ? newAddress : storedUnionBridgeContractAddress;
        assertGivenAddressIsStored(expectedAddress);

        // Check locking cap - if a new cap was set, it should be stored, otherwise the original remains
        Coin expectedLockingCap = newLockingCap != null ? newLockingCap : unionBridgeLockingCap;
        assertGivenLockingCapIsStored(expectedLockingCap);

        // Check transferred amount - if new amount was set, it should be added to the stored amount,
        Coin expectedAmount = newTransferAmount != null? newTransferAmount.add(amountTransferredToUnionBridge): amountTransferredToUnionBridge;
        assertGivenWeisTransferredToUnionBridgeIsStored(expectedAmount);
    }


    private static Stream<Arguments> saveParametersProvider() {
        return Stream.of(
            // All values null
            Arguments.of(null, null, null),

            // Only one value set
            Arguments.of(newUnionBridgeContractAddress, null, null),
            Arguments.of(null, newUnionBridgeLockingCap, null),
            Arguments.of(null, null, newAmountTransferredToUnionBridge),

            // Two values set
            Arguments.of(newUnionBridgeContractAddress, newUnionBridgeLockingCap, null),
            Arguments.of(newUnionBridgeContractAddress, null, newAmountTransferredToUnionBridge),
            Arguments.of(null, newUnionBridgeLockingCap, newAmountTransferredToUnionBridge),

            // All values set
            Arguments.of(newUnionBridgeContractAddress, newUnionBridgeLockingCap, newAmountTransferredToUnionBridge)
        );
    }


    @Test
    void save_whenValuesAreSet_shouldStoreEachValue() {
        // Arrange
        // To simulate, there is an address already stored in the storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress
        );
        // To simulate, there is a locking cap already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap, BridgeSerializationUtils::serializeRskCoin
        );
        // To simulate, there is WEIS_TRANSFERRED_TO_UNION_BRIDGE's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            amountTransferredToUnionBridge, BridgeSerializationUtils::serializeRskCoin);

        // Set the new values
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);
        unionBridgeStorageProvider.setLockingCap(newUnionBridgeLockingCap);
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(newAmountTransferredToUnionBridge);

        // Act
        unionBridgeStorageProvider.save();

        // Assert
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
        assertGivenLockingCapIsStored(newUnionBridgeLockingCap);
        Coin expectedAmountTransferredToUnionBridge = newAmountTransferredToUnionBridge.add(
            amountTransferredToUnionBridge);
        assertGivenWeisTransferredToUnionBridgeIsStored(expectedAmountTransferredToUnionBridge);
    }

    @Test
    void save_whenNothingIsSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save();

        // Assert
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredToUnionBridgeIsStored();
    }

    private void assertGivenWeisTransferredToUnionBridgeIsStored(
        Coin expectedTransferredToUnionBridge) {
        Coin savedWeisTransferredToUnionBridge = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNotNull(savedWeisTransferredToUnionBridge);
        assertEquals(expectedTransferredToUnionBridge, savedWeisTransferredToUnionBridge);
    }
}
