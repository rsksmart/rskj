package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;

public interface LockingCapStorageProvider {

    Coin getLockingCap();

    void setLockingCap(Coin lockingCap);

    void save();
}
