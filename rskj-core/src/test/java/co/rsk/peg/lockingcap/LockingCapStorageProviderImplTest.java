package co.rsk.peg.lockingcap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockingCapStorageProviderImplTest {

    private final LockingCapConstants constants = LockingCapMainNetConstants.getInstance();
    private LockingCapStorageProvider lockingCapStorageProvider;
    private ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private StorageAccessor bridgeStorageAccessor;

    @BeforeEach
    void setUp() {
        bridgeStorageAccessor = new InMemoryStorage();
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
    }

    @Test
    void getLockingCap_whenNoPreviousValueExists_shouldReturnOptionalEmpty() {
        // Arrange
        Optional<Coin> expectedLockingCap = Optional.empty();

        // Act
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);

        // Assert
        assertEquals(expectedLockingCap, actualLockingCap);
    }

    @Test
    void getLockingCap_whenLockingCapIsSavedInStorage_shouldReturnSavedValue() {
        // Arrange
        Coin expectedLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(expectedLockingCap);
        lockingCapStorageProvider.save(activations);
        // Recreate LockingCapStorageProvider to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);

        // Act
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);

        // Assert
        assertEquals(Optional.of(expectedLockingCap), actualLockingCap);
    }

    @Test
    void getLockingCap_prePapyrus200_shouldReturnOptionalEmpty() {
        // Arrange
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        Optional<Coin> expectedLockingCap = Optional.empty();

        // Act
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);

        // Assert
        assertEquals(expectedLockingCap, actualLockingCap);
    }

    @Test
    void setLockingCap_whenANewLockingCapValueIsSet_shouldSetLockingCap() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);

        // Act
        lockingCapStorageProvider.setLockingCap(newLockingCap);

        // Assert
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);
        assertEquals(Optional.of(newLockingCap), actualLockingCap);
    }

    @Test
    void setLockingCap_whenIsSetNullValue_shouldReturnOptionalEmpty() {
        // Arrange
        Optional<Coin> expectedLockingCap = Optional.empty();

        // Act
        lockingCapStorageProvider.setLockingCap(null);

        // Assert
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);
        assertEquals(expectedLockingCap, actualLockingCap);
    }

    @Test
    void save_whenIsSavedANewLockingCapValue_shouldSaveLockingCap() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(newLockingCap);

        // Act
        lockingCapStorageProvider.save(activations);

        // Assert
        // Recreate LockingCapStorageProvider to load the previous value from storage and make sure it was saved
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);
        assertEquals(Optional.of(newLockingCap), actualLockingCap);
    }

    @Test
    void save_prePapyrus200_whenIsAttemptedSaveANewLockingCapValue_shouldNotSaveLockingCap() {
        // Arrange
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        Optional<Coin> expectedLockingCap = Optional.empty();
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(newLockingCap);

        // Act
        lockingCapStorageProvider.save(activations);

        // Assert
        // Recreate LockingCapStorageProvider to load the previous value from storage and make sure it was not saved
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        Optional<Coin> actualLockingCap = lockingCapStorageProvider.getLockingCap(activations);
        assertEquals(expectedLockingCap, actualLockingCap);
    }
}
