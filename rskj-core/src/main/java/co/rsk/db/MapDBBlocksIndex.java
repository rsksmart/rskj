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

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.ByteUtil;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;

/**
 * MapDBBlocksIndex is a thread safe implementation of BlocksIndex with mapDB providing the underlying functionality.
 */
public class MapDBBlocksIndex implements BlocksIndex {

    static final String MAX_BLOCK_NUMBER_KEY = "max_block";

    private final Map<Long, List<IndexedBlockStore.BlockInfo>> index;
    private final Map<String, byte[]> metadata;

    private final DB indexDB;

    public static MapDBBlocksIndex create(DB indexDB) {
        return new MapDBBlocksIndex(indexDB);
    }

    @VisibleForTesting
    static MapDBBlocksIndex createForTesting(DB indexDB, Map<Long, List<IndexedBlockStore.BlockInfo>> index, Map<String, byte[]> metadata) {
        return new MapDBBlocksIndex(indexDB, index, metadata);
    }

    protected MapDBBlocksIndex(DB indexDB) {
        this(indexDB, buildIndex(indexDB), buildMetadata(indexDB));
    }

    MapDBBlocksIndex(DB indexDB, Map<Long, List<IndexedBlockStore.BlockInfo>> index, Map<String, byte[]> metadata) {
        this.indexDB = indexDB;
        this.index = wrapIndex(index);
        this.metadata = wrapIndex(metadata);

        // Max block number initialization assumes an index without gap
        if (!metadata.containsKey(MAX_BLOCK_NUMBER_KEY)) { // NOSONAR: computeIfAbsent is not implemented in TransientMap
            long maxBlockNumber = (long) index.size() - 1;
            metadata.put(MAX_BLOCK_NUMBER_KEY,  ByteUtil.longToBytes(maxBlockNumber));
        }
    }

    protected <K, V> Map<K, V> wrapIndex(Map<K, V> base) {
        // no wrap needed
        return base;
    }

    @Override
    public final boolean isEmpty() {
        return index.isEmpty();
    }

    @Override
    public final long getMaxNumber() {
        if (index.isEmpty()) {
            throw new IllegalStateException("Index is empty");
        }

        return ByteUtil.byteArrayToLong(metadata.get(MAX_BLOCK_NUMBER_KEY));
    }

    @Override
    public final long getMinNumber() {
        if (index.isEmpty()) {
            throw new IllegalStateException("Index is empty");
        }

        return getMaxNumber() - index.size() + 1;
    }

    @Override
    public final boolean contains(long blockNumber) {
        return index.containsKey(blockNumber);
    }

    @Override
    public final List<IndexedBlockStore.BlockInfo> getBlocksByNumber(long blockNumber) {
        return index.getOrDefault(blockNumber, new ArrayList<>());
    }

    @Override
    public final void putBlocks(long blockNumber, List<IndexedBlockStore.BlockInfo> blocks) {
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
    public final List<IndexedBlockStore.BlockInfo> removeLast() {
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
    public final void close() {
        indexDB.close();
    }

    @VisibleForTesting
    Map<Long, List<IndexedBlockStore.BlockInfo>> getIndex() {
        return index;
    }

    @VisibleForTesting
    Map<String, byte[]> getMetadata() {
        return metadata;
    }

    private static HTreeMap<String, byte[]> buildMetadata(DB indexDB) {
        return indexDB.hashMapCreate("metadata")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY)
                .makeOrGet();
    }

    private static HTreeMap<Long, List<IndexedBlockStore.BlockInfo>> buildIndex(DB indexDB) {
        return indexDB.hashMapCreate("index")
                .keySerializer(Serializer.LONG)
                .valueSerializer(BLOCK_INFO_SERIALIZER)
                .counterEnable()
                .makeOrGet();
    }
}
