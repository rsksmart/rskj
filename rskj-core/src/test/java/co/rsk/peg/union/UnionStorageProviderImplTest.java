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

class UnionStorageProviderImplTest {

    private static final ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

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
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress("unionBridgeContractAddress");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(unionBridgeContractAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_withoutSaving_shouldReturnCachedAddress() {
        // Arrange
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress("unionBridgeContractAddress");
        unionBridgeStorageProvider.setAddress(unionBridgeContractAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(unionBridgeContractAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenExistsStoredAddress_shouldReturnStoredAddress() {
        // Arrange
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress("unionBridgeContractAddress");
        unionBridgeStorageProvider.setAddress(unionBridgeContractAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Act
        Optional<RskAddress> actualUnionBridgeContractAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(actualUnionBridgeContractAddress.isPresent());
        assertEquals(unionBridgeContractAddress, actualUnionBridgeContractAddress.get());
    }

    @Test
    void setAddress_whenEmptyAddress_shouldNotStoreEmptyAddresses() {
        // Arrange
        RskAddress emptyAddress = new RskAddress(new byte[20]);

        // Act
        Assertions.assertThrows(IllegalArgumentException.class, () -> unionBridgeStorageProvider.setAddress(emptyAddress), "Union Bridge address cannot be empty");
    }

    @Test
    void setAddress_whenNull_shouldNotStoreNullValue() {
        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isEmpty());

        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);
    }

    @Test
    void setAddress_withoutSaving_shouldNotStore() {
        // Act
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isPresent());

        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);
    }

    @Test
    void save_whenAddressSetAndBeforeRSKIP502_shouldNotStoreAnything() {
        // Arrange
        RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress("newUnionBridgeContractAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Act
        unionBridgeStorageProvider.save(lovell700);

        // Assert
        RskAddress retrievedRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedRskAddress);
    }

    @Test
    void save_whenNoAddressSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isEmpty());

        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);

    }

    @Test
    void getAndSetAddress_whenNoAddressIsSaved_shouldSaveNewAddress() {
        // Arrange
        RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress("newUnionBridgeContractAddress");

        // Act & Assert

        // Check that the address is not present in the storage nor in cache
        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(savedAddress);

        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isEmpty());

        // Set address
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);

        // Check that the address is now present in cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeContractAddress, cachedAddress.get());

        // Save the value
        unionBridgeStorageProvider.save(allActivations);

        // Create a new instance of the storage provider to retrieve the address from the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeContractAddress, storedAddress.get());
    }

    @Test
    void getAndSetAddress_whenAddressAlreadyExistsInTheStorage_shouldOverrideWithTheNewOne() {
        // Arrange
        RskAddress existingUnionBridgeContractAddress = TestUtils.generateAddress("existingUnionBridgeContractAddress");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            existingUnionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act & Assert
        // Check that union address is present in the storage
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(existingUnionBridgeContractAddress, unionBridgeAddress.get());

        // Set new union address
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Check the new union address is not stored yet but is present in the cache
        RskAddress originalAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertEquals(existingUnionBridgeContractAddress, originalAddress);
        assertNotEquals(newUnionBridgeAddress, originalAddress);

        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Save the new union address
        unionBridgeStorageProvider.save(allActivations);

        // Create a new instance of the storage provider to retrieve the union address from the storage instead of the cache
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);

        // Check that the new union address is retrieved from the storage
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, storedAddress.get());

        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertEquals(newUnionBridgeAddress, savedAddress);
    }
}
