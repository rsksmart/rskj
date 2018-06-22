package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class UnlimitedWhiteListEntry implements LockWhitelistEntry {
    private final Address address;

    public UnlimitedWhiteListEntry(Address address) {
        this.address = address;
    }

    public Address address() {
        return this.address;
    }

    public void consume() {
        // Unlimited whitelisting means that the entries are never fully consumed so nothing to do here
    }

    public Boolean isConsumed() {
        return Boolean.FALSE;
    }

    public Boolean canLock(Coin value) {
        return Boolean.TRUE;
    }
}
