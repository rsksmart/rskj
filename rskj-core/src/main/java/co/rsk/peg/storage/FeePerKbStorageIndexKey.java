package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

public enum FeePerKbStorageIndexKey {
    FEE_PER_KB_KEY("feePerKb"),
    FEE_PER_KB_ELECTION_KEY("feePerKbElection")
    ;

    private final String key;

    FeePerKbStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }

}
