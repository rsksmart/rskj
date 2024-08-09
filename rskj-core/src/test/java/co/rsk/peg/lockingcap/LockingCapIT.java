package co.rsk.peg.lockingcap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class LockingCapIT {

    private final LockingCapConstants constants = LockingCapMainNetConstants.getInstance();
    private LockingCapSupport lockingCapSupport;
    private LockingCapStorageProvider lockingCapStorageProvider;
    private SignatureCache signatureCache;
    private ActivationConfig.ForBlock activations;
    private StorageAccessor bridgeStorageAccessor;
    private Coin currentLockingCap; // it is used to guarantee the value from getLockingCap() contains the value expected

    @BeforeAll
    void setUp() {
        bridgeStorageAccessor = new InMemoryStorage();
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    }

    @Test
    @Order(-1)
    void increaseLockingCap_prePapyrus200_whenLockingCapIsNotSet_shouldNotSavedNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Previously to the genesis of the Locking Cap
        activations = ActivationConfigsForTest.wasabi100().forBlock(0);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);

        // Setting up new Locking Cap value
        Coin newLockingCap = constants.getInitialValue();
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        assertFalse(isIncrease);
    }

    @Test
    @Order(0)
    void getInitialValue_whenFirstTimeGettingLockingCap_shouldReturnInitialValue() {
        // Arrange
        activations = ActivationConfigsForTest.all().forBlock(0);
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, constants, signatureCache);
        // The first time the Locking Cap is requested, it should return the initial value
        Coin initialValue = constants.getInitialValue();
        currentLockingCap = initialValue;

        // Actual / Assert
        assertLockingCapValue(initialValue);
    }

    @Test
    @Order(1)
    void increaseLockingCap_whenNewValueIsGreaterThanCurrentLockingCap_shouldSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);
        if (isIncrease){
            lockingCapSupport.save();
            currentLockingCap = newLockingCap;
        }

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(newLockingCap);
    }

    @Test
    @Order(2)
    void increaseLockingCap_whenPreviousValueExistsInStorageAndNewLockingCapIsLessThanPreviousValue_shouldNotSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap.subtract(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(currentLockingCap);
        assertFalse(isIncrease);
    }

    @Test
    @Order(3)
    void increaseLockingCap_whenPreviousValueExistsInStorageAndNewLockingCapIsGreaterThanPreviousValue_shouldSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);
        if (isIncrease){
            lockingCapSupport.save();
            currentLockingCap = newLockingCap;
        }

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(newLockingCap);
    }

    @Test
    @Order(4)
    void increaseLockingCap_whenAnUnauthorizedCallerRequestToIncreaseLockingCapValue_shouldNotSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.UNAUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(currentLockingCap);
        assertFalse(isIncrease);
    }

    @Test
    @Order(5)
    void increaseLockingCap_whenNewLockingCapIsGreaterThanMaxLockingCap_shouldNotSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin maxLockingCapVoteValueAllowed = currentLockingCap.multiply(constants.getIncrementsMultiplier());
        Coin newLockingCap = maxLockingCapVoteValueAllowed.add(Coin.SATOSHI);
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(currentLockingCap);
        assertFalse(isIncrease);
    }

    @Test
    @Order(6)
    void increaseLockingCap_whenNewLockingCapIsEqualToMaxLockingCap_shouldSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap.multiply(constants.getIncrementsMultiplier());
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);
        if (isIncrease){
            lockingCapSupport.save();
            currentLockingCap = newLockingCap;
        }

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(newLockingCap);
    }

    @Test
    @Order(7)
    void increaseLockingCap_whenNewLockingCapIsZero_shouldThrowLockingCapIllegalArgumentException() {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = Coin.ZERO;
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Actual / Assert
        assertThrows(LockingCapIllegalArgumentException.class, () -> lockingCapSupport.increaseLockingCap(tx, newLockingCap));
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(currentLockingCap);
    }

    @Test
    @Order(8)
    void increaseLockingCap_whenNewLockingCapIsNegative_shouldThrowLockingCapIllegalArgumentException() {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = Coin.NEGATIVE_SATOSHI;
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Actual / Assert
        assertThrows(LockingCapIllegalArgumentException.class, () -> lockingCapSupport.increaseLockingCap(tx, newLockingCap));
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(currentLockingCap);
    }

    @Test
    @Order(9)
    void increaseLockingCap_whenNewValueIsEqualToCurrentValue_shouldSaveNewLockingCapValue() throws LockingCapIllegalArgumentException {
        // Arrange
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // Ensure the value is the same as the one saved in the storage
        assertLockingCapValue(currentLockingCap);

        // Setting up new Locking Cap value
        Coin newLockingCap = currentLockingCap;
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, LockingCapCaller.AUTHORIZED.getRskAddress());

        // Act
        boolean isIncrease = lockingCapSupport.increaseLockingCap(tx, newLockingCap);
        if (isIncrease){
            lockingCapSupport.save();
            currentLockingCap = newLockingCap;
        }

        // Assert
        // Recreate LockingCapStorageProvider to clear cached value and make sure it was saved in the storage
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        assertLockingCapValue(newLockingCap);
    }

    private void assertLockingCapValue(Coin expectedLockingCap) {
        // Act
        Optional<Coin> actualLockingCap = lockingCapSupport.getLockingCap();

        // Assert
        assertTrue(actualLockingCap.isPresent());
        assertEquals(expectedLockingCap, actualLockingCap.get());
    }
}
