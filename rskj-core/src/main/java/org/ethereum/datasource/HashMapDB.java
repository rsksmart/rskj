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
import org.ethereum.db.ByteArrayWrapper;
import org.iq80.leveldb.DBException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.ethereum.util.ByteUtil.wrap;

public class HashMapDB implements KeyValueDataSource {

    private final Map<ByteArrayWrapper, byte[]> storage = new ConcurrentHashMap<>();
    private boolean clearOnClose = true;

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

    @Override
    public synchronized void updateBatch(Map<ByteArrayWrapper, byte[]> rows) {
        // Hashmap does not handle null values. If there are empty values, remove them
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
            byte[] key = entry.getValue();
            byte[] value = entry.getValue();
            // Make this check explicit so that users know very clearly what to expect
            if (key == null || value == null) throw new NullPointerException();
            if (key.length != 0)
                put(key, value);
            else
                delete(key);
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