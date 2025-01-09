package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistSupportImplTest {
    private final WhitelistConstants whitelistConstants = WhitelistMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = whitelistConstants.getBtcParams();
    private WhitelistSupport whitelistSupport;
    private SignatureCache signatureCache;
    private StorageAccessor inMemoryStorage;
    private Address btcAddress;
    private Address secondBtcAddress;

    @BeforeEach
    void setUp() {
        inMemoryStorage = new InMemoryStorage();
        WhitelistStorageProvider whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        ActivationConfig.ForBlock activationConfig = ActivationConfigsForTest.all().forBlock(0);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        whitelistSupport = new WhitelistSupportImpl(whitelistConstants, whitelistStorageProvider, activationConfig, signatureCache);
        btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "btcAddress");
        secondBtcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "secondBtcAddress");
    }

    @Test
    void getLockWhitelistSize_whenLockWhitelistIsEmpty_shouldReturnZero() {
        int actualSize = whitelistSupport.getLockWhitelistSize();

        assertEquals(0, actualSize);
    }

    @Test
    void getLockWhitelistSize_whenLockWhitelistHasEntries_shouldReturnOne() {
        saveInMemoryStorageOneOffWhiteListEntry();

        int actualSize = whitelistSupport.getLockWhitelistSize();

        assertEquals(1, actualSize);
    }

    @Test
    void getLockWhitelistEntryByIndex_whenLockWhitelistIsEmpty_shouldReturnAnEmptyOptional() {
        Optional<LockWhitelistEntry> actualEntry = whitelistSupport.getLockWhitelistEntryByIndex(0);

        assertTrue(actualEntry.isEmpty());
    }

    @Test
    void getLockWhitelistEntryByIndex_whenLockWhitelistHasEntries_shouldReturnOneOffWhiteListEntry() {
        saveInMemoryStorageOneOffWhiteListEntry();

        Optional<LockWhitelistEntry> actualLockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByIndex(0);

        assertTrue(actualLockWhitelistEntry.isPresent());
        assertEquals(btcAddress, actualLockWhitelistEntry.get().address());
    }

    @Test
    void getLockWhitelistEntryByIndex_whenIndexIsOutOfBounds_shouldReturnAnEmptyOptional() {
        saveInMemoryStorageOneOffWhiteListEntry();

        Optional<LockWhitelistEntry> actualLockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByIndex(1);

        assertTrue(actualLockWhitelistEntry.isEmpty());
    }

    @Test
    void getLockWhitelistEntryByAddress_whenLockWhitelistIsEmpty_shouldReturnAnEmptyOptional() {
        Optional<LockWhitelistEntry> actualEntry = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());

        assertTrue(actualEntry.isEmpty());
    }

    @Test
    void getLockWhitelistEntryByAddress_whenLockWhitelistHasEntries_shouldReturnOneOffWhiteListEntry() {
        saveInMemoryStorageOneOffWhiteListEntry();

        Optional<LockWhitelistEntry> actualLockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());

        assertTrue(actualLockWhitelistEntry.isPresent());
        assertEquals(btcAddress, actualLockWhitelistEntry.get().address());
    }

    private void saveInMemoryStorageOneOffWhiteListEntry() {
        Coin maxTransferValue = Coin.COIN;
        final int disableBlockHeight = 100;
        OneOffWhiteListEntry oneOffWhiteListEntry = new OneOffWhiteListEntry(btcAddress, maxTransferValue);
        List<OneOffWhiteListEntry> oneOffWhiteListEntries = Collections.singletonList(oneOffWhiteListEntry);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffWhiteListEntries, disableBlockHeight);

        inMemoryStorage.saveToRepository(
            LOCK_ONE_OFF.getKey(),
            pairValue,
            BridgeSerializationUtils::serializeOneOffLockWhitelist
        );
    }

    @Test
    void getLockWhitelistEntryByAddress_whenAddressIsInvalid_shouldReturnAnEmptyOptional() {
        Optional<LockWhitelistEntry> actualLockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByAddress("invalidAddress");
        assertTrue(actualLockWhitelistEntry.isEmpty());
    }

    @Test
    void addOneOffLockWhitelistAddress_whenAddressIsWhitelisted_shouldReturnSuccess() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);

        assertEquals(WhitelistResponseCode.SUCCESS.getCode(), actualResult);
    }

    @Test
    void addOneOffLockWhitelistAddress_whenAddressIsAlreadyWhitelisted_shouldReturnAddressAlreadyWhitelisted() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        // Add the address to the whitelist
        whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);

        // Try to add the address again
        int actualResult = whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);

        assertEquals(WhitelistResponseCode.ADDRESS_ALREADY_WHITELISTED.getCode(), actualResult);
    }

    @Test
    void addOneOffLockWhitelistAddress_whenAddressIsInvalid_shouldReturnInvalidAddressFormat() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addOneOffLockWhitelistAddress(tx, "invalidAddress", BigInteger.TEN);

        assertEquals(WhitelistResponseCode.INVALID_ADDRESS_FORMAT.getCode(), actualResult);
    }

    @Test
    void addOneOffLockWhitelistAddress_whenCallerIsUnauthorized_shouldReturnUnauthorizedCaller() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.UNAUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);

        assertEquals(WhitelistResponseCode.UNAUTHORIZED_CALLER.getCode(), actualResult);
    }

    @Test
    void addUnlimitedLockWhitelistAddress_whenAddressIsWhitelisted_shouldReturnSuccess() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.SUCCESS.getCode(), actualResult);
    }

    @Test
    void addUnlimitedLockWhitelistAddress_whenAddressIsAlreadyWhitelisted_shouldReturnAddressAlreadyWhitelisted() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        // Add the address to the whitelist
        whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        // Try to add the address again
        int actualResult = whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.ADDRESS_ALREADY_WHITELISTED.getCode(), actualResult);
    }

    @Test
    void addUnlimitedLockWhitelistAddress_whenAddressIsInvalid_shouldReturnInvalidAddressFormat() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addUnlimitedLockWhitelistAddress(tx, "invalidAddress");

        assertEquals(WhitelistResponseCode.INVALID_ADDRESS_FORMAT.getCode(), actualResult);
    }

    @Test
    void addUnlimitedLockWhitelistAddress_whenCallerIsUnauthorized_shouldReturnUnauthorizedCaller() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.UNAUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.UNAUTHORIZED_CALLER.getCode(), actualResult);
    }

    @Test
    void removeLockWhitelistAddress_whenAddressIsWhitelisted_shouldReturnSuccess() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        // Add the address to the whitelist
        whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        // Remove the address from the whitelist
        int actualResult = whitelistSupport.removeLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.SUCCESS.getCode(), actualResult);
    }

    @Test
    void removeLockWhitelistAddress_whenAddressIsNotWhitelisted_shouldReturnAddressNotExist() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.removeLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.ADDRESS_NOT_EXIST.getCode(), actualResult);
    }

    @Test
    void removeLockWhitelistAddress_whenAddressIsInvalid_shouldReturnInvalidAddressFormat() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        int actualResult = whitelistSupport.removeLockWhitelistAddress(tx, "invalidAddress");
        assertEquals(WhitelistResponseCode.INVALID_ADDRESS_FORMAT.getCode(), actualResult);
    }

    @Test
    void removeLockWhitelistAddress_whenCallerIsUnauthorized_shouldReturnUnauthorizedCaller() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.UNAUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.removeLockWhitelistAddress(tx, btcAddress.toString());

        assertEquals(WhitelistResponseCode.UNAUTHORIZED_CALLER.getCode(), actualResult);
    }

    @Test
    void setLockWhitelistDisableBlockDelay_whenCallerIsUnauthorized_shouldReturnUnauthorizedCaller() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.UNAUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.setLockWhitelistDisableBlockDelay(tx, BigInteger.TEN, 0);

        assertEquals(WhitelistResponseCode.UNAUTHORIZED_CALLER.getCode(), actualResult);
    }

    @Test
    void setLockWhitelistDisableBlockDelay_whenCallerIsAuthorized_shouldReturnSuccess() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.setLockWhitelistDisableBlockDelay(tx, BigInteger.TEN, 0);

        assertEquals(WhitelistResponseCode.SUCCESS.getCode(), actualResult);
    }

    @Test
    void setLockWhitelistDisableBlockDelay_whenDisableBlockIsSet_shouldReturnDelayAlreadySet() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        // Set the disable block delay
        whitelistSupport.setLockWhitelistDisableBlockDelay(tx, BigInteger.TEN, 0);

        // Try to set the disable block delay again
        int actualResult = whitelistSupport.setLockWhitelistDisableBlockDelay(tx, BigInteger.TEN, 0);

        assertEquals(WhitelistResponseCode.DELAY_ALREADY_SET.getCode(), actualResult);
    }

    @Test
    void setLockWhitelistDisableBlockDelay_whenDisableBlockDelayPlusBtcBlockchainBestChainHeightIsLessOrEqualThanBtcBlockchainBestChainHeight_shouldReturnDisableBlockDelayInvalid() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());

        int actualResult = whitelistSupport.setLockWhitelistDisableBlockDelay(tx, BigInteger.ZERO, 0);

        assertEquals(WhitelistResponseCode.DISABLE_BLOCK_DELAY_INVALID.getCode(), actualResult);
    }

    @Test
    void verifyLockSenderIsWhitelisted_whenOneOffLockWhitelistAddressIsWhitelisted_shouldReturnTrue() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);
        Optional<LockWhitelistEntry> lockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());

        boolean actualResult = whitelistSupport.verifyLockSenderIsWhitelisted(btcAddress, Coin.SATOSHI, 0);

        assertTrue(lockWhitelistEntry.isPresent());
        assertTrue(lockWhitelistEntry.get().isConsumed());
        assertTrue(actualResult);
    }

    @Test
    void verifyLockSenderIsWhitelisted_whenUnlimitedLockWhitelistAddressIsWhitelisted_shouldReturnTrue() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        boolean actualResult = whitelistSupport.verifyLockSenderIsWhitelisted(btcAddress, Coin.SATOSHI, 0);

        assertTrue(actualResult);
    }

    @Test
    void verifyLockSenderIsWhitelisted_whenAddressIsNotWhitelisted_shouldReturnFalse() {
        boolean actualResult = whitelistSupport.verifyLockSenderIsWhitelisted(btcAddress, Coin.SATOSHI, 0);

        assertFalse(actualResult);
    }

    @Test
    void save_whenLockWhitelistIsNull_shouldReturnZeroEntries() {
        whitelistSupport.save();

        int actualSize = whitelistSupport.getLockWhitelistSize();
        assertEquals(0, actualSize);
        assertTrue(whitelistSupport.getLockWhitelistEntryByIndex(0).isEmpty());
        assertTrue(whitelistSupport.getLockWhitelistEntryByIndex(1).isEmpty());
    }

    @Test
    void save_whenOneOffLockWhitelistAddressIsWhitelisted_shouldSaveOneOffLockWhitelistAddress() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);

        whitelistSupport.save();

        int actualSize = whitelistSupport.getLockWhitelistSize();
        Optional<LockWhitelistEntry> lockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());
        assertTrue(lockWhitelistEntry.isPresent());

        Address actualBtcAddress = lockWhitelistEntry.get().address();
        assertEquals(1, actualSize);
        assertEquals(btcAddress, actualBtcAddress);
    }

    @Test
    void save_whenUnlimitedLockWhitelistAddressIsWhitelisted_shouldSaveUnlimitedLockWhitelistAddress() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        whitelistSupport.addUnlimitedLockWhitelistAddress(tx, btcAddress.toString());

        whitelistSupport.save();

        int actualSize = whitelistSupport.getLockWhitelistSize();
        Optional<LockWhitelistEntry> lockWhitelistEntry = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());
        assertTrue(lockWhitelistEntry.isPresent());

        Address actualBtcAddress = lockWhitelistEntry.get().address();
        assertEquals(1, actualSize);
        assertEquals(btcAddress, actualBtcAddress);
    }

    @Test
    void save_whenOneOffAndUnlimitedLockWhitelistAddressesAreWhitelisted_shouldSaveBothAddresses() {
        Transaction tx = TransactionUtils.getTransactionFromCaller(signatureCache, WhitelistCaller.AUTHORIZED.getRskAddress());
        whitelistSupport.addOneOffLockWhitelistAddress(tx, btcAddress.toString(), BigInteger.TEN);
        whitelistSupport.addUnlimitedLockWhitelistAddress(tx, secondBtcAddress.toString());

        whitelistSupport.save();

        int actualSize = whitelistSupport.getLockWhitelistSize();
        Optional<LockWhitelistEntry> lockWhitelistEntryBtcAddress = whitelistSupport.getLockWhitelistEntryByAddress(btcAddress.toString());
        assertTrue(lockWhitelistEntryBtcAddress.isPresent());
        Address actualBtcAddress = lockWhitelistEntryBtcAddress.get().address();

        Optional<LockWhitelistEntry> lockWhitelistEntrySecondBtcAddress = whitelistSupport.getLockWhitelistEntryByAddress(secondBtcAddress.toString());
        assertTrue(lockWhitelistEntrySecondBtcAddress.isPresent());
        Address actualSecondBtcAddress = lockWhitelistEntrySecondBtcAddress.get().address();
        assertEquals(2, actualSize);
        assertEquals(btcAddress, actualBtcAddress);
        assertEquals(secondBtcAddress, actualSecondBtcAddress);
    }
}
