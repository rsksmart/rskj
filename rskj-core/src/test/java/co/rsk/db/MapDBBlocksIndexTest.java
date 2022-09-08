/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db;

import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mapdb.DB;
import org.mapdb.HTreeMap;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MapDBBlocksIndexTest {

    private static final String MAX_BLOCK_NUMBER_KEY = "max_block";
    private MapDBBlocksIndex target;
    private Map<Long, List<IndexedBlockStore.BlockInfo>> index;
    private Map<String, byte[]> metadata;
    private DB indexDB;

    @BeforeEach
    public void setUp() throws Exception {
        indexDB = mock(DB.class);
        DB.HTreeMapMaker hTreeMapMaker = mock(DB.HTreeMapMaker.class);

        when(indexDB.hashMapCreate(anyString())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.valueSerializer(any())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.keySerializer(any())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.counterEnable()).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.makeOrGet())
                .thenReturn(mock(HTreeMap.class))
                .thenReturn(mock(HTreeMap.class));

        target = new MapDBBlocksIndex(indexDB);

        index = new HashMap<>();
        Field indexF = target.getClass().getDeclaredField("index");
        indexF.setAccessible(true);
        indexF.set(target, index);

        metadata = new HashMap<>();
        Field metadataF = target.getClass().getDeclaredField("metadata");
        metadataF.setAccessible(true);
        metadataF.set(target, metadata);
    }

    @Test
    public void getMinNumber_emptyIndex() {
        Assertions.assertThrows(IllegalStateException.class, () -> target.getMinNumber());
    }

    @Test
    public void getMinNumber_nonEmptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        index.put(9L, new ArrayList<>());
        index.put(8L, new ArrayList<>());

        assertEquals(8L, target.getMinNumber());
    }

    @Test
    public void getMaxNumber_emptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        Assertions.assertThrows(IllegalStateException.class, () -> target.getMaxNumber());
    }

    @Test
    public void getMaxNumber() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        index.put(9L, new ArrayList<>());

        assertEquals(target.getMaxNumber(),9);
    }

    @Test
    public void contains_true() {
        long blockNumber = 12;

        index.put(blockNumber, new ArrayList<>());

        assertTrue(target.contains(blockNumber));
    }

    @Test
    public void contains_false() {
        long blockNumber = 12;

        assertFalse(target.contains(blockNumber));
    }

    @Test
    public void getBlocksByNumber_noneFound() {
        long blockNumber = 20;

        List<IndexedBlockStore.BlockInfo> result = target.getBlocksByNumber(blockNumber);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getBlocksByNumber_found() {
        long blockNumber = 20;
        List<IndexedBlockStore.BlockInfo> expectedResult = mock(List.class);

        index.put(blockNumber, expectedResult);

        List<IndexedBlockStore.BlockInfo> result = target.getBlocksByNumber(blockNumber);

        assertEquals(expectedResult, result);
    }

    @Test
    public void putBlocks_emptyList() {
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);
        when(putBlocks.isEmpty()).thenReturn(true);

        Assertions.assertThrows(IllegalArgumentException.class, () -> target.putBlocks(20L, putBlocks));
    }

    @Test
    public void putBlocks_nullList() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> target.putBlocks(20L, null));
    }

    @Test
    public void putBlocks_noNewMaxNumber() {
        long blockNumber = 20;
        byte[] byteNumber = ByteUtil.longToBytes(blockNumber);
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);

        index.put(19L, new ArrayList<>());
        metadata.put(MAX_BLOCK_NUMBER_KEY, byteNumber);

        target.putBlocks(blockNumber, putBlocks);

        assertEquals(putBlocks, index.get(blockNumber));
        assertEquals(blockNumber, target.getMaxNumber());
    }

    @Test
    public void isEmpty_empty() {
        assertTrue(target.isEmpty());
    }

    @Test
    public void isEmpty_notEmpty() {
        long blockNumber = 20;
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);
        target.putBlocks(blockNumber, putBlocks);

        assertFalse(target.isEmpty());
    }

    @Test
    public void putBlocks_emptyIndex() {
        long blockNumber = 20;
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);

        target.putBlocks(blockNumber, putBlocks);

        assertEquals(putBlocks, index.get(blockNumber));
        assertEquals(blockNumber, target.getMaxNumber());
    }

    @Test
    public void putBlocks_newMaxNumber() {
        long blockNumber = 21;
        byte[] byteNumber = ByteUtil.longToBytes(blockNumber);
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);

        target.removeLast();

        metadata.put(MAX_BLOCK_NUMBER_KEY, byteNumber);

        target.putBlocks(blockNumber, putBlocks);

        assertEquals(putBlocks, index.get(blockNumber));
        assertEquals(blockNumber, target.getMaxNumber());
    }

    @Test
    public void removeLastBlock_nonEmptyIndex() {
        List<IndexedBlockStore.BlockInfo> expectedResult = mock(List.class);

        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(20));
        index.put(19L, new ArrayList<>());
        index.put(20L, expectedResult);

        List<IndexedBlockStore.BlockInfo> result = target.removeLast();

        assertEquals(expectedResult, result);
        assertFalse(index.containsKey(20L));
        assertEquals(19L, target.getMaxNumber());
    }

    @Test
    public void removeLastBlock_emptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(-1));

        List<IndexedBlockStore.BlockInfo> result = target.removeLast();

        assertTrue(result.isEmpty());

        Assertions.assertThrows(IllegalStateException.class, () -> target.getMaxNumber());
    }

    @Test
    public void removeLastBlock_nowEmptyIndex() {
        List<IndexedBlockStore.BlockInfo> expectedResult = mock(List.class);

        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(1));
        index.put(1L, expectedResult);

        List<IndexedBlockStore.BlockInfo> result = target.removeLast();

        assertEquals(expectedResult, result);
        assertFalse(index.containsKey(1L));

        Assertions.assertThrows(IllegalStateException.class, () -> target.getMaxNumber());
    }

    @Test
    public void flush() {
        target.flush();

        verify(indexDB, times(1)).commit();
    }
}
