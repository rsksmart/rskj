package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.*;

import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

class UnionBridgeStorageIndexKeyTest {

    @Test
    void getKey_unionBridgeContractAddress_shouldReturnKey() {
        UnionBridgeStorageIndexKey key = UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS;

        DataWord expectedKey = DataWord.fromString("unionBridgeContractAddress");
        assertEquals(expectedKey, key.getKey());
    }

    @Test
    void getKey_unionBridgeLockingCap_shouldReturnKey() {
        UnionBridgeStorageIndexKey key = UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP;

        DataWord expectedKey = DataWord.fromString("unionBridgeLockingCap");
        assertEquals(expectedKey, key.getKey());
    }
}