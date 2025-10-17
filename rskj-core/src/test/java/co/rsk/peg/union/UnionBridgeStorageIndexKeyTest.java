package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import org.ethereum.vm.DataWord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UnionBridgeStorageIndexKeyTest {

    @ParameterizedTest
    @EnumSource(UnionBridgeStorageIndexKey.class)
    void getKey_shouldReturnCorrectKey(UnionBridgeStorageIndexKey key) {
        // Testing that getKey() returns the correct DataWord for each enum value
        DataWord expectedKey = switch (key) {
            case UNION_BRIDGE_CONTRACT_ADDRESS -> DataWord.fromLongString("unionBridgeContractAddress");
            case UNION_BRIDGE_LOCKING_CAP -> DataWord.fromLongString("unionBridgeLockingCap");
            case WEIS_TRANSFERRED_TO_UNION_BRIDGE -> DataWord.fromLongString("weisTransferredToUnionBridge");
            case UNION_BRIDGE_REQUEST_ENABLED -> DataWord.fromLongString("unionBridgeRequestEnabled");
            case UNION_BRIDGE_RELEASE_ENABLED -> DataWord.fromLongString("unionBridgeReleaseEnabled");
            case SUPER_EVENT -> DataWord.fromLongString("superEvent");
            case BASE_EVENT -> DataWord.fromLongString("baseEvent");
        };
        
        assertEquals(expectedKey, key.getKey());
    }
}
