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
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.HTreeMap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MapDBBlocksIndexTest {

    private MapDBBlocksIndex target;
    private HTreeMap indexMap;
    private DB indexDB;

    @Before
    public void setUp() {
        indexDB = mock(DB.class);
        DB.HTreeMapMaker hTreeMapMaker = mock(DB.HTreeMapMaker.class);
        indexMap = mock(HTreeMap.class);

        when(indexDB.hashMapCreate(anyString())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.valueSerializer(any())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.keySerializer(any())).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.counterEnable()).thenReturn(hTreeMapMaker);
        when(hTreeMapMaker.makeOrGet()).thenReturn(indexMap);

        target = new MapDBBlocksIndex(indexDB);
    }

    @Test
    public void getMaxNumber_empty() {
        assertEquals(target.getMaxNumber(),-1);
    }

    @Test
    public void getMaxNumber_nonEmpty() {
        when(indexMap.size()).thenReturn(10);
        assertEquals(target.getMaxNumber(),9);
    }

    @Test
    public void contains_true() {
        long blockNumber = 12;
        when(indexMap.containsKey(eq(blockNumber))).thenReturn(true);

        assertTrue(target.contains(blockNumber));
    }

    @Test
    public void contains_false() {
        long blockNumber = 12;
        when(indexMap.containsKey(eq(blockNumber))).thenReturn(false);

        assertFalse(target.contains(blockNumber));
    }

    @Test
    public void getBlocksByNumber_noneFound() {
        long blockNumber = 20;
        when(indexMap.getOrDefault(eq(blockNumber), anyList())).thenReturn(new ArrayList<>());
        List<IndexedBlockStore.BlockInfo> result = target.getBlocksByNumber(blockNumber);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getBlocksByNumber_found() {
        long blockNumber = 20;
        List expectedResult = mock(List.class);

        when(indexMap.getOrDefault(eq(blockNumber), anyList())).thenReturn(expectedResult);
        List<IndexedBlockStore.BlockInfo> result = target.getBlocksByNumber(blockNumber);

        assertEquals(expectedResult, result);
    }

    @Test
    public void putBlocks() {
        long blockNumber = 20;
        List<IndexedBlockStore.BlockInfo> putBlocks = mock(List.class);

        target.putBlocks(blockNumber, putBlocks);

        verify(indexMap, times(1)).put(eq(blockNumber), eq(putBlocks));
    }

    @Test
    public void removeBlocksByNumber_found() {
        long blockNumber = 20;
        List expectedResult = mock(List.class);

        when(indexMap.remove(eq(blockNumber))).thenReturn(expectedResult);

        List<IndexedBlockStore.BlockInfo> result = target.removeBlocksByNumber(blockNumber);

        assertEquals(expectedResult, result);
    }

    @Test
    public void removeBlocksByNumber_notFound() {
        long blockNumber = 20;

        List<IndexedBlockStore.BlockInfo> result = target.removeBlocksByNumber(blockNumber);

        assertTrue(result.isEmpty());
    }

    @Test
    public void flush() {
        target.flush();

        verify(indexDB, times(1)).commit();
    }
}