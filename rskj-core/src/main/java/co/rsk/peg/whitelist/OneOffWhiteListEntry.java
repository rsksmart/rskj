package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;

public class OneOffWhiteListEntry implements LockWhitelistEntry {
    private final Address address;
    private final Coin maxTransferValueField;

    private boolean consumed = false;

    public OneOffWhiteListEntry(Address address, Coin maxTransferValue) {
        this.address = address;
        this.maxTransferValueField = maxTransferValue;
    }

    public Address address() {
        return this.address;
    }

    public Coin maxTransferValue() {
        return this.maxTransferValueField;
    }

    public void consume() {
        this.consumed = true;
    }

    public Boolean isConsumed() {
        return this.consumed;
    }

    public Boolean canLock(Coin value) {
        return !this.consumed && (this.maxTransferValueField.isLessThan(value) || this.maxTransferValueField.equals(value));
    }
}
