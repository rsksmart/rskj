package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public interface LockingCapStorageProvider {

    Optional<Coin> getLockingCap(ActivationConfig.ForBlock activations);

    void setLockingCap(Coin lockingCap);

    void save(ActivationConfig.ForBlock activations);
}
