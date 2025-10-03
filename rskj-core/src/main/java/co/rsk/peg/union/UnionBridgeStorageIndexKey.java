package co.rsk.peg.union;

import org.ethereum.vm.DataWord;

public enum UnionBridgeStorageIndexKey {
    UNION_BRIDGE_CONTRACT_ADDRESS("unionBridgeContractAddress"),

    UNION_BRIDGE_LOCKING_CAP("unionBridgeLockingCap"),

    WEIS_TRANSFERRED_TO_UNION_BRIDGE("weisTransferredToUnionBridge"),

    UNION_BRIDGE_REQUEST_ENABLED("unionBridgeRequestEnabled"),
    UNION_BRIDGE_RELEASE_ENABLED("unionBridgeReleaseEnabled"),
    SUPER_EVENT("superEvent"),
    ;

    private final String key;

    UnionBridgeStorageIndexKey(String key) {
        this.key = key;
    }

    public DataWord getKey() {
        return DataWord.fromLongString(key);
    }
}
