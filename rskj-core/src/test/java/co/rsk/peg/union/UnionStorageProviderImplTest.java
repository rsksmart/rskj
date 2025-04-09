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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnionStorageProviderImplTest {

    private StorageAccessor mockStorageAccessor;
    private UnionBridgeStorageProviderImpl unionBridgeStorageProviderUsingMockStorage;

    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProviderImpl unionBridgeStorageProvider;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);

        mockStorageAccessor = mock(StorageAccessor.class);
        unionBridgeStorageProviderUsingMockStorage = new UnionBridgeStorageProviderImpl(mockStorageAccessor);
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
        RskAddress rskAddress = TestUtils.generateAddress("address");

        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            rskAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressSet_shouldReturnCacheAddress() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");
        unionBridgeStorageProvider.setAddress(rskAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @Test
    void getAddress_whenAddressSetAndStored_shouldReturnSetAddress() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");
        unionBridgeStorageProvider.setAddress(rskAddress);
        unionBridgeStorageProvider.save();

        // Act
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @Test
    void setAddress_whenEmptyAddress_shouldNotStore() {
        // Arrange
        RskAddress rskAddress = new RskAddress(new byte[20]);

        // Act
        unionBridgeStorageProvider.setAddress(rskAddress);
        unionBridgeStorageProvider.save();

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void setAddress_whenNull_shouldNotStore() {
        // Act
        unionBridgeStorageProvider.setAddress(null);
        unionBridgeStorageProvider.save();

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void setAddress_whenAddressSet_shouldNotStore() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");

        // Act
        unionBridgeStorageProvider.setAddress(rskAddress);

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void save_whenNoAddressSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProviderUsingMockStorage.save();

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void getAndSetAddress_whenFirstTime_shouldSaveNewAddress() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");

        // Act & Assert

        // Check that the address is not present in the storage nor in cache initially
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isEmpty());

        // Set address
        unionBridgeStorageProvider.setAddress(rskAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(rskAddress, cachedAddress.get());

        // Save the value
        unionBridgeStorageProvider.save();

        // New instance of the storage provider to check the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(rskAddress, storedAddress.get());
    }

    @Test
    void getAndSetAddress_whenAddressAlreadyExistsInTheStorage_shouldOverrideWithTheNewOne() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            rskAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act & Assert
        // Check that the address is not present in cache initially
        Optional<RskAddress> unionBridgeAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());

        // This should override the address in the storage
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionBridgeStorageProvider.setAddress(newUnionBridgeAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Overwrite the address in the storage
        unionBridgeStorageProvider.save();

        // New instance of the storage provider to check the storage
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);

        // Check that the new address is the retrieved from the storage
        Optional<RskAddress> storedAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, storedAddress.get());
    }
}
