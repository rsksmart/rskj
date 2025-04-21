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
    void getAddress_beforeRSKIP502_shouldReturnEmpty() {
        // Arrange
        // To simulate, there is an address already stored in the storage
        RskAddress unionBridgeContractAddress = TestUtils.generateAddress("unionBridgeContractAddress");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(lovell700);

        // Assert
        assertTrue(unionBridgeAddress.isEmpty());
    }

    @Test
    void getAddress_whenNoAddressStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(allActivations);

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
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(allActivations);

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
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(allActivations);

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
        Optional<RskAddress> actualUnionBridgeContractAddress = unionBridgeStorageProvider.getAddress(allActivations);

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
        // Arrange
        RskAddress expectedUnionBridgeContractAddress = TestUtils.generateAddress("expectedUnionBridgeContractAddress");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils.serializeRskAddress(expectedUnionBridgeContractAddress)
        );

        // Act
        unionBridgeStorageProvider.setAddress(expectedUnionBridgeContractAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isPresent());

        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNotNull(retrievedAddress);
        assertEquals(expectedUnionBridgeContractAddress, retrievedAddress);
    }

    @Test
    void setAddress_withoutSaving_shouldNotStore() {
        // Act
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
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
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(actualAddress.isEmpty());

        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);

    }

    @Test
    void getSetAndSaveAddress_whenNoAddressIsSaved_shouldSaveNewAddress() {
        // Arrange
        RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress("newUnionBridgeContractAddress");

        // Act & Assert

        // Check that the address is not present in the storage nor in the cache
        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(savedAddress);

        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(allActivations);
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
        RskAddress storedUnionAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNotNull(storedUnionAddress);
        assertEquals(newUnionBridgeContractAddress, storedUnionAddress);
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
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(existingUnionBridgeContractAddress, unionBridgeAddress.get());

        // Set the new union address
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Check the new union address is not stored yet but is present in the cache
        RskAddress originalAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertEquals(existingUnionBridgeContractAddress, originalAddress);
        assertNotEquals(newUnionBridgeAddress, originalAddress);

        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress(allActivations);
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Save the new union address
        unionBridgeStorageProvider.save(allActivations);

        RskAddress savedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNotNull(savedAddress);
        assertEquals(newUnionBridgeAddress, savedAddress);
    }
}
