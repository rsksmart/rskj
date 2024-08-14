package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeIllegalArgumentException;
import java.util.Optional;
import org.ethereum.core.Transaction;

public interface LockingCapSupport {

    Optional<Coin> getLockingCap();

    boolean increaseLockingCap(Transaction tx, Coin newCap) throws BridgeIllegalArgumentException;

    void save();
}
