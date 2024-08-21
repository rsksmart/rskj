package co.rsk.peg.lockingcap.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;

public class LockingCapConstants {

    protected AddressBasedAuthorizer increaseAuthorizer;
    protected Coin initialValue;
    protected int incrementsMultiplier;

    public AddressBasedAuthorizer getIncreaseAuthorizer() {
        return increaseAuthorizer;
    }

    public Coin getInitialValue() {
        return initialValue;
    }

    public int getIncrementsMultiplier() {
        return incrementsMultiplier;
    }
}
