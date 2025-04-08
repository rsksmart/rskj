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
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionStorageProviderImplTest {

    private StorageAccessor mockStorageAccessor;
    private UnionStorageProviderImpl unionStorageProviderUsingMockStorage;

    private StorageAccessor storageAccessor;
    private UnionStorageProviderImpl unionStorageProvider;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionStorageProvider = new UnionStorageProviderImpl(storageAccessor);

        mockStorageAccessor = mock(StorageAccessor.class);
        unionStorageProviderUsingMockStorage = new UnionStorageProviderImpl(mockStorageAccessor);
    }

    @Test
    void getUnionAddress_whenNoAddressStoredOrSet_shouldReturnEmpty() {
        // Act
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();

        // Assert
        assertTrue(unionBridgeAddress.isEmpty());
    }

    private static Stream<Arguments> validAddressProvider() {
        return Stream.of(
            Arguments.of(new RskAddress(new byte[20])), // empty address: 0000000000000000000000000000000000
            Arguments.of(TestUtils.generateAddress("address"))
        );
    }

    @ParameterizedTest
    @MethodSource("validAddressProvider")
    void getUnionAddress_whenAddressStored_shouldReturnStoredAddress(RskAddress rskAddress) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            rskAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @ParameterizedTest
    @MethodSource("validAddressProvider")
    void getUnionAddress_whenAddressSet_shouldReturnCacheAddress(RskAddress rskAddress) {
        // Arrange
        unionStorageProvider.setUnionAddress(rskAddress);

        // Act
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @ParameterizedTest
    @MethodSource("validAddressProvider")
    void getUnionAddress_whenAddressSetAndStored_shouldReturnSetAddress(RskAddress rskAddress) {
        // Arrange
        unionStorageProvider.setUnionAddress(rskAddress);
        unionStorageProvider.save();

        // Act
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();

        // Assert
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());
    }

    @Test
    void setUnionAddress_whenAddressSet_shouldNotStore() {
        // Arrange
        RskAddress rskAddress = TestUtils.generateAddress("address");

        // Act
        unionStorageProvider.setUnionAddress(rskAddress);

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @Test
    void save_whenNoAddressSet_shouldNotStoreAnything() {
        // Act
        unionStorageProviderUsingMockStorage.save();

        // Assert
        verify(mockStorageAccessor, never())
            .saveToRepository(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("validAddressProvider")
    void getAndSetUnionAddress_whenFirstTime_ok(RskAddress rskAddress) {
        // Act & Assert
        // Check that the address is not present in the storage nor in cache initially
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();
        assertTrue(unionBridgeAddress.isEmpty());

        // Set the address
        unionStorageProvider.setUnionAddress(rskAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionStorageProvider.getUnionAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(rskAddress, cachedAddress.get());

        // Check that the address is now present in the storage
        unionStorageProvider.save();

        // New instance of the storage provider to check the storage
        unionStorageProvider = new UnionStorageProviderImpl(storageAccessor);
        Optional<RskAddress> storedAddress = unionStorageProvider.getUnionAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(rskAddress, storedAddress.get());
    }

    @ParameterizedTest
    @MethodSource("validAddressProvider")
    void getAndSetUnionAddress_whenAddressAlreadyExistInTheStorage_shouldOverrideWithTheNewOne(RskAddress rskAddress) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            rskAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // Act & Assert
        // Check that the address is not present in the storage nor in cache initially
        Optional<RskAddress> unionBridgeAddress = unionStorageProvider.getUnionAddress();
        assertTrue(unionBridgeAddress.isPresent());
        assertEquals(rskAddress, unionBridgeAddress.get());

        // This should override the address in the storage
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newUnionBridgeAddress");
        unionStorageProvider.setUnionAddress(newUnionBridgeAddress);

        // Check that the address is now present in the cache
        Optional<RskAddress> cachedAddress = unionStorageProvider.getUnionAddress();
        assertTrue(cachedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, cachedAddress.get());

        // Overwrite the address in the storage
        unionStorageProvider.save();

        // New instance of the storage provider to check the storage
        unionStorageProvider = new UnionStorageProviderImpl(storageAccessor);

        // Check that the new address is the retrieved from the storage
        Optional<RskAddress> storedAddress = unionStorageProvider.getUnionAddress();
        assertTrue(storedAddress.isPresent());
        assertEquals(newUnionBridgeAddress, storedAddress.get());
    }
}
