package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnionStorageProviderImplTest {

    private static final ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
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
            newUnionBridgeAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(newUnionBridgeAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressSet_shouldReturnCacheAddress() {
        // Arrange
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(newUnionBridgeAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressSetAndStored_shouldReturnSetAddress() {
        // Arrange
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(newUnionBridgeAddress, unionBridgeAddress.get());
    }

    @Test
    void setAddress_whenEmptyAddress_shouldNotStore() {
        // Arrange
        RskAddress rskAddress = new RskAddress(new byte[20]);

        // Act
        unionBridgeStorageProvider.setAddress(rskAddress);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);
    }

    @Test
    void setAddress_whenNull_shouldNotStore() {
        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save(allActivations);

        // Assert
        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);
    }

    @Test
    void setAddress_whenAddressSet_shouldNotStore() {
        // Act
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Assert
        RskAddress retrievedAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(retrievedAddress);
    }

    @Test
    void save_whenAddressSetAndBeforeRSKIP502_shouldNotStoreAnything() {
        // Arrange
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

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
        // Arrange
        StorageAccessor mockStorageAccessor = mock(StorageAccessor.class);
        UnionBridgeStorageProviderImpl unionBridgeStorageProviderUsingMockStorage = new UnionBridgeStorageProviderImpl(mockStorageAccessor);

        // Act
        unionBridgeStorageProviderUsingMockStorage.save(allActivations);

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void getAndSetAddress_whenFirstTime_shouldSaveNewAddress() {
        // Act & Assert

        // Check that the address is not present in the storage nor in cache initially
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isEmpty());

        // Set address
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Save the value
        unionBridgeStorageProvider.save(allActivations);

        // New instance of the storage provider to check the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, storedAddress.get());
    }

    @Test
    void getAndSetAddress_whenAddressAlreadyExistsInTheStorage_shouldOverrideWithTheNewOne() {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            newUnionBridgeAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act & Assert
        // Check that the address is not present in cache initially
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(newUnionBridgeAddress, unionBridgeAddress.get());

        // This should override the address in the storage

        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Overwrite the address in the storage
        unionBridgeStorageProvider.save(allActivations);

        // New instance of the storage provider to check the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);

        // Check that the new address is the retrieved from the storage
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, storedAddress.get());
    }
}
