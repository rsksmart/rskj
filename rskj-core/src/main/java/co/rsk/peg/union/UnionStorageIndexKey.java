package co.rsk.peg.union;

import org.ethereum.vm.DataWord;

public enum UnionStorageIndexKey {
    UNION_BRIDGE_CONTRACT_ADDRESS("unionBridgeContractAddress")
    ;

    private final String key;

    UnionStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }
}
