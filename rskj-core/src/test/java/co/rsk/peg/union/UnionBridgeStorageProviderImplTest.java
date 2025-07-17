package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnionBridgeStorageProviderImplTest {

    private static final RskAddress storedUnionBridgeContractAddress = TestUtils.generateAddress(
        "unionBridgeContractAddress");
    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final Coin unionBridgeLockingCap = UnionBridgeMainNetConstants.getInstance().getInitialLockingCap();
    private static final Coin newUnionBridgeLockingCap = unionBridgeLockingCap.multiply(BigInteger.TWO);

    private static final Coin amountTransferredToUnionBridge = unionBridgeLockingCap.divide(BigInteger.TWO);
    private static final Coin newAmountTransferredToUnionBridge = newUnionBridgeLockingCap.divide(BigInteger.TWO);

    private static final boolean unionBridgeRequestEnabled = true;
    private static final boolean newUnionBridgeRequestEnabled = false;

    private static final boolean unionBridgeReleaseEnabled = true;
    private static final boolean newUnionBridgeReleaseEnabled = false;

    private static final AddressBasedAuthorizer INCREASE_LOCKING_CAP_AUTHORIZER =
        UnionBridgeMainNetConstants.getInstance().getChangeLockingCapAuthorizer();
    private static final RskAddress INCREASE_LOCKING_CAP_AUTHORIZER_1 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode("040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09")).getAddress()
    );
    private static final RskAddress INCREASE_LOCKING_CAP_AUTHORIZER_2 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode("04ee99364235a33edbd177c0293bd3e13f1c85b2ee6197e66aa7e975fb91183b08b30bf1227468980180e10092decaaeed0ae1c4bcf29d17993569bb3c1b274f83")).getAddress()
    );

    private static final AddressBasedAuthorizer TRANSFER_PERMISSIONS_AUTHORIZER =
        UnionBridgeMainNetConstants.getInstance().getChangeTransferPermissionsAuthorizer();
    private static final RskAddress TRANSFER_PERMISSIONS_AUTHORIZER_1 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode("0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282")).getAddress()
    );
    private static final RskAddress TRANSFER_PERMISSIONS_AUTHORIZER_2 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode("0486559d73a991df9e5eef1782c41959ecc7e334ef57ddcb6e4ebc500771a50f0c3b889afb9917165db383a9bf9a8e9b4f73abd542109ba06387f016f62df41b0f")).getAddress()
    );

    // Function names for election tests
    private static final String INCREASE_LOCKING_CAP_FUNCTION = "increaseLockingCap";
    private static final String TRANSFER_PERMISSIONS_FUNCTION = "setTransferPermissions";

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
    void setAddress_whenEmptyAddress_shouldSetEmptyAddress() {
        // Arrange
        RskAddress emptyAddress = new RskAddress(new byte[20]);

        // Act
        unionBridgeStorageProvider.setAddress(emptyAddress);

        // assert
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isPresent());
        assertEquals(emptyAddress, actualAddress.get());
        assertNoAddressIsStored();
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
        assertEquals(newUnionBridgeContractAddress, actualAddress.get());
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
        assertGivenWeisTransferredToUnionBridgeIsStored(actualWeisTransferred.get());
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
    @ValueSource(booleans = {true, false})
    void setUnionBridgeRequestEnabled_whenNoValueStored_shouldSetValue(boolean enabled) {
        // Act
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(enabled);

        // Assert
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();
        assertTrue(isUnionBridgeRequestEnabled.isPresent());
        assertEquals(enabled, isUnionBridgeRequestEnabled.get());

        // Check that the new value is not stored yet
        assertNoUnionBridgeRequestEnabledIsStored();

        // Call save to persist the value
        unionBridgeStorageProvider.save();
        // Check that the value is stored
        assertGivenUnionBridgeRequestEnabledIsStored(enabled);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setUnionBridgeRequestEnabled_whenValueStored_shouldOverrideWithTheNewOne(boolean enabled) {
        // Arrange
        boolean storedValue = !enabled;
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            storedValue, BridgeSerializationUtils::serializeBoolean);

        // Act
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(enabled);

        // Assert
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();
        assertTrue(isUnionBridgeRequestEnabled.isPresent());
        assertEquals(enabled, isUnionBridgeRequestEnabled.get());

        // Check that the new value is not stored yet
        assertGivenUnionBridgeRequestEnabledIsStored(storedValue);

        // Call save to persist the value
        unionBridgeStorageProvider.save();
        // Check that the value is stored
        assertGivenUnionBridgeRequestEnabledIsStored(enabled);
    }

    private void assertNoUnionBridgeRequestEnabledIsStored() {
        Optional<Long> retrievedUnionBridgeRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong);
        assertTrue(retrievedUnionBridgeRequestEnabled.isEmpty());
    }

    private void assertGivenUnionBridgeRequestEnabledIsStored(boolean expectedValue) {
        Boolean savedUnionBridgeRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean);

        assertEquals(expectedValue, savedUnionBridgeRequestEnabled);
    }

    @Test
    void isUnionBridgeRequestEnabled_whenNoValueStored_shouldReturnEmpty() {
        // Act
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();

        // Assert
        assertTrue(isUnionBridgeRequestEnabled.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isUnionBridgeRequestEnabled_whenValueStored_shouldReturnStoredValue(boolean requestEnabled) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
             requestEnabled, BridgeSerializationUtils::serializeBoolean);

        // Act
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();

        // Assert
        assertTrue(isUnionBridgeRequestEnabled.isPresent());
        assertEquals( requestEnabled, isUnionBridgeRequestEnabled.get());
    }

    @Test
    void isUnionBridgeRequestEnabled_whenValueSet_shouldReturnCachedValue() {
        // Arrange
        boolean requestEnabled = true;
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(requestEnabled);

        // Act
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();

        // Assert
        assertTrue(isUnionBridgeRequestEnabled.isPresent());
        assertEquals(requestEnabled, isUnionBridgeRequestEnabled.get());
        assertNoUnionBridgeRequestEnabledIsStored();
    }

    @Test
    void isUnionBridgeRequestEnabled_whenValueSetAndSaved_shouldReturnStoredValue() {
        // Arrange
        boolean requestEnabled = true;
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(requestEnabled);
        unionBridgeStorageProvider.save();

        // Act
        Optional<Boolean> isUnionBridgeRequestEnabled = unionBridgeStorageProvider.isUnionBridgeRequestEnabled();

        // Assert
        assertTrue(isUnionBridgeRequestEnabled.isPresent());
        assertEquals(requestEnabled, isUnionBridgeRequestEnabled.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setUnionBridgeReleaseEnabled_whenNoValueStored_shouldSetValue(boolean enabled) {
        // Act
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(enabled);

        // Assert
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();
        assertTrue(isUnionBridgeReleaseEnabled.isPresent());
        assertEquals(enabled, isUnionBridgeReleaseEnabled.get());

        // Check that the value is not stored yet
        assertNoUnionBridgeReleaseEnabledIsStored();

        // Call save to persist the value
        unionBridgeStorageProvider.save();
        // Check that the value is stored
        assertGivenUnionBridgeReleaseEnabledIsStored(enabled);
    }

    private void assertNoUnionBridgeReleaseEnabledIsStored() {
        Optional<Long> retrievedUnionBridgeReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong);
        assertTrue(retrievedUnionBridgeReleaseEnabled.isEmpty());
    }

    private void assertGivenUnionBridgeReleaseEnabledIsStored(boolean expectedValue) {
        Boolean savedUnionBridgeReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean);

        assertEquals(expectedValue, savedUnionBridgeReleaseEnabled);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setUnionBridgeReleaseEnabled_whenValueStored_shouldOverrideWithTheNewOne(boolean enabled) {
        // Arrange
        boolean storedValue = !enabled;
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            storedValue, BridgeSerializationUtils::serializeBoolean);

        // Act
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(enabled);

        // Assert
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();
        assertTrue(isUnionBridgeReleaseEnabled.isPresent());
        assertEquals(enabled, isUnionBridgeReleaseEnabled.get());

        // Check that the new value is not stored yet
        assertGivenUnionBridgeReleaseEnabledIsStored(storedValue);

        // Call save to persist the value
        unionBridgeStorageProvider.save();
        // Check that the value is stored
        assertGivenUnionBridgeReleaseEnabledIsStored(enabled);
    }

    @Test
    void isUnionBridgeReleaseEnabled_whenNoValueStored_shouldReturnEmpty() {
        // Act
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();

        // Assert
        assertTrue(isUnionBridgeReleaseEnabled.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isUnionBridgeReleaseEnabled_whenValueStored_shouldReturnStoredValue(boolean releaseEnabled) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
             releaseEnabled, BridgeSerializationUtils::serializeBoolean);

        // Act
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();

        // Assert
        assertTrue(isUnionBridgeReleaseEnabled.isPresent());
        assertEquals(releaseEnabled, isUnionBridgeReleaseEnabled.get());
    }

    @Test
    void isUnionBridgeReleaseEnabled_whenValueSet_shouldReturnCachedValue() {
        // Arrange
        boolean releaseEnabled = true;
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(releaseEnabled);

        // Act
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();

        // Assert
        assertTrue(isUnionBridgeReleaseEnabled.isPresent());
        assertEquals(releaseEnabled, isUnionBridgeReleaseEnabled.get());
        assertNoUnionBridgeReleaseEnabledIsStored();
    }

    @Test
    void isUnionBridgeReleaseEnabled_whenValueSetAndSaved_shouldReturnStoredValue() {
        // Arrange
        boolean releaseEnabled = true;
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(releaseEnabled);
        unionBridgeStorageProvider.save();

        // Act
        Optional<Boolean> isUnionBridgeReleaseEnabled = unionBridgeStorageProvider.isUnionBridgeReleaseEnabled();

        // Assert
        assertTrue(isUnionBridgeReleaseEnabled.isPresent());
        assertEquals(releaseEnabled, isUnionBridgeReleaseEnabled.get());
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void save_whenUnionBridgeRequestEnabledIsSet_shouldStoreValue(boolean unionBridgeRequestEnabled) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            unionBridgeRequestEnabled, BridgeSerializationUtils::serializeBoolean
        );

        // Act
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(newUnionBridgeRequestEnabled);
        unionBridgeStorageProvider.save();

        // Assert
        assertGivenUnionBridgeRequestEnabledIsStored(newUnionBridgeRequestEnabled);

        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredToUnionBridgeIsStored();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void save_whenUnionBridgeReleaseEnabledIsSet_shouldStoreValue(boolean unionBridgeReleaseEnabled) {
        // Arrange
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            unionBridgeReleaseEnabled, BridgeSerializationUtils::serializeBoolean
        );

        // Act
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(newUnionBridgeReleaseEnabled);
        unionBridgeStorageProvider.save();

        // Assert
        assertGivenUnionBridgeReleaseEnabledIsStored(newUnionBridgeReleaseEnabled);

        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredToUnionBridgeIsStored();
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

        // Simulate there is UNION_BRIDGE_REQUEST_ENABLED's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            unionBridgeRequestEnabled, BridgeSerializationUtils::serializeBoolean
        );
        // Simulate there is UNION_BRIDGE_RELEASE_ENABLED's value already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            unionBridgeReleaseEnabled, BridgeSerializationUtils::serializeBoolean
        );

        // Set the new values
        unionBridgeStorageProvider.setAddress(newUnionBridgeContractAddress);
        unionBridgeStorageProvider.setLockingCap(newUnionBridgeLockingCap);
        unionBridgeStorageProvider.increaseWeisTransferredToUnionBridge(newAmountTransferredToUnionBridge);
        unionBridgeStorageProvider.setUnionBridgeRequestEnabled(newUnionBridgeRequestEnabled);
        unionBridgeStorageProvider.setUnionBridgeReleaseEnabled(newUnionBridgeReleaseEnabled);

        // Act
        unionBridgeStorageProvider.save();

        // Assert
        assertGivenAddressIsStored(newUnionBridgeContractAddress);
        assertGivenLockingCapIsStored(newUnionBridgeLockingCap);
        Coin expectedAmountTransferredToUnionBridge = newAmountTransferredToUnionBridge.add(
            amountTransferredToUnionBridge);
        assertGivenWeisTransferredToUnionBridgeIsStored(expectedAmountTransferredToUnionBridge);
        assertGivenUnionBridgeRequestEnabledIsStored(newUnionBridgeRequestEnabled);
        assertGivenUnionBridgeReleaseEnabledIsStored(newUnionBridgeReleaseEnabled);
    }

    @Test
    void save_whenNothingIsSet_shouldNotStoreAnything() {
        // Act
        unionBridgeStorageProvider.save();

        // Assert
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredToUnionBridgeIsStored();
        assertNoUnionBridgeRequestEnabledIsStored();
        assertNoUnionBridgeReleaseEnabledIsStored();
        assertNoIncreaseLockingCapElectionIsStored();
        assertNoTransferPermissionElectionIsStored();
    }

    private void assertNoIncreaseLockingCapElectionIsStored() {
        ABICallElection retrievedIncreaseLockingCapElection = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION.getKey(),
            data -> data == null ? null
                : BridgeSerializationUtils.deserializeElection(data,
                    INCREASE_LOCKING_CAP_AUTHORIZER)
        );
        assertNull(retrievedIncreaseLockingCapElection);
    }
    
    private void assertGivenIncreaseLockingCapElectionIsStored(ABICallElection expectedElection) {
        ABICallElection retrievedElection = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION.getKey(),
            data -> BridgeSerializationUtils.deserializeElection(data, INCREASE_LOCKING_CAP_AUTHORIZER)
        );
        assertNotNull(retrievedElection);

        assertElectionsAreEqual(expectedElection, retrievedElection);
    }

    private static void assertElectionsAreEqual(ABICallElection expectedElection,
        ABICallElection actualElection) {
        assertEquals(expectedElection.getVotes().size(), actualElection.getVotes().size());
        // Verify each vote in the expected election is in the retrieved election
        for (ABICallSpec spec : expectedElection.getVotes().keySet()) {
            List<RskAddress> expectedVoters = expectedElection.getVotes().get(spec);

            assertTrue(actualElection.getVotes().containsKey(spec));
            List<RskAddress> actualVoters = actualElection.getVotes().get(spec);
            assertEquals(expectedVoters.size(), actualVoters.size());

            for (int i = 0; i < expectedVoters.size(); i++) {
                assertEquals(expectedVoters.get(i), actualVoters.get(i));
            }
        }
    }

    private void assertNoTransferPermissionElectionIsStored() {
        ABICallElection retrievedTransferPermissionElection = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION.getKey(),
            data -> data == null ? null
                : BridgeSerializationUtils.deserializeElection(data,
                    TRANSFER_PERMISSIONS_AUTHORIZER)
        );
        assertNull(retrievedTransferPermissionElection);
    }
    
    private void assertGivenTransferPermissionsElectionIsStored(ABICallElection expectedElection) {
        ABICallElection retrievedElection = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION.getKey(),
            data -> BridgeSerializationUtils.deserializeElection(data, TRANSFER_PERMISSIONS_AUTHORIZER)
        );
        assertNotNull(retrievedElection);

        assertElectionsAreEqual(expectedElection, retrievedElection);
    }

    private void assertGivenWeisTransferredToUnionBridgeIsStored(
        Coin expectedTransferredToUnionBridge) {
        Coin savedWeisTransferredToUnionBridge = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin);
        assertNotNull(savedWeisTransferredToUnionBridge);

        assertEquals(expectedTransferredToUnionBridge, savedWeisTransferredToUnionBridge);
    }
    
    @Test
    void getIncreaseLockingCapElection_whenNoElectionStoredOrSet_shouldReturnNewEmptyElection() {
        // Act
        ABICallElection increaseLockingCapElection = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);

        // Assert
        assertNotNull(increaseLockingCapElection);
        assertTrue(increaseLockingCapElection.getVotes().isEmpty());
    }

    @Test
    void getIncreaseLockingCapElection_whenElectionStored_shouldReturnStoredElection() {
        // Arrange
        ABICallElection expectedElection = new ABICallElection(INCREASE_LOCKING_CAP_AUTHORIZER);

        // Add a vote to the election
        ABICallSpec increaseLockingCapVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newUnionBridgeLockingCap)
        });
        expectedElection.vote(increaseLockingCapVote, INCREASE_LOCKING_CAP_AUTHORIZER_1);

        // To simulate, there is an election already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION.getKey(),
            expectedElection, BridgeSerializationUtils::serializeElection
        );

        // Act
        ABICallElection actualElection = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);

        // Assert
        assertNotNull(actualElection);
        assertEquals(1, actualElection.getVotes().size());
        assertTrue(actualElection.getVotes().containsKey(increaseLockingCapVote));
        List<RskAddress> voters = actualElection.getVotes().get(increaseLockingCapVote);
        assertEquals(1, voters.size());
        assertEquals(INCREASE_LOCKING_CAP_AUTHORIZER_1, voters.get(0));
    }

    @Test
    void getIncreaseLockingCapElection_whenVoteToCacheElection_shouldAddVotesToCacheElection() {
        // Arrange
        // First, store an election in the repository
        ABICallElection storedIncreaseLockingCapElection = new ABICallElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        ABICallSpec firstVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newUnionBridgeLockingCap)});
        storedIncreaseLockingCapElection.vote(firstVote, INCREASE_LOCKING_CAP_AUTHORIZER_1);

        // to simulate, there is an election already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION.getKey(),
            storedIncreaseLockingCapElection, BridgeSerializationUtils::serializeElection
        );
        
        // Then, get the election once to cache it
        ABICallElection cachedElection = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        
        // Add a second vote for the same locking cap
        ABICallSpec secondVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newUnionBridgeLockingCap)});
        cachedElection.vote(secondVote, INCREASE_LOCKING_CAP_AUTHORIZER_2);
        
        // Act
        ABICallElection actualElection = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        
        // Assert
        assertNotNull(actualElection);
        // Since both authorizers voted for the same locking cap, we expect only one vote in the election
        assertEquals(1, actualElection.getVotes().size());
        // Both votes should be present in the election
        assertTrue(actualElection.getVotes().containsKey(firstVote));
        assertTrue(actualElection.getVotes().containsKey(secondVote));

        // Both votes should be equal since they are for the same value
        assertEquals(firstVote, secondVote);

        // Check that both authorizers voted for the same locking cap
        List<RskAddress> votingAddresses = actualElection.getVotes().get(firstVote);
        assertEquals(2, votingAddresses.size());
        assertEquals(INCREASE_LOCKING_CAP_AUTHORIZER_1, votingAddresses.get(0));
        assertEquals(INCREASE_LOCKING_CAP_AUTHORIZER_2, votingAddresses.get(1));
    }
    
    @Test
    void save_whenIncreaseLockingCapElectionIsSet_shouldPersistElectionState() {
        // Arrange
        // Get an empty election
        ABICallElection election = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        assertNotNull(election);
        assertTrue(election.getVotes().isEmpty());
        
        // Add two votes for different locking caps
        ABICallSpec firstVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newUnionBridgeLockingCap)
        });
        election.vote(firstVote, INCREASE_LOCKING_CAP_AUTHORIZER_1);
        
        // Create a different locking cap value for the second vote
        Coin differentLockingCap = newUnionBridgeLockingCap.multiply(BigInteger.valueOf(3));
        ABICallSpec secondVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(differentLockingCap)
        });
        election.vote(secondVote, INCREASE_LOCKING_CAP_AUTHORIZER_2);
        
        // Act
        unionBridgeStorageProvider.save();
        
        // Assert
        assertGivenIncreaseLockingCapElectionIsStored(election);
    }
    
    @Test
    void save_whenIncreaseLockingCapElectionStoredAndMoreVotesAdded_shouldPersistUpdatedElection() {
        // Arrange
        // Store an election with one vote
        ABICallElection initialElection = new ABICallElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        ABICallSpec initialVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(unionBridgeLockingCap)
        });
        initialElection.vote(initialVote, INCREASE_LOCKING_CAP_AUTHORIZER_1);
        
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION.getKey(),
            initialElection, BridgeSerializationUtils::serializeElection
        );
        
        // Get the stored election
        ABICallElection retrievedElection = unionBridgeStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        assertNotNull(retrievedElection);
        assertEquals(1, retrievedElection.getVotes().size());
        
        // Add one more vote for a different locking cap
        ABICallSpec additionalVote = new ABICallSpec(INCREASE_LOCKING_CAP_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newUnionBridgeLockingCap)
        });
        retrievedElection.vote(additionalVote, INCREASE_LOCKING_CAP_AUTHORIZER_2);
        
        // Act
        unionBridgeStorageProvider.save();
        
        // Create a new storage provider instance with no cache
        UnionBridgeStorageProviderImpl newStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        
        // Assert
        // Get the election from the new provider
        ABICallElection finalElection = newStorageProvider.getIncreaseLockingCapElection(INCREASE_LOCKING_CAP_AUTHORIZER);
        assertNotNull(finalElection);
        assertEquals(2, finalElection.getVotes().size());
        
        // Verify both votes are present
        assertTrue(finalElection.getVotes().containsKey(initialVote));
        assertTrue(finalElection.getVotes().containsKey(additionalVote));
        
        // Verify voters for each vote
        List<RskAddress> initialVoters = finalElection.getVotes().get(initialVote);
        assertEquals(1, initialVoters.size());
        assertEquals(INCREASE_LOCKING_CAP_AUTHORIZER_1, initialVoters.get(0));
        
        List<RskAddress> additionalVoters = finalElection.getVotes().get(additionalVote);
        assertEquals(1, additionalVoters.size());
        assertEquals(INCREASE_LOCKING_CAP_AUTHORIZER_2, additionalVoters.get(0));
    }
    
    @Test
    void getTransferPermissionsElection_whenNoElectionStoredOrSet_shouldReturnNewEmptyElection() {
        // Act
        ABICallElection transferPermissionsElection = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);

        // Assert
        assertNotNull(transferPermissionsElection);
        assertTrue(transferPermissionsElection.getVotes().isEmpty());
    }
    
    @Test
    void getTransferPermissionsElection_whenElectionStored_shouldReturnStoredElection() {
        // Arrange
        ABICallElection expectedElection = new ABICallElection(TRANSFER_PERMISSIONS_AUTHORIZER);

        // Add a vote to the election
        ABICallSpec transferPermissionsVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(true),
            BridgeSerializationUtils.serializeBoolean(false)
        });
        expectedElection.vote(transferPermissionsVote, TRANSFER_PERMISSIONS_AUTHORIZER_1);

        // To simulate, there is an election already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION.getKey(),
            expectedElection, BridgeSerializationUtils::serializeElection
        );

        // Act
        ABICallElection actualElection = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);

        // Assert
        assertNotNull(actualElection);
        assertEquals(1, actualElection.getVotes().size());
        assertTrue(actualElection.getVotes().containsKey(transferPermissionsVote));
        List<RskAddress> voters = actualElection.getVotes().get(transferPermissionsVote);
        assertEquals(1, voters.size());
        assertEquals(TRANSFER_PERMISSIONS_AUTHORIZER_1, voters.get(0));
    }
    
    @Test
    void getTransferPermissionsElection_whenVoteToCacheElection_shouldAddVotesToCacheElection() {
        // Arrange
        // First, store an election in the repository
        ABICallElection storedTransferPermissionsElection = new ABICallElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        ABICallSpec firstVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(true),
            BridgeSerializationUtils.serializeBoolean(false)
        });
        storedTransferPermissionsElection.vote(firstVote, TRANSFER_PERMISSIONS_AUTHORIZER_1);

        // to simulate, there is an election already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION.getKey(),
            storedTransferPermissionsElection, BridgeSerializationUtils::serializeElection
        );
        
        // Then, get the election once to cache it
        ABICallElection cachedElection = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        
        // Add a second vote for the same permissions
        ABICallSpec secondVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(true),
            BridgeSerializationUtils.serializeBoolean(false)
        });
        cachedElection.vote(secondVote, TRANSFER_PERMISSIONS_AUTHORIZER_2);
        
        // Act
        ABICallElection actualElection = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        
        // Assert
        assertNotNull(actualElection);
        // Since both authorizers voted for the same permissions, we expect only one vote in the election
        assertEquals(1, actualElection.getVotes().size());
        // Both votes should be present in the election
        assertTrue(actualElection.getVotes().containsKey(firstVote));
        assertTrue(actualElection.getVotes().containsKey(secondVote));

        // Both votes should be equal since they are for the same value
        assertEquals(firstVote, secondVote);

        // Check that both authorizers voted for the same permissions
        List<RskAddress> votingAddresses = actualElection.getVotes().get(firstVote);
        assertEquals(2, votingAddresses.size());
        assertEquals(TRANSFER_PERMISSIONS_AUTHORIZER_1, votingAddresses.get(0));
        assertEquals(TRANSFER_PERMISSIONS_AUTHORIZER_2, votingAddresses.get(1));
    }
    
    @Test
    void save_whenTransferPermissionsElectionIsSet_shouldPersistElectionState() {
        // Arrange
        // Get an empty election
        ABICallElection election = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        assertNotNull(election);
        assertTrue(election.getVotes().isEmpty());
        
        // Add two votes for different permission combinations
        ABICallSpec firstVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(true),
            BridgeSerializationUtils.serializeBoolean(false)
        });
        election.vote(firstVote, TRANSFER_PERMISSIONS_AUTHORIZER_1);
        
        ABICallSpec secondVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(false),
            BridgeSerializationUtils.serializeBoolean(true)
        });
        election.vote(secondVote, TRANSFER_PERMISSIONS_AUTHORIZER_2);
        
        // Act
        unionBridgeStorageProvider.save();
        
        // Assert
        assertGivenTransferPermissionsElectionIsStored(election);
    }
    
    @Test
    void save_whenTransferPermissionsElectionStoredAndMoreVotesAdded_shouldPersistUpdatedElection() {
        // Arrange
        // Store an election with one vote
        ABICallElection initialElection = new ABICallElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        ABICallSpec initialVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(true),
            BridgeSerializationUtils.serializeBoolean(true)
        });
        initialElection.vote(initialVote, TRANSFER_PERMISSIONS_AUTHORIZER_1);
        
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION.getKey(),
            initialElection, BridgeSerializationUtils::serializeElection
        );
        
        // Get the stored election
        ABICallElection retrievedElection = unionBridgeStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        assertNotNull(retrievedElection);
        assertEquals(1, retrievedElection.getVotes().size());
        
        // Add one more vote for a different permission combination
        ABICallSpec additionalVote = new ABICallSpec(TRANSFER_PERMISSIONS_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(false),
            BridgeSerializationUtils.serializeBoolean(false)
        });
        retrievedElection.vote(additionalVote, TRANSFER_PERMISSIONS_AUTHORIZER_2);
        
        // Act
        unionBridgeStorageProvider.save();
        
        // Create a new storage provider instance with no cache
        UnionBridgeStorageProviderImpl newStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        
        // Assert
        // Get the election from the new provider
        ABICallElection finalElection = newStorageProvider.getTransferPermissionsElection(TRANSFER_PERMISSIONS_AUTHORIZER);
        assertNotNull(finalElection);
        assertEquals(2, finalElection.getVotes().size());
        
        // Verify both votes are present
        assertTrue(finalElection.getVotes().containsKey(initialVote));
        assertTrue(finalElection.getVotes().containsKey(additionalVote));
        
        // Verify voters for each vote
        List<RskAddress> initialVoters = finalElection.getVotes().get(initialVote);
        assertEquals(1, initialVoters.size());
        assertEquals(TRANSFER_PERMISSIONS_AUTHORIZER_1, initialVoters.get(0));
        
        List<RskAddress> additionalVoters = finalElection.getVotes().get(additionalVote);
        assertEquals(1, additionalVoters.size());
        assertEquals(TRANSFER_PERMISSIONS_AUTHORIZER_2, additionalVoters.get(0));
    }
}
