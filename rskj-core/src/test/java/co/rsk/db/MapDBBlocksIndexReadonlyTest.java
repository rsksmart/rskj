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

import org.ethereum.datasource.TransientMap;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class MapDBBlocksIndexReadonlyTest extends MapDBBlocksIndexTest {

    @Override
    protected void setupMode() {
        target = new MapDBBlocksIndex(indexDB, baseIndex, baseMetadata, true);
    }

    @Test
    public void putBlocksTransient() {
        byte[] originalMaxBlock = baseMetadata.get(MAX_BLOCK_NUMBER_KEY);
        long blockNumber = 2L;

        List<IndexedBlockStore.BlockInfo> addedList = mock(List.class);
        target.putBlocks(blockNumber, addedList);

        Assert.assertEquals(addedList, index.get(blockNumber));
        Assert.assertArrayEquals(metadata.get(MAX_BLOCK_NUMBER_KEY), ByteUtil.longToBytes(blockNumber));
        Assert.assertEquals(addedList, target.getBlocksByNumber(blockNumber));

        Assert.assertNull(baseIndex.get(blockNumber));
        Assert.assertEquals(originalMaxBlock, baseMetadata.get(MAX_BLOCK_NUMBER_KEY));
    }

    @Test
    public void removeLastBlockTransient() {
        byte[] originalMaxBlock = baseMetadata.get(MAX_BLOCK_NUMBER_KEY);
        List<IndexedBlockStore.BlockInfo> originalBlockList = mock(List.class);
        long originalBlockNumber = 2L;

        // simulate base collection contained a value
        baseIndex.put(originalBlockNumber, originalBlockList);

        // add the same element again with a different list and a new element
        List<IndexedBlockStore.BlockInfo> addedList = mock(List.class);
        target.putBlocks(originalBlockNumber, addedList);
        List<IndexedBlockStore.BlockInfo> secondAddedList = mock(List.class);
        long newBlockNumber = 3L;
        target.putBlocks(newBlockNumber, secondAddedList);

        Assert.assertArrayEquals(metadata.get(MAX_BLOCK_NUMBER_KEY), ByteUtil.longToBytes(newBlockNumber));
        Assert.assertEquals(addedList, index.get(originalBlockNumber));
        Assert.assertEquals(secondAddedList, index.get(newBlockNumber));
        Assert.assertEquals(addedList, target.getBlocksByNumber(originalBlockNumber));
        Assert.assertEquals(secondAddedList, target.getBlocksByNumber(newBlockNumber));

        target.removeLast(); // remove 3L
        Assert.assertEquals(1, index.size());
        Assert.assertArrayEquals(ByteUtil.longToBytes(originalBlockNumber), metadata.get(MAX_BLOCK_NUMBER_KEY));
        target.removeLast(); // remove 2L
        Assert.assertEquals(0, index.size());
        Assert.assertArrayEquals(ByteUtil.longToBytes(1L), metadata.get(MAX_BLOCK_NUMBER_KEY));

        Assert.assertEquals(1, baseIndex.size());
        Assert.assertNull(baseIndex.get(newBlockNumber));
        Assert.assertEquals(originalBlockList, baseIndex.get(originalBlockNumber));
        Assert.assertEquals(originalMaxBlock, baseMetadata.get(MAX_BLOCK_NUMBER_KEY));
    }

    @Test
    public void flush() {
        target.flush();

        verify(indexDB, never()).commit();
    }

    @Test
    public void indexIsTransientTransient() {
        Assert.assertTrue(target.getIndex() instanceof TransientMap);
    }

    @Test
    public void metadataIsTransientTransient() {
        Assert.assertTrue(target.getMetadata() instanceof TransientMap);
    }

}
