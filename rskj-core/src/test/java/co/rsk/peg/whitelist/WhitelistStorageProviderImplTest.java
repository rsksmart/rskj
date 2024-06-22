package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_UNLIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        //make sure the lockWhitelist and storage is empty
        assertEquals(0, actualLockWhitelist.getAll().size());

        //Making sure there is no value saved in Storage
        Map<Address, OneOffWhiteListEntry> oneOffWhiteListEntryMap = getAddressFromOneOffWhiteListEntryInMemoryStorage();
        assertEquals(0, oneOffWhiteListEntryMap.size());

        //Making sure there is no value saved in Storage
        Map<Address, UnlimitedWhiteListEntry> addressUnlimitedWhiteListEntryMap = getAddressFromUnlimitedWhiteListEntryInMemoryStorage();
        assertEquals(0, addressUnlimitedWhiteListEntryMap.size());
    }

    @Test
    void save_whenLockWhiteListIsNotNull_shouldReturnSavedValue() {
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        //make sure the lockWhitelist is empty
        assertEquals(0, lockWhitelist.getAll().size());

        LockWhitelistEntry oneOffWhiteListEntry =  createOneOffWhiteListEntry(firstBtcAddress);
        lockWhitelist.put(firstBtcAddress, oneOffWhiteListEntry);

        whitelistStorageProvider.save(activationConfig);

        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(1, actualLockWhitelist.getAll().size());

        //Making sure the saved value is correct
        Map<Address, OneOffWhiteListEntry> whitelistedAddresses = getAddressFromOneOffWhiteListEntryInMemoryStorage();
        assertTrue(whitelistedAddresses.containsKey(firstBtcAddress));
    }

    @Test
    void save_whenIsActiveRSKIP87_ShouldSavedUnlimitedLockWhitelist() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        //make sure the lockWhitelist is empty
        assertEquals(0, lockWhitelist.getAll().size());

        LockWhitelistEntry oneOffWhiteListEntry =  createOneOffWhiteListEntry(firstBtcAddress);
        LockWhitelistEntry unlimitedWhiteListEntry = createUnlimitedWhiteListEntry(secondBtcAddress);
        lockWhitelist.put(firstBtcAddress, oneOffWhiteListEntry);
        lockWhitelist.put(secondBtcAddress, unlimitedWhiteListEntry);

        whitelistStorageProvider.save(activationConfig);

        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(2, actualLockWhitelist.getAll().size());

        //Making sure the saved value is correct
        Map<Address, OneOffWhiteListEntry> oneOffWhiteListEntryMap = getAddressFromOneOffWhiteListEntryInMemoryStorage();
        assertTrue(oneOffWhiteListEntryMap.containsKey(firstBtcAddress));

        Map<Address, UnlimitedWhiteListEntry> lockWhitelistEntryMap = getAddressFromUnlimitedWhiteListEntryInMemoryStorage();
        assertTrue(lockWhitelistEntryMap.containsKey(secondBtcAddress));
    }

    @Test
    void getLockWhitelist_whenNoEntriesInStorage_shouldReturnZeroEntries() {
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(0, actualLockWhitelist.getAll().size());
    }

    @Test
    void getLockWhitelist_whenLockWhitelistIsNotNull_shouldReturnOneEntry() {
        saveInMemoryStorageOneOffWhiteListEntry();

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(1, actualLockWhitelist.getAll().size());
        //Making sure the correct value was whitelisted
        assertTrue(actualLockWhitelist.isWhitelisted(firstBtcAddress));
    }

    @Test
    void getWhitelist_whenIsActiveRSKIP87_shouldReturnUnlimitedEntries() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        saveInMemoryStorageOneOffWhiteListEntry();
        saveInMemoryStorageUnlimitedWhiteListEntry();

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(2, actualLockWhitelist.getAll().size());
        //Making sure the correct value was whitelisted
        assertTrue(actualLockWhitelist.isWhitelisted(firstBtcAddress));
        assertTrue(actualLockWhitelist.isWhitelisted(secondBtcAddress));
    }

    private void saveInMemoryStorageOneOffWhiteListEntry() {
        OneOffWhiteListEntry oneOffWhiteListEntry = createOneOffWhiteListEntry(firstBtcAddress);
        List<OneOffWhiteListEntry> oneOffWhiteListEntries = Collections.singletonList(oneOffWhiteListEntry);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffWhiteListEntries, 100);

        inMemoryStorage.safeSaveToRepository(
            LOCK_ONE_OFF.getKey(),
            pairValue,
            BridgeSerializationUtils::serializeOneOffLockWhitelist
        );
    }

    private void saveInMemoryStorageUnlimitedWhiteListEntry() {
        UnlimitedWhiteListEntry unlimitedWhiteListEntry = createUnlimitedWhiteListEntry(secondBtcAddress);
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

    private Map<Address, OneOffWhiteListEntry> getAddressFromOneOffWhiteListEntryInMemoryStorage() {
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> oneOffWhitelistAndDisableBlockHeightData = inMemoryStorage.safeGetFromRepository(
            LOCK_ONE_OFF.getKey(),
            data -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(data, networkParameters)
        );
        return Objects.isNull(oneOffWhitelistAndDisableBlockHeightData) ? new HashMap<>() : oneOffWhitelistAndDisableBlockHeightData.getLeft();
    }

    private Map<Address, UnlimitedWhiteListEntry> getAddressFromUnlimitedWhiteListEntryInMemoryStorage() {
        return inMemoryStorage.safeGetFromRepository(
            LOCK_UNLIMITED.getKey(),
            data -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(
                data,
                networkParameters)
        );
    }
}
