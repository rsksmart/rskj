package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_ONE_OFF;
import static co.rsk.peg.whitelist.WhitelistStorageIndexKey.LOCK_UNLIMITED;
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

    private final StorageAccessor bridgeStorageAccessor;
    private LockWhitelist lockWhitelist;

    public WhitelistStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save(ActivationConfig.ForBlock activations) {
        if (lockWhitelist == null) {
            return;
        }

        List<OneOffWhiteListEntry> oneOffEntries = lockWhitelist.getAll(OneOffWhiteListEntry.class);
        Pair<List<OneOffWhiteListEntry>, Integer> pairValue = Pair.of(oneOffEntries, lockWhitelist.getDisableBlockHeight());
        bridgeStorageAccessor.saveToRepository(
            LOCK_ONE_OFF.getKey(),
            pairValue,
            BridgeSerializationUtils::serializeOneOffLockWhitelist
        );

        if (activations.isActive(RSKIP87)) {
            List<UnlimitedWhiteListEntry> unlimitedEntries = lockWhitelist.getAll(UnlimitedWhiteListEntry.class);
            bridgeStorageAccessor.saveToRepository(
                LOCK_UNLIMITED.getKey(),
                unlimitedEntries,
                BridgeSerializationUtils::serializeUnlimitedLockWhitelist
            );
        }
    }

    @Override
    public synchronized LockWhitelist getLockWhitelist(ActivationConfig.ForBlock activations, NetworkParameters networkParameters) {
        if (lockWhitelist == null) {
            lockWhitelist = initializeLockWhitelist(activations, networkParameters);
        }
        return lockWhitelist;
    }

    private LockWhitelist initializeLockWhitelist(ActivationConfig.ForBlock activations, NetworkParameters networkParameters) {
        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> oneOffWhitelistAndDisableBlockHeightData = bridgeStorageAccessor.getFromRepository(
            LOCK_ONE_OFF.getKey(),
            data -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(data, networkParameters)
        );

        if (oneOffWhitelistAndDisableBlockHeightData == null) {
            lockWhitelist = new LockWhitelist(new HashMap<>());
            return lockWhitelist;
        }

        Map<Address, LockWhitelistEntry> whitelistedAddresses = new HashMap<>(oneOffWhitelistAndDisableBlockHeightData.getLeft());

        if (activations.isActive(RSKIP87)) {
            whitelistedAddresses.putAll(bridgeStorageAccessor.getFromRepository(
                LOCK_UNLIMITED.getKey(),
                data -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(data, networkParameters)
            ));
        }

        lockWhitelist = new LockWhitelist(whitelistedAddresses, oneOffWhitelistAndDisableBlockHeightData.getRight());

        return lockWhitelist;
    }
}
