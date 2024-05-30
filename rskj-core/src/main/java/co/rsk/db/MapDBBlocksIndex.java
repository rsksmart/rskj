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

import co.rsk.crypto.Keccak256;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.ByteUtil;
import org.mapdb.DB;
import org.mapdb.Serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;

/**
 * MapDBBlocksIndex is a thread safe implementation of BlocksIndex with mapDB providing the underlying functionality.
 */
public class MapDBBlocksIndex implements BlocksIndex {

    private static final String MAX_BLOCK_NUMBER_KEY = "max_block";

    private final Map<Long, List<IndexedBlockStore.BlockInfo>> index;
    private final Map<String, byte[]> metadata;

    private final DB indexDB;

    public MapDBBlocksIndex(DB indexDB) {

        this.indexDB = indexDB;

        index = indexDB.hashMapCreate("index")
                .keySerializer(Serializer.LONG)
                .valueSerializer(BLOCK_INFO_SERIALIZER)
                .counterEnable()
                .makeOrGet();

        metadata = indexDB.hashMapCreate("metadata")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY)
                .makeOrGet();

        // Max block number initialization assumes an index without gap
        if (!metadata.containsKey(MAX_BLOCK_NUMBER_KEY)) {
            long maxBlockNumber = (long) index.size() - 1;
            metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(maxBlockNumber));
        }
    }

    @Override
    public boolean isEmpty() {
        return index.isEmpty();
    }

    @Override
    public long getMaxNumber() {
        if (index.isEmpty()) {
            throw new IllegalStateException("Index is empty");
        }

        return ByteUtil.byteArrayToLong(metadata.get(MAX_BLOCK_NUMBER_KEY));
    }

    @Override
    public long getMinNumber() {
        if (index.isEmpty()) {
            throw new IllegalStateException("Index is empty");
        }

        return getMaxNumber() - index.size() + 1;
    }

    @Override
    public boolean contains(long blockNumber) {
        return index.containsKey(blockNumber);
    }

    @Override
    public List<IndexedBlockStore.BlockInfo> getBlocksByNumber(long blockNumber) {
        return index.getOrDefault(blockNumber, new ArrayList<>());
    }

    @Override
    public void putBlocks(long blockNumber, List<IndexedBlockStore.BlockInfo> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("Block list cannot be empty nor null.");
        }

        long maxNumber = -1;
        if (index.size() > 0) {
            maxNumber = getMaxNumber();
        }
        if (blockNumber > maxNumber) {
            metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(blockNumber));
        }

        index.put(blockNumber, blocks);
    }

    @Override
    public void removeBlock(long blockNumber, Keccak256 blockHash) {
        List<IndexedBlockStore.BlockInfo> blockInfoList = index.get(blockNumber);

        if (blockInfoList == null) {
            return;
        }

        List<IndexedBlockStore.BlockInfo> toRemove = new ArrayList<>();

        for (IndexedBlockStore.BlockInfo bInfo : blockInfoList) {
            if (bInfo.getHash().equals(blockHash)) {
                toRemove.add(bInfo);
            }
        }
        blockInfoList.removeAll(toRemove);
        if (blockInfoList.isEmpty()) {
            //We are not allowing empty list into the index
            index.remove(blockNumber);
        } else {
            //MapDB does not support update of values in a map so we use the list as a immutable object
            index.put(blockNumber, blockInfoList);
        }
    }

    @Override
    public List<IndexedBlockStore.BlockInfo> removeLast() {
        long lastBlockNumber = -1;
        if (index.size() > 0) {
            lastBlockNumber = getMaxNumber();
        }

        List<IndexedBlockStore.BlockInfo> result = index.remove(lastBlockNumber);

        if (result == null) {
            result = new ArrayList<>();
        }

        metadata.put(MAX_BLOCK_NUMBER_KEY, ByteUtil.longToBytes(lastBlockNumber - 1));

        return result;
    }

    @Override
    public void flush() {
        indexDB.commit();
    }

    @Override
    public void close() {
        indexDB.close();
    }
}