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

package org.ethereum.datasource;

import co.rsk.peg.ReleaseRequestQueue;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;
import org.iq80.leveldb.DBException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.ethereum.util.ByteUtil.wrap;

public class HashMapDB implements KeyValueDataSource {

    private Map<ByteArrayWrapper, byte[]> storage;
    private boolean clearOnClose = true;

    public HashMapDB() {
        this(new ConcurrentHashMap<>());
    }

    public HashMapDB(Map<ByteArrayWrapper, byte[]> astorage) {
        storage = astorage;
    }

    // Create a cache with removal policy
    // In the future we could use total user RAM instead of a number of elements

    public HashMapDB(int maxSize, boolean accessOrder) {
        // We could use Collections.synchronizedMap() to archieve the concurrency
        // properties, although it won't be optimized for concurrent reads as desired.
        // Anyway the full node should be optimized for blockchain synchronization
        // and not so much for RPC handling, to the synchronizedMap seems appropiate
        // This projects https://github.com/ben-manes/concurrentlinkedhashmap/tree/master/src
        // seems to provide a map with the desired properties.

        this(Collections.synchronizedMap(new MaxSizeHashMap(maxSize,accessOrder)));
    }

    public Map<ByteArrayWrapper, byte[]> getStorageMap() {
        return storage;
    }

    @Override
    public void delete(byte[] arg0) throws DBException {
        storage.remove(wrap(arg0));
    }


    @Override
    public byte[] get(byte[] arg0) throws DBException {
        return storage.get(wrap(arg0));
    }


    @Override
    public byte[] put(byte[] key, byte[] value) throws DBException {
        return storage.put(wrap(key), value);
    }

    @Override
    public void init() {
        clear();
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public String getName() {
        return "in-memory";
    }

    @Override
    public synchronized Set<ByteArrayWrapper> keys() {
        return storage.keySet();
    }

    public synchronized void removeBatch(Map<ByteArrayWrapper, byte[]> rows) {
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
            ByteArrayWrapper key = entry.getKey();
            byte[] keyBytes = key.getData();
            // Make this check explicit so that users know very clearly what to expect
            if (keyBytes == null) throw new NullPointerException();
            delete(keyBytes);
        }
    }

    // Assumes elements are not present. Do not delete.
    public synchronized void addBatch(Map<ByteArrayWrapper, byte[]> rows) {
        storage.putAll(rows);
    }

    @Override
    public synchronized void updateBatch(Map<ByteArrayWrapper, byte[]> rows) {
        // Hashmap does not handle null values. If there are empty values, remove them
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
            ByteArrayWrapper key = entry.getKey();
            byte[] value = entry.getValue();
            byte[] keyBytes = key.getData();

            // Make this check explicit so that users know very clearly what to expect
            if (keyBytes == null || value == null) throw new NullPointerException();
            if (keyBytes.length != 0)
                put(keyBytes , value);
            else
                delete(keyBytes );
        }
        //storage.putAll(rows);

    }

    public synchronized HashMapDB setClearOnClose(boolean clearOnClose) {
        this.clearOnClose = clearOnClose;
        return this;
    }

    public void clear() {
        this.storage.clear();
    }

    @Override
    public synchronized void close() {
        if (clearOnClose) {
            this.storage.clear();
        }
    }

    // HashMapDB has no flush: everything is kept in memory.
    @Override
    public void flush(){

    }
}