package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class OneOffWhiteListEntry implements LockWhitelistEntry {
    private final Address address;
    private final Coin maxTransferValue;

    public OneOffWhiteListEntry(Address address, Coin maxTransferValue) {
        this.address = address;
        this.maxTransferValue = maxTransferValue;
    }

    public Address Address() {
        return this.address;
    }
    public Coin MaxTransferValue() {
        return this.maxTransferValue;
    }
    public Integer Usages() {
        return 1;
    }
}
