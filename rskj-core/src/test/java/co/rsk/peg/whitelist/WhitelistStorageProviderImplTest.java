package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_UNLIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistStorageProviderImplTest {
    private final WhitelistConstants whitelistConstants = WhitelistMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = whitelistConstants.getBtcParams();
    private WhitelistStorageProvider whitelistStorageProvider;
    private ActivationConfig.ForBlock activationConfig;
    private StorageAccessor inMemoryStorage;
    private Address firstBtcAddress;
    private Address secondBtcAddress;

    @BeforeEach
    void setUp() {
        inMemoryStorage = new InMemoryStorage();
        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        activationConfig = mock(ActivationConfig.ForBlock.class);
        firstBtcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "firstBtcAddress");
        secondBtcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "secondBtcAddress");
    }

    @Test
    void save_whenLockWhitelistIsNull_shouldReturnZeroEntries() {
        whitelistStorageProvider.save(activationConfig);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // Make sure the lockWhitelist cache and storage entries are empty
        assertEquals(0, actualLockWhitelist.getAll().size());

        // There should be no value saved in the one-off entry
        Map<Address, OneOffWhiteListEntry> oneOffWhiteListEntryMap = getAddressFromOneOffStorageEntry();
        assertEquals(0, oneOffWhiteListEntryMap.size());

        // There should be no value saved in the unlimited entry
        Map<Address, UnlimitedWhiteListEntry> unlimitedWhiteListEntryMap = getAddressFromUnlimitedStorageEntry();
        assertEquals(0, unlimitedWhiteListEntryMap.size());
    }

    @Test
    void save_whenLockWhiteListIsNotNullAndRSKIP87IsNotActive_shouldReturnSavedValue() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(false);
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // Make sure the lockWhitelist is empty
        assertEquals(0, lockWhitelist.getAll().size());

        LockWhitelistEntry oneOffWhiteListEntry =  createOneOffWhiteListEntry(firstBtcAddress);
        lockWhitelist.put(firstBtcAddress, oneOffWhiteListEntry);

        whitelistStorageProvider.save(activationConfig);

        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(1, actualLockWhitelist.getAll().size());

        // Making sure the saved value is correct and related to OneOffWhiteListEntry
        Map<Address, OneOffWhiteListEntry> oneOffWhiteListEntryMap = getAddressFromOneOffStorageEntry();
        assertTrue(oneOffWhiteListEntryMap.containsKey(firstBtcAddress));

        // Making sure there is no value saved in storage related to UnlimitedWhiteListEntry
        Map<Address, UnlimitedWhiteListEntry> unlimitedWhiteListEntryMap = getAddressFromUnlimitedStorageEntry();
        assertEquals(0, unlimitedWhiteListEntryMap.size());
    }

    @Test
    void save_whenIsActiveRSKIP87_shouldSavedUnlimitedLockWhitelist() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // Make sure the lockWhitelist is empty
        assertEquals(0, lockWhitelist.getAll().size());

        LockWhitelistEntry oneOffWhiteListEntry =  createOneOffWhiteListEntry(firstBtcAddress);
        LockWhitelistEntry unlimitedWhiteListEntry = createUnlimitedWhiteListEntry(secondBtcAddress);
        lockWhitelist.put(firstBtcAddress, oneOffWhiteListEntry);
        lockWhitelist.put(secondBtcAddress, unlimitedWhiteListEntry);

        whitelistStorageProvider.save(activationConfig);

        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(2, actualLockWhitelist.getAll().size());

        // Making sure the saved value is correct
        Map<Address, OneOffWhiteListEntry> oneOffWhiteListEntryMap = getAddressFromOneOffStorageEntry();
        assertTrue(oneOffWhiteListEntryMap.containsKey(firstBtcAddress));

        Map<Address, UnlimitedWhiteListEntry> lockWhitelistEntryMap = getAddressFromUnlimitedStorageEntry();
        assertTrue(lockWhitelistEntryMap.containsKey(secondBtcAddress));
    }

    @Test
    void getLockWhitelist_whenNoEntriesInStorage_shouldReturnZeroEntries() {
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(0, actualLockWhitelist.getAll().size());
    }

    @Test
    void getLockWhitelist_whenSavedValueDirectlyInStorageAndLockWhitelistIsNotNull_shouldReturnZeroEntries() {
        // The first time LockWhitelist is null and it queries the storage
        LockWhitelist firstTimeQuery = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // It should return zero entries as there is no value saved in storage. As of here LockWhitelist is not null
        assertEquals(0, firstTimeQuery.getAll().size());

        // Saving a value directly in storage
        saveInMemoryStorageOneOffWhiteListEntry(firstBtcAddress);
        // The second time LockWhitelist is not null so that it doesn't query the storage and get the value from cache
        LockWhitelist secondTimeQuery = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // Return zero entries as it doesn't query in storage
        assertEquals(0, secondTimeQuery.getAll().size());
        assertFalse(secondTimeQuery.isWhitelisted(firstBtcAddress));

        // Recreating whitelistStorageProvider to make sure it is querying the storage
        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        // Return one entry that was saved directly in storage
        assertEquals(1, actualLockWhitelist.getAll().size());
        // Making sure the correct value was whitelisted
        assertTrue(actualLockWhitelist.isWhitelisted(firstBtcAddress));
    }

    @Test
    void getWhitelist_whenIsActiveRSKIP87_shouldReturnUnlimitedEntries() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        saveInMemoryStorageOneOffWhiteListEntry(firstBtcAddress);
        saveInMemoryStorageUnlimitedWhiteListEntry(secondBtcAddress);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(2, actualLockWhitelist.getAll().size());
        // Making sure the correct value was whitelisted
        assertTrue(actualLockWhitelist.isWhitelisted(firstBtcAddress));
        assertTrue(actualLockWhitelist.isWhitelisted(secondBtcAddress));
    }

    private void saveInMemoryStorageOneOffWhiteListEntry(Address btcAddress) {
        OneOffWhiteListEntry oneOffWhiteListEntry = createOneOffWhiteListEntry(btcAddress);
        List<OneOffWhiteListEntry> oneOffWhiteListEntries = Collections.singletonList(oneOffWhiteListEntry);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffWhiteListEntries, 100);

        inMemoryStorage.safeSaveToRepository(
            LOCK_ONE_OFF.getKey(),
            pairValue,
            BridgeSerializationUtils::serializeOneOffLockWhitelist
        );
    }

    private void saveInMemoryStorageUnlimitedWhiteListEntry(Address btcAddress) {
        UnlimitedWhiteListEntry unlimitedWhiteListEntry = createUnlimitedWhiteListEntry(btcAddress);
        List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = Collections.singletonList(unlimitedWhiteListEntry);

        inMemoryStorage.safeSaveToRepository(
            WhitelistStorageIndexKey.LOCK_UNLIMITED.getKey(),
            unlimitedWhiteListEntries,
            BridgeSerializationUtils::serializeUnlimitedLockWhitelist
        );
    }

    private OneOffWhiteListEntry createOneOffWhiteListEntry(Address btcAddress) {
        Coin maxTransferValue = Coin.COIN;
        return new OneOffWhiteListEntry(btcAddress, maxTransferValue);
    }

    private UnlimitedWhiteListEntry createUnlimitedWhiteListEntry(Address btcAddress) {
        return new UnlimitedWhiteListEntry(btcAddress);
    }

    private Map<Address, OneOffWhiteListEntry> getAddressFromOneOffStorageEntry() {
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> oneOffWhitelistAndDisableBlockHeightData = inMemoryStorage.safeGetFromRepository(
            LOCK_ONE_OFF.getKey(),
            data -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(data, networkParameters)
        );
        return Objects.isNull(oneOffWhitelistAndDisableBlockHeightData) ? new HashMap<>() : oneOffWhitelistAndDisableBlockHeightData.getLeft();
    }

    private Map<Address, UnlimitedWhiteListEntry> getAddressFromUnlimitedStorageEntry() {
        return inMemoryStorage.safeGetFromRepository(
            LOCK_UNLIMITED.getKey(),
            data -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(
                data,
                networkParameters)
        );
    }
}
