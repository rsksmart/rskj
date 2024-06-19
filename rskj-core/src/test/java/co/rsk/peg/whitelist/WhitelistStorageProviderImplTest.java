package co.rsk.peg.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistStorageProviderImplTest {
    private final WhitelistConstants whitelistConstants = WhitelistMainNetConstants.getInstance();
    private final NetworkParameters networkParameters = whitelistConstants.getBtcParams();
    private WhitelistStorageProvider whitelistStorageProvider;
    private ActivationConfig.ForBlock activationConfig;

    @BeforeEach
    void setUp() {
        StorageAccessor inMemoryStorage = new InMemoryStorage();
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
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        LockWhitelistEntry oneOffWhiteListEntry =  addOneOffWhiteListEntry();
        lockWhitelist.put(getBtcAddress(), oneOffWhiteListEntry);

        whitelistStorageProvider.save(activationConfig);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        assertEquals(1, actualLockWhitelist.getAll().size());
    }

    @Test
    void save_whenIsActiveRSKIP87_ShouldSavedUnlimitedLockWhitelist() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        LockWhitelistEntry oneOffWhiteListEntry =  addOneOffWhiteListEntry();
        LockWhitelistEntry unlimitedWhiteListEntry = addUnlimitedWhiteListEntry();
        lockWhitelist.put(getBtcAddress(), oneOffWhiteListEntry);
        lockWhitelist.put(getBtcAddress(), unlimitedWhiteListEntry);

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
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        LockWhitelistEntry oneOffWhiteListEntry =  addOneOffWhiteListEntry();
        lockWhitelist.put(getBtcAddress(), oneOffWhiteListEntry);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(1, actualLockWhitelist.getAll().size());
    }

    @Test
    void getWhitelist_whenIsActiveRSKIP87_shouldReturnTwoEntries() {
        when(activationConfig.isActive(ConsensusRule.RSKIP87)).thenReturn(true);
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);
        LockWhitelistEntry oneOffWhiteListEntry =  addOneOffWhiteListEntry();
        LockWhitelistEntry unlimitedWhiteListEntry = addUnlimitedWhiteListEntry();
        lockWhitelist.put(getBtcAddress(), oneOffWhiteListEntry);
        lockWhitelist.put(getBtcAddress(), unlimitedWhiteListEntry);

        LockWhitelist actualLockWhitelist = whitelistStorageProvider.getLockWhitelist(activationConfig, networkParameters);

        assertEquals(2, actualLockWhitelist.getAll().size());
    }

    private LockWhitelistEntry addOneOffWhiteListEntry() {
        Coin maxTransferValue = networkParameters.getMaxMoney();
        return new OneOffWhiteListEntry(getBtcAddress(), maxTransferValue);
    }

    private LockWhitelistEntry addUnlimitedWhiteListEntry() {
        return new UnlimitedWhiteListEntry(getBtcAddress());
    }

    private Address getBtcAddress() {
        BtcECKey btcECKey = new BtcECKey();
        return btcECKey.toAddress(whitelistConstants.getBtcParams());
    }
}
