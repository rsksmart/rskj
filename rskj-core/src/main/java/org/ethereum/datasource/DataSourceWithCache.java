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
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCache implements KeyValueDataSource {
    private final int cacheSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;

    public DataSourceWithCache(KeyValueDataSource base, int cacheSize) {
        this.cacheSize = cacheSize;
        this.base = base;
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float)0.75, false);
        this.committedCache = new MaxSizeHashMap<>(cacheSize, true);
    }

    @Override
    public synchronized byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        if (committedCache.containsKey(wrappedKey)) {
            return committedCache.get(wrappedKey);
        }

        if (uncommittedCache.containsKey(wrappedKey)) {
            return uncommittedCache.get(wrappedKey);
        }

        byte[] value = base.get(key);

        //null value, as expected, is allowed here to be stored in committedCache
        committedCache.put(wrappedKey, value);

        return value;
    }

    @Override
    public synchronized byte[] put(byte[] key, byte[] value) {
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        return put(wrappedKey, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);
        // here I could check for equal data or just move to the uncommittedCache.
        byte[] priorValue = committedCache.get(wrappedKey);

        if (priorValue != null && Arrays.equals(priorValue, value)) {
            return value;
        }

        committedCache.remove(wrappedKey);
        this.putKeyValue(wrappedKey, value);

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        uncommittedCache.put(key, value);

        if (uncommittedCache.size() > cacheSize) {
            this.flush();
        }
    }

    @Override
    public synchronized void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        // always mark for deletion if we don't know the state in the underlying store
        if (!committedCache.containsKey(wrappedKey)) {
            this.putKeyValue(wrappedKey, null);
            return;
        }

        byte[] valueToRemove = committedCache.get(wrappedKey);

        // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
        if (valueToRemove != null) {
            this.putKeyValue(wrappedKey, null);
            committedCache.remove(wrappedKey);
        }
    }

    @Override
    public synchronized Set<byte[]> keys() {
        Stream<ByteArrayWrapper> baseKeys = base.keys().stream().map(ByteArrayWrapper::new);
        Stream<ByteArrayWrapper> committedKeys = committedCache.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(Map.Entry::getKey);
        Stream<ByteArrayWrapper> uncommittedKeys = uncommittedCache.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(Map.Entry::getKey);
        Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<ByteArrayWrapper> knownKeys = Stream.concat(Stream.concat(baseKeys, committedKeys), uncommittedKeys)
                .collect(Collectors.toSet());
        knownKeys.removeAll(uncommittedKeysToRemove);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return knownKeys.stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toSet());
    }

    @Override
    public synchronized void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        rows.forEach(this::put);
        keysToRemove.forEach(this::delete);
    }

    @Override
    public synchronized void flush() {
        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.uncommittedCache.forEach((key, value) -> {
            if (value != null) {
                uncommittedBatch.put(key, value);
            }
        });

        Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
        base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
        committedCache.putAll(uncommittedCache);
        uncommittedCache.clear();
    }

    public String getName() {
        return base.getName() + "-with-uncommittedCache";
    }

    public synchronized void init() {
        base.init();
    }

    public synchronized boolean isAlive() {
        return base.isAlive();
    }

    public synchronized void close() {
        flush();
        base.close();
        uncommittedCache.clear();
        committedCache.clear();
    }
}
