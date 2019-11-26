/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCache implements KeyValueDataSource {
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public DataSourceWithCache(KeyValueDataSource base, int cacheSize) {
        this.base = base;
        this.uncommittedCache = new HashMap<>();
        this.committedCache = Collections.synchronizedMap(new MaxSizeHashMap<>(cacheSize, true));
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        lock.readLock().lock();
        try {
            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            if (committedCache.containsKey(wrappedKey)) {
                return committedCache.get(wrappedKey);
            } else {
                byte[] value = base.get(key);

                //null value, as expected, is allowed here to be stored in committedCache
                committedCache.put(wrappedKey, value);

                return value;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        return put(wrappedKey, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);

        lock.writeLock().lock();
        try {
            // here I could check for equal data or just move to the uncommittedCache.
            byte[] priorValue = committedCache.get(wrappedKey);
            if (priorValue != null && Arrays.equals(priorValue, value)) {
                return value;
            }

            committedCache.remove(wrappedKey);
            uncommittedCache.put(wrappedKey, value);
        } finally {
            lock.writeLock().unlock();
        }

        return value;
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        lock.writeLock().lock();

        try {
            // always mark for deletion if we don't know the state in the underlying store
            if (!committedCache.containsKey(wrappedKey)) {
                uncommittedCache.put(wrappedKey, null);
                return;
            }

            byte[] valueToRemove = committedCache.get(wrappedKey);
            // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
            if (valueToRemove != null) {
                committedCache.remove(wrappedKey);
                uncommittedCache.put(wrappedKey, null);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() {
        lock.readLock().lock();

        try {
            Stream<ByteArrayWrapper> baseKeys = base.keys().stream().map(ByteArrayWrapper::new);
            Stream<ByteArrayWrapper> uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            Set<ByteArrayWrapper> knownKeys = Stream.concat(baseKeys, uncommittedKeys)
                    .collect(Collectors.toSet());
            knownKeys.removeAll(uncommittedKeysToRemove);

            // note that toSet doesn't work with byte[], so we have to do this extra step
            return knownKeys.stream()
                    .map(ByteArrayWrapper::getData)
                    .collect(Collectors.toSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        lock.writeLock().lock();
        try {
            // remove overlapping entries
            rows.keySet().removeAll(keysToRemove);

            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public synchronized void flush() {
        lock.writeLock().lock();
        try {
            Map<ByteArrayWrapper, byte[]> uncommittedBatch = uncommittedCache.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            committedCache.putAll(uncommittedCache);
            uncommittedCache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getName() {
        return base.getName() + "-with-uncommittedCache";
    }

    public void init() {
        base.init();
    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        flush();
        base.close();
        uncommittedCache.clear();
        committedCache.clear();
    }
}
