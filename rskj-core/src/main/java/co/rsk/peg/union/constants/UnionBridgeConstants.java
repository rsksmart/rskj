package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;

public class UnionBridgeConstants {
    protected NetworkParameters btcParams;
    protected RskAddress unionBridgeAddress;
    protected Coin initialLockingCap;
    protected int lockingCapIncrementsMultiplier;
    protected AddressBasedAuthorizer changeAuthorizer;

    public NetworkParameters getBtcParams() {
        return btcParams;
    }

    public RskAddress getAddress() {
        return unionBridgeAddress;
    }

    public Coin getInitialLockingCap() {
        return initialLockingCap;
    }

    public int getLockingCapIncrementsMultiplier() {
        return lockingCapIncrementsMultiplier;
    }

    public AddressBasedAuthorizer getChangeAuthorizer() {
        return changeAuthorizer;
    }
}
