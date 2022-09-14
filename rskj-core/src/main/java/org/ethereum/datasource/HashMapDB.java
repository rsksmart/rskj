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

import org.ethereum.db.ByteArrayWrapper;
import org.iq80.leveldb.DBException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.ethereum.util.ByteUtil.wrap;

public class HashMapDB implements KeyValueDataSource<Iterator<ByteArrayWrapper> > {

    private final Map<ByteArrayWrapper, byte[]> storage = new ConcurrentHashMap<>();
    private boolean clearOnClose = true;

    @Override
    public void delete(byte[] arg0) throws DBException {
        storage.remove(wrap(arg0));
    }


    @Override
    public byte[] get(byte[] arg0) throws DBException {
        Objects.requireNonNull(arg0);
        return storage.get(wrap(arg0));
    }


    @Override
    public byte[] put(byte[] key, byte[] value) throws DBException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return storage.put(wrap(key), value);
    }

    @Override
    public void init() {
        this.storage.clear();
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
        return new HashSet<>(storage.keySet());
    }

    @Override
    public synchronized Iterator<ByteArrayWrapper> iterator() {
        return storage.keySet().iterator();
    }

    @Override
    public synchronized void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }
        rows.keySet().removeAll(keysToRemove);
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
            ByteArrayWrapper wrappedKey = entry.getKey();
            byte[] key = wrappedKey.getData();
            byte[] value = entry.getValue();
            put(key , value);
        }

        for (ByteArrayWrapper keyToRemove : keysToRemove) {
            delete(keyToRemove.getData());
        }
    }

    public synchronized HashMapDB setClearOnClose(boolean clearOnClose) {
        this.clearOnClose = clearOnClose;
        return this;
    }

    @Override
    public synchronized void close() {
        if (clearOnClose) {
            this.storage.clear();
        }
    }

    @Override
    public void flush(){
        // HashMapDB has no flush: everything is kept in memory.
    }
}