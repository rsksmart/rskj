/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.db.BlockStoreEncoder;
import co.rsk.net.BlockCache;
import co.rsk.remasc.Sibling;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.IndexedBlockStore.BlockInfo;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;


public class IndexedBlockStoreTest {

    private BlockStoreEncoder blockStoreEncoder;
    private Map<Long, List<BlockInfo>> indexMap;
    private KeyValueDataSource keyValueDataSource;
    private DB indexDB;
    private IndexedBlockStore target;
    private BlockCache blockCache;
    private MaxSizeHashMap<Keccak256, Map<Long, List<Sibling>>> remascCache;

    @Before
    public void setup() {
        blockStoreEncoder = mock(BlockStoreEncoder.class);
        indexMap = mock(Map.class);
        keyValueDataSource = mock(KeyValueDataSource.class);
        indexDB = mock(DB.class);
        blockCache = mock(BlockCache.class);
        remascCache = mock(MaxSizeHashMap.class);

        target = new IndexedBlockStore(
                blockStoreEncoder,
                indexMap,
                keyValueDataSource,
                indexDB,
                blockCache,
                remascCache
        );
    }

    @Test
    public void getBlockHeaderByHash_success() {
        byte[] headerResponse = {0x02, 0x02};
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];

        BlockHeader blockHeaderDecoded = mock(BlockHeader.class);

        when(hash.getBytes()).thenReturn(hashBytes);
        when(keyValueDataSource.get(hashBytes)).thenReturn(headerResponse);
        when(blockStoreEncoder.decodeBlockHeader(headerResponse)).thenReturn(Optional.of(blockHeaderDecoded));

        Optional<BlockHeader> result = target.getBlockHeaderByHash(hash);

        assertEquals(Optional.of(blockHeaderDecoded), result);
    }

    @Test
    public void getBlockHeaderByHash_not_found() {
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];

        when(hash.getBytes()).thenReturn(hashBytes);
        when(keyValueDataSource.get(hashBytes)).thenReturn(null);

        Optional<BlockHeader> result = target.getBlockHeaderByHash(hash);

        assertFalse(result.isPresent());
    }

    @Test
    public void saveBlockHeader_contained_in_cache_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        Keccak256 hash = mock(Keccak256.class);

        when(blockHeader.getHash()).thenReturn(hash);
        when(blockCache.getBlockHeaderByHash(hash)).thenReturn(Optional.of(blockHeader));

        target.saveBlockHeader(blockHeader);

        verify(blockCache, times(0)).addBlockHeader(blockHeader);
    }

    @Test
    public void saveBlockHeader_cache_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        Keccak256 hash = mock(Keccak256.class);

        when(blockHeader.getHash()).thenReturn(hash);
        when(blockCache.getBlockHeaderByHash(hash)).thenReturn(Optional.empty());

        target.saveBlockHeader(blockHeader);

        verify(blockCache, times(1)).addBlockHeader(blockHeader);
    }


    @Test
    public void saveBlockHeader_contained_in_data_store_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];
        byte[] encodedHeader = new byte[] {0x0F, 0x0A};

        when(blockHeader.getHash()).thenReturn(hash);
        when(hash.getBytes()).thenReturn(hashBytes);
        when(keyValueDataSource.get(hashBytes)).thenReturn(encodedHeader);

        target.saveBlockHeader(blockHeader);

        verify(keyValueDataSource, times(0)).put(hashBytes, encodedHeader);
    }

    @Test
    public void saveBlockHeader_data_store_success() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];
        byte[] encodedHeader = new byte[] {0x0F, 0x0A};

        when(blockHeader.getHash()).thenReturn(hash);
        when(hash.getBytes()).thenReturn(hashBytes);
        when(keyValueDataSource.get(hashBytes)).thenReturn(null);
        when(blockStoreEncoder.encodeBlockHeader(blockHeader)).thenReturn(encodedHeader);
        target.saveBlockHeader(blockHeader);

        verify(keyValueDataSource, times(1)).put(hashBytes, encodedHeader);
    }


    @Test
    public void saveBlock_cache_success() {
        Block block = mock(Block.class);
        Keccak256 hash = mock(Keccak256.class);

        when(block.getHash()).thenReturn(hash);

        target.saveBlock(block, mock(BlockDifficulty.class), true);

        verify(blockCache, times(1)).addBlock(block);
    }

    @Test
    public void saveBlock_nothing_found() {
        Block block = mock(Block.class);
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];
        byte[] encodedBlock = {0x0F, 0x0A};

        when(block.getHash()).thenReturn(hash);
        when(hash.getBytes()).thenReturn(hashBytes);
        when(blockStoreEncoder.encodeBlock(block)).thenReturn(encodedBlock);
        target.saveBlock(block, mock(BlockDifficulty.class), true);

        verify(keyValueDataSource, times(1)).put(hashBytes, encodedBlock);
    }

    @Test
    public void saveBlock_header_found() {
        Block block = mock(Block.class);
        byte[] encodedBlock = {0x0F, 0x0F};
        Keccak256 hash = mock(Keccak256.class);
        byte[] hashBytes = new byte[32];
        byte[] encodedHeader = {0x0A, 0x0A};


        when(block.getHash()).thenReturn(hash);
        when(hash.getBytes()).thenReturn(hashBytes);
        when(blockStoreEncoder.encodeBlock(block)).thenReturn(encodedBlock);
        when(keyValueDataSource.get(hashBytes)).thenReturn(encodedHeader);
        when(blockStoreEncoder.decodeBlock(encodedHeader)).thenReturn(Optional.empty());
        target.saveBlock(block, mock(BlockDifficulty.class), true);

        verify(keyValueDataSource, times(1)).put(hashBytes, encodedBlock);
    }
}