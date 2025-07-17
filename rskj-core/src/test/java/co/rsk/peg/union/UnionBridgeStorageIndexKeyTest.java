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
        switch (key) {
            case UNION_BRIDGE_CONTRACT_ADDRESS:
                assertEquals(DataWord.fromLongString("unionBridgeContractAddress"), key.getKey());
                break;
            case UNION_BRIDGE_LOCKING_CAP:
                assertEquals(DataWord.fromLongString("unionBridgeLockingCap"), key.getKey());
                break;
            case UNION_BRIDGE_INCREASE_LOCKING_CAP_ELECTION:
                assertEquals(DataWord.fromLongString("unionBridgeIncreaseLockingCapElection"), key.getKey());
                break;
            case WEIS_TRANSFERRED_TO_UNION_BRIDGE:
                assertEquals(DataWord.fromLongString("weisTransferredToUnionBridge"), key.getKey());
                break;
            case UNION_BRIDGE_REQUEST_ENABLED:
                assertEquals(DataWord.fromLongString("unionBridgeRequestEnabled"), key.getKey());
                break;
            case UNION_BRIDGE_RELEASE_ENABLED:
                assertEquals(DataWord.fromLongString("unionBridgeReleaseEnabled"), key.getKey());
                break;
            case UNION_BRIDGE_TRANSFER_PERMISSIONS_ELECTION:
                assertEquals(DataWord.fromLongString("unionBridgeTransferPermissionsElection"), key.getKey());
                break;
            default:
                fail("Unexpected enum value: " + key);
        }
    }
}