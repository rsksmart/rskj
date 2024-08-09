package co.rsk.peg.lockingcap;

import org.ethereum.vm.DataWord;

public enum LockingCapStorageIndexKey {
    LOCKING_CAP("lockingCap");

    private final String key;

    LockingCapStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }
}
