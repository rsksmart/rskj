package co.rsk.peg.lockingcap;

import static co.rsk.peg.lockingcap.LockingCapStorageIndexKey.LOCKING_CAP;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP134;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public class LockingCapStorageProviderImpl implements LockingCapStorageProvider {

    private Coin lockingCap;
    private final ActivationConfig.ForBlock activations;
    private final StorageAccessor bridgeStorageAccessor;

    public LockingCapStorageProviderImpl(
        ActivationConfig.ForBlock activations,
        StorageAccessor bridgeStorageAccessor) {
        this.activations = activations;
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public Optional<Coin> getLockingCap() {
        if (activations.isActive(RSKIP134)) {
            if (lockingCap == null) {
                lockingCap = initializeLockingCap();
            }
            return Optional.of(lockingCap);
        }
        return Optional.empty();
    }

    private synchronized Coin initializeLockingCap() {
        return bridgeStorageAccessor.getFromRepository(LOCKING_CAP.getKey(), BridgeSerializationUtils::deserializeCoin);
    }

    @Override
    public void setLockingCap(Coin lockingCap) {
        this.lockingCap = lockingCap;
    }

    @Override
    public void save() {
        if (activations.isActive(RSKIP134)) {
            bridgeStorageAccessor.saveToRepository(LOCKING_CAP.getKey(), getLockingCap().get(), BridgeSerializationUtils::serializeCoin);
        }
    }
}
