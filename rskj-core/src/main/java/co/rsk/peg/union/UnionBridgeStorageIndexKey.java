package co.rsk.peg.union;

import org.ethereum.vm.DataWord;

public enum UnionBridgeStorageIndexKey {
    UNION_BRIDGE_CONTRACT_ADDRESS("unionBridgeContractAddress");

    private final String key;

    UnionBridgeStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromString(key);
    }
}
