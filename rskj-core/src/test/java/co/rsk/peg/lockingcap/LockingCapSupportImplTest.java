package co.rsk.peg.lockingcap;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockingCapSupportImplTest {
    private final LockingCapConstants constants = LockingCapMainNetConstants.getInstance();
    private LockingCapSupport lockingCapSupport;
    private LockingCapStorageProvider lockingCapStorageProvider;
    private ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private SignatureCache signatureCache;
    private StorageAccessor bridgeStorageAccessor;

    @BeforeEach
    void setUp() {
        bridgeStorageAccessor = new InMemoryStorage();
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
    }

    @Test
    void getLockingCap_whenNoPreviousValueExists_ShouldReturnInitialValue() {
        // Arrange
        Optional<Coin> expectedLockingCap = Optional.of(constants.getInitialValue());

        // Act
        Optional<Coin> actualLockingCap = lockingCapSupport.getLockingCap();

        // Assert
        assertEquals(expectedLockingCap, actualLockingCap);
    }

    @Test
    void getLockingCap_whenAPreviousLockingCapValueExistsInStorage_ShouldReturnPreviousValue() {
        // Arrange
        Coin previousLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(previousLockingCap);
        lockingCapSupport.save();
        // Recreate LockingCapSupport to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        // Act
        Optional<Coin> actualLockingCap = lockingCapSupport.getLockingCap();

        // Assert
        assertEquals(Optional.of(previousLockingCap), actualLockingCap);
    }

    @Test
    void getLockingCap_prePapyrus200_whenLockingCapIsNotSet_ShouldReturnEmpty() {
        // Arrange
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        // Act
        Optional<Coin> actualLockingCap = lockingCapSupport.getLockingCap();

        // Assert
        assertEquals(Optional.empty(), actualLockingCap);
    }

    @Test
    void increaseLockingCap_whenNotExistsPreviousValueAndTakeDefaultInitialValueAndNewLockingCapIsGreaterThanInitialValue_ShouldReturnTrue() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertTrue(actualResult);
        assertEquals(Optional.of(newLockingCap), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNotExistsPreviousValueAndTakeDefaultInitialValueAndNewLockingCapIsLessThanInitialValue_ShouldReturnFalse() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().subtract(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(constants.getInitialValue()), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenExistInStorageAPreviousValueAndNewLockingCapIsGreaterThanPreviousValue_ShouldReturnTrue() {
        // Arrange
        Coin previousLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(previousLockingCap);
        lockingCapSupport.save();
        // Recreate LockingCapSupport to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        Coin newLockingCap = previousLockingCap.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertTrue(actualResult);
        assertEquals(Optional.of(newLockingCap), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenExistInStorageAPreviousValueAndNewLockingCapIsLessThanPreviousValue_ShouldReturnFalse() {
        // Arrange
        Coin previousLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(previousLockingCap);
        lockingCapSupport.save();
        // Recreate LockingCapSupport to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        Coin newLockingCap = previousLockingCap.subtract(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(previousLockingCap), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenAnUnauthorizedCallerRequestToIncreaseLockingCapValue_ShouldReturnFalse() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.UNAUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(constants.getInitialValue()), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsGreaterThanMaxLockingCap_ShouldReturnFalse() {
        // Arrange
        Coin previousLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(previousLockingCap);
        lockingCapSupport.save();
        // Recreate LockingCapSupport to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        Coin maxLockingCap = previousLockingCap.multiply(constants.getIncrementsMultiplier());
        Coin newLockingCap = maxLockingCap.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(previousLockingCap), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsEqualToMaxLockingCap_ShouldReturnTrue() {
        // Arrange
        Coin previousLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        lockingCapStorageProvider.setLockingCap(previousLockingCap);
        lockingCapSupport.save();
        // Recreate LockingCapSupport to load the previous value from storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        // The new locking cap is the maximum value that can be set
        Coin newLockingCap = previousLockingCap.multiply(constants.getIncrementsMultiplier());
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertTrue(actualResult);
        assertEquals(Optional.of(newLockingCap), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsEqualToLockingCapValueAlreadyExist_ShouldReturnTrue() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue();
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertTrue(actualResult);
        assertEquals(Optional.of(constants.getInitialValue()), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsZero_ShouldReturnFalse() {
        // Arrange
        Coin newLockingCap = Coin.ZERO;
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(constants.getInitialValue()), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsNegative_ShouldReturnFalse() {
        // Arrange
        Coin newLockingCap = Coin.NEGATIVE_SATOSHI;
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.of(constants.getInitialValue()), lockingCapSupport.getLockingCap());
    }

    @Test
    void increaseLockingCap_prePapyrus200_whenLockingCapIsNotSet_ShouldReturnFalse() {
        // Arrange
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean actualResult = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(actualResult);
        assertEquals(Optional.empty(), lockingCapSupport.getLockingCap());
    }

    @Test
    void save_whenIsIncreasedLockingCapValue_ShouldSaveLockingCap() {
        // Arrange
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());
        lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Act
        lockingCapSupport.save();

        // Assert
        // Recreate LockingCapSupport to load the previous value from storage and make sure it was saved
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
        assertEquals(Optional.of(newLockingCap), lockingCapStorageProvider.getLockingCap(activations));
    }

    @Test
    void save_prePapyrus200_whenIsAttemptedIncreaseLockingCapValue_ShouldNotSaveLockingCap() {
        // Arrange
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
        Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());
        lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Act
        lockingCapSupport.save();

        // Assert
        // Recreate LockingCapSupport to load the previous value from storage and make sure it was not saved
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
        assertEquals(Optional.empty(), lockingCapStorageProvider.getLockingCap(activations));
    }
}