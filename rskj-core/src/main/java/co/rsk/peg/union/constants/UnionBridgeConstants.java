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
    protected AddressBasedAuthorizer unionBridgeChangeAuthorizer;

    public NetworkParameters getBtcParams() {
        return btcParams;
    }

    public RskAddress getUnionBridgeAddress() {
        return unionBridgeAddress;
    }

    public Coin getInitialLockingCap() {
        return initialLockingCap;
    }

    public int getLockingCapIncrementsMultiplier() {
        return lockingCapIncrementsMultiplier;
    }

    public AddressBasedAuthorizer getUnionBridgeChangeAuthorizer() {
        return unionBridgeChangeAuthorizer;
    }
}
