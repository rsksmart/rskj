package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistStorageProviderImplTest {
    private final WhitelistConstants whitelistConstants = WhitelistMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = whitelistConstants.getBtcParams();
    private WhitelistStorageProvider whitelistStorageProvider;
    private StorageAccessor inMemoryStorage;
    private ActivationConfig.ForBlock activationConfig;

    @BeforeEach
    void setUp() {
        inMemoryStorage = new InMemoryStorage();
        whitelistStorageProvider = new WhitelistStorageProviderImpl(inMemoryStorage);
        activationConfig = mock(ActivationConfig.ForBlock.class);
    }

    @Test
    void save_whenLockWhitelistIsNull_shouldReturnZeroEntries() {
        whitelistStorageProvider.save(activationConfig);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(0, actualLockWhitelist.getAll().size());
    }

    @Test
    void save_whenLockWhiteListIsNotNull_ShouldReturnSavedValue() {
        saveOneOffWhiteListEntry();

        whitelistStorageProvider.save(activationConfig);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(1, actualLockWhitelist.getAll().size());
    }

    @Test
    void save_whenIsActiveRSKIP87_ShouldSavedUnlimitedLockWhitelist() {
        saveOneOffWhiteListEntry();
        saveUnlimitedWhiteListEntry();
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);

        whitelistStorageProvider.save(activationConfig);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(2, actualLockWhitelist.getAll().size());
    }

    @Test
    void getLockWhitelist_whenLockWhitelistIsNull_shouldReturnZeroEntries() {
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(0, actualLockWhitelist.getAll().size());
    }

    @Test
    void getLockWhitelist_whenLockWhitelistIsNotNull_shouldReturnOneEntry() {
        saveOneOffWhiteListEntry();
        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(1, actualLockWhitelist.getAll().size());
    }

    @Test
    void getWhitelist_whenIsActiveRSKIP87_shouldReturnTwoEntries() {
        saveOneOffWhiteListEntry();
        saveUnlimitedWhiteListEntry();
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(2, actualLockWhitelist.getAll().size());
    }

    private void saveOneOffWhiteListEntry() {
        Coin maxTransferValue = networkParameters.getMaxMoney();
        OneOffWhiteListEntry oneOffWhiteListEntry = new OneOffWhiteListEntry(getBtcAddress(), maxTransferValue);
        List<OneOffWhiteListEntry> oneOffWhiteListEntries = Collections.singletonList(oneOffWhiteListEntry);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffWhiteListEntries, 100);

        inMemoryStorage.safeSaveToRepository(
            LOCK_ONE_OFF.getKey(),
            pairValue,
            BridgeSerializationUtils::serializeOneOffLockWhitelist
        );
    }

    private void saveUnlimitedWhiteListEntry() {
        UnlimitedWhiteListEntry unlimitedWhiteListEntry = new UnlimitedWhiteListEntry(getBtcAddress());
        List<UnlimitedWhiteListEntry> unlimitedWhiteListEntries = Collections.singletonList(unlimitedWhiteListEntry);

        inMemoryStorage.safeSaveToRepository(
            WhitelistStorageIndexKey.LOCK_UNLIMITED.getKey(),
            unlimitedWhiteListEntries,
            BridgeSerializationUtils::serializeUnlimitedLockWhitelist
        );
    }

    private Address getBtcAddress() {
        BtcECKey btcECKey = new BtcECKey();
        return btcECKey.toAddress(whitelistConstants.getBtcParams());
    }
}
