package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class OneOffWhiteListEntry implements LockWhitelistEntry {
    private final Address address;
    private final Coin maxTransferValue;

    private boolean consumed = false;

    public OneOffWhiteListEntry(Address address, Coin maxTransferValue) {
        this.address = address;
        this.maxTransferValue = maxTransferValue;
    }

    public Address address() {
        return this.address;
    }

    public Coin maxTransferValue() {
        return this.maxTransferValue;
    }

    public void consume() {
        this.consumed = true;
    }

    public Boolean canConsume() {
        return !this.consumed;
    }
}
