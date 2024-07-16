package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import org.ethereum.core.Transaction;

public interface LockingCapSupport {

    Coin getLockingCap();

    boolean increaseLockingCap(Transaction tx, Coin newCap);
}
