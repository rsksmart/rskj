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

import static org.ethereum.util.ByteUtil.wrap;

public class HashMapDB implements KeyValueDataSource {

    Map<ByteArrayWrapper, byte[]> storage = new HashMap<>();
    private boolean clearOnClose = true;

    @Override
    public synchronized void delete(byte[] arg0) throws DBException {
        storage.remove(wrap(arg0));
    }


    @Override
    public synchronized byte[] get(byte[] arg0) throws DBException {
        return storage.get(wrap(arg0));
    }


    @Override
    public synchronized byte[] put(byte[] key, byte[] value) throws DBException {
        return storage.put(wrap(key), value);
    }

    /**
     * Returns the number of items added to this Mock DB
     *
     * @return int
     */
    public synchronized int getAddedItems() {
        return storage.size();
    }

    @Override
    public void init() {

    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public String getName() {
        return "in-memory";
    }

    @Override
    public synchronized Set<byte[]> keys() {
        Set<byte[]> keys = new HashSet<>();
        for (ByteArrayWrapper key : storage.keySet()){
            keys.add(key.getData());
        }
        return keys;
    }

    @Override
    public synchronized void updateBatch(Map<byte[], byte[]> rows) {
        for (byte[] key :  rows.keySet()){
            storage.put(wrap(key), rows.get(key));
        }
    }

    public HashMapDB setClearOnClose(boolean clearOnClose) {
        this.clearOnClose = clearOnClose;
        return this;
    }

    @Override
    public void close() {
        if (clearOnClose) {
            this.storage.clear();
        }
    }
}