package co.rsk.peg.whitelist;

import org.ethereum.vm.DataWord;

/**
 *  Enum for Whitelist storage index key.
 */
public enum WhitelistStorageIndexKey {
    LOCK_ONE_OFF("lockWhitelist"),
    LOCK_UNLIMITED("unlimitedLockWhitelist");

    private final String key;

    WhitelistStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }
}
