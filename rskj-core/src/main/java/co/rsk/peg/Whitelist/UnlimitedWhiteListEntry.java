package co.rsk.peg.Whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class UnlimitedWhiteListEntry implements LockWhitelistEntry {
    private final Address address;
    private final Coin maxTransferValue;

    public UnlimitedWhiteListEntry(Address address) {
        this.address = address;
        this.maxTransferValue = Coin.valueOf(Integer.MAX_VALUE);
    }

    public Address Address() {
        return this.address;
    }
    public Coin MaxTransferValue() {
        return this.maxTransferValue;
    }
    public Integer Usages() {
        return Integer.MAX_VALUE;
    }
}
