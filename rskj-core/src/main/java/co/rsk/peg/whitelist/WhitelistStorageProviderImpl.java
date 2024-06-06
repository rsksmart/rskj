package co.rsk.peg.whitelist;

import static co.rsk.peg.BridgeStorageIndexKey.LOCK_ONE_OFF_WHITELIST_KEY;
import static co.rsk.peg.BridgeStorageIndexKey.LOCK_UNLIMITED_WHITELIST_KEY;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP87;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

/**
 * Implementation of {@link WhitelistStorageProvider} that saves the whitelist to the Storage and
 * get the lock Whitelist.
 */
public class WhitelistStorageProviderImpl implements WhitelistStorageProvider {

    private LockWhitelist lockWhitelist;
    private final NetworkParameters networkParameters;
    private final ActivationConfig.ForBlock activations;
    private final StorageAccessor storageAccessor;

    public WhitelistStorageProviderImpl(NetworkParameters networkParameters,
        ActivationConfig.ForBlock activations,
        StorageAccessor storageAccessor) {
        this.networkParameters = networkParameters;
        this.activations = activations;
        this.storageAccessor = storageAccessor;
    }

    @Override
    public void save() {
        if (lockWhitelist == null) {
            return;
        }

        List<OneOffWhiteListEntry> oneOffEntries = lockWhitelist.getAll(OneOffWhiteListEntry.class);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffEntries, lockWhitelist.getDisableBlockHeight());
        storageAccessor.safeSaveToRepository(LOCK_ONE_OFF_WHITELIST_KEY.getKey(), pairValue, BridgeSerializationUtils::serializeOneOffLockWhitelist);

        if (activations.isActive(RSKIP87)) {
            List<UnlimitedWhiteListEntry> unlimitedEntries = lockWhitelist.getAll(UnlimitedWhiteListEntry.class);
            storageAccessor.safeSaveToRepository(LOCK_UNLIMITED_WHITELIST_KEY.getKey(), unlimitedEntries, BridgeSerializationUtils::serializeUnlimitedLockWhitelist);
        }
    }

    @Override
    public LockWhitelist getLockWhitelist() {
        if (lockWhitelist != null) {
            return lockWhitelist;
        }

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> oneOffWhitelistAndDisableBlockHeightData =
            storageAccessor.safeGetFromRepository(LOCK_ONE_OFF_WHITELIST_KEY.getKey(),
                data -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(data, networkParameters));

        if (oneOffWhitelistAndDisableBlockHeightData == null) {
            lockWhitelist = new LockWhitelist(new HashMap<>());
            return lockWhitelist;
        }

        Map<Address, LockWhitelistEntry> whitelistedAddresses = new HashMap<>(oneOffWhitelistAndDisableBlockHeightData.getLeft());

        if (activations.isActive(RSKIP87)) {
            whitelistedAddresses.putAll(
                storageAccessor.safeGetFromRepository(LOCK_UNLIMITED_WHITELIST_KEY.getKey(),
                    data -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(data,
                        networkParameters)));
        }

        lockWhitelist = new LockWhitelist(whitelistedAddresses, oneOffWhitelistAndDisableBlockHeightData.getRight());

        return lockWhitelist;
    }
}
