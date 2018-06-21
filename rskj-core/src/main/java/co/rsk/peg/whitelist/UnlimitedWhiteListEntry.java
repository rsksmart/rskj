package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class UnlimitedWhiteListEntry implements LockWhitelistEntry {
    private final Address address;
    private final Coin maxTransferValue;

    public UnlimitedWhiteListEntry(Address address) {
        this.address = address;
        this.maxTransferValue = Coin.valueOf(Integer.MAX_VALUE);
    }

    public Address address() {
        return this.address;
    }

    public Coin maxTransferValue() {
        return this.maxTransferValue;
    }

    public void consume() {
        // Unlimited whitelisting means that the entries are never fully consumed so nothing to do here
    }

    public Boolean canConsume() {
        return Boolean.TRUE;
    }
}
