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
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.HTreeMap;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MapDBBlocksIndexTest {

    protected static final String MAX_BLOCK_NUMBER_KEY = "max_block";
    protected MapDBBlocksIndex target;
    protected Map<Long, List<IndexedBlockStore.BlockInfo>> index;
    protected Map<String, byte[]> metadata;
    protected Map<Long, List<IndexedBlockStore.BlockInfo>> baseIndex;
    protected Map<String, byte[]> baseMetadata;

    protected DB indexDB;

    @Before
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

        baseMetadata = new HashMap<>();
        baseIndex = new HashMap<>();

        setupMode();

        index = target.getIndex();
        metadata = target.getMetadata();
    }

    protected void setupMode() {
        target = new MapDBBlocksIndex(indexDB, baseIndex, baseMetadata, false);
    }

    @Test(expected = IllegalStateException.class)
    public void getMinNumber_emptyIndex() {
        target.getMinNumber();
    }

    @Test
    public void getMinNumber_nonEmptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        index.put(9L, new ArrayList<>());
        index.put(8L, new ArrayList<>());

        assertEquals(8L, target.getMinNumber());
    }

    @Test(expected = IllegalStateException.class)
    public void getMaxNumber_emptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        target.getMaxNumber();
    }

    @Test
    public void getMaxNumber() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(9));
        index.put(9L, new ArrayList<>());

        assertEquals(9, target.getMaxNumber());
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

    @Test(expected = IllegalArgumentException.class)
    public void putBlocks_emptyList() {
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);
        when(putBlocks.isEmpty()).thenReturn(true);

        target.putBlocks(20L, putBlocks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void putBlocks_nullList() {
        target.putBlocks(20L, null);
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

    @Test(expected = IllegalStateException.class)
    public void removeLastBlock_emptyIndex() {
        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(-1));

        List<IndexedBlockStore.BlockInfo> result = target.removeLast();

        assertTrue(result.isEmpty());
        target.getMaxNumber();
    }

    @Test(expected = IllegalStateException.class)
    public void removeLastBlock_nowEmptyIndex() {
        List<IndexedBlockStore.BlockInfo> expectedResult = mock(List.class);

        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(1));
        index.put(1L, expectedResult);

        List<IndexedBlockStore.BlockInfo> result = target.removeLast();

        assertEquals(expectedResult, result);
        assertFalse(index.containsKey(1L));
        target.getMaxNumber();
    }

    @Test
    public void flush() {
        target.flush();

        verify(indexDB, times(1)).commit();
    }

}
