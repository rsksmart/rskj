package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
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

    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProviderImpl unionBridgeStorageProvider;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
    }

    @Test
    void getAddress_beforeRSKIP502_shouldReturnEmpty() {
        // Arrange
        // To simulate, there is an address already stored in the storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(lovell700);

        // Assert
        assertTrue(unionBridgeAddress.isEmpty());
    }

    @Test
    void getAddress_whenNoAddressStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(
            allActivations);

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
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(
            allActivations);

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(storedUnionBridgeContractAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressWasSet_shouldReturnCachedAddress() {
        // Arrange
        unionBridgeStorageProvider.setAddress(storedUnionBridgeContractAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(
            allActivations);

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(storedUnionBridgeContractAddress, unionBridgeAddress.get());
        assertNoAddressIsStored();
    }

    @Test
    void getAddress_whenExistsStoredAddress_shouldReturnStoredAddress() {
        // Arrange
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress(
            "unionBridgeContractAddress");
        unionBridgeStorageProvider.setAddress(unionBridgeContractAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Act
        Optional<RskAddress> actualUnionBridgeContractAddress = unionBridgeStorageProvider.getAddress(
            allActivations);

        // Assert
        assertTrue(actualUnionBridgeContractAddress.isPresent());
        assertEquals(unionBridgeContractAddress, actualUnionBridgeContractAddress.get());
    }

    @Test
    void setAddress_whenEmptyAddress_shouldNotStoreEmptyAddress() {
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
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            storedUnionBridgeContractAddress, BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isEmpty());
        assertNoAddressIsStored();
    }

    private void assertNoAddressIsStored() {
        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress);
        assertNull(retrievedAddress);
    }

    @Test
    void setAndSaveAddress_whenStoredAddressButGivenAddressIsNull_shouldNotStoreNull() {
        // Arrange
        RskAddress expectedUnionBridgeContractAddress = TestUtils.generateAddress(
            "expectedUnionBridgeContractAddress");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils.serializeRskAddress(expectedUnionBridgeContractAddress));

        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isPresent());
        assertEquals(expectedUnionBridgeContractAddress, actualAddress.get());
        assertGivenAddressIsStored(actualAddress.get());
    }

    private void assertGivenAddressIsStored(RskAddress newUnionBridgeAddress) {
        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress);
        assertNotNull(savedAddress);
        assertEquals(newUnionBridgeAddress, savedAddress);
    }

    @Test
    void setAddress_withoutSaving_shouldNotStore() {
        // Act

        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Assert
        assertNoAddressIsStored();
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isPresent());
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
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isEmpty());

        assertNoAddressIsStored();

    }

    @Test
    void getSetAndSaveAddress_whenNoAddressIsSaved_shouldSaveNewAddress() {
        // Act & Assert

        // Check that the address is not present in the storage nor in the cache
        assertNoAddressIsStored();

        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(
            allActivations);
        assertTrue(unionBridgeAddress.isEmpty());

        // Set address
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress(allActivations);
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
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(
            allActivations);
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

        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeContractAddress, cachedAddress.get());

        // Save the new union address
        unionBridgeStorageProvider.save(allActivations);
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
    }
}
