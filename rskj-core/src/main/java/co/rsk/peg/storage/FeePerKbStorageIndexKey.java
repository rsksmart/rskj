package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

public enum FeePerKbStorageIndexKey {
    FEE_PER_KB("feePerKb"),
    FEE_PER_KB_ELECTION("feePerKbElection")
    ;

    private final String key;

    FeePerKbStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }

}
