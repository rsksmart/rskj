package co.rsk.util;

import org.ethereum.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaxSizeHashMapTest {

    @Test
    void maxSizeMap_Test() {
        int maxSize = 50_000;
        Map<Integer, Integer> maxSizeMap = new MaxSizeHashMap<>(maxSize, true);

        Object[] table = TestUtils.getInternalState(maxSizeMap, "table");
        assertNull(table);
        for(int i=0; i < maxSize/2 ; i++) {
            maxSizeMap.put(i, i);
        }
        table = TestUtils.getInternalState(maxSizeMap, "table");
        assertEquals(65_536, table.length);
        for(int i=maxSize/2; i < maxSize ; i++) {
            maxSizeMap.put(i, i);
        }
        table = TestUtils.getInternalState(maxSizeMap, "table");
        assertEquals(131_072, table.length);
        for(int i=maxSize; i < maxSize + maxSize ; i++) {
            maxSizeMap.put(i, i);
        }
        table = TestUtils.getInternalState(maxSizeMap, "table");
        assertEquals(131_072, table.length);
    }
}
