package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import java.util.Optional;

public interface LockingCapStorageProvider {

    Optional<Coin> getLockingCap();

    void setLockingCap(Coin lockingCap);

    void save();
}
