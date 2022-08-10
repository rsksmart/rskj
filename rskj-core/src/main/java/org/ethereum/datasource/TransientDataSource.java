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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Datasource without cache. Only stored changes and avoid flushing to base.
public class TransientDataSource implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("transientdatasource");

    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TransientDataSource(@Nonnull KeyValueDataSource base) {
        this.base = Objects.requireNonNull(base);
        this.uncommittedCache = new HashMap<>();

    }


    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();

        try {

            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            value = base.get(key);

            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

        } finally {
            if (traceEnabled) {
                numOfGets.incrementAndGet();
            }

            this.lock.readLock().unlock();
        }

        return value;
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        return put(wrappedKey, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);

        this.lock.writeLock().lock();

        try {
            byte[] priorValue = uncommittedCache.get(wrappedKey);

            if (priorValue != null) {
                if (Arrays.equals(priorValue, value)) {
                    return value;
                }
            } else {
                if (value==null)
                    return null;
            }
            byte[] ret = uncommittedCache.put(wrappedKey, value);
            if (ret==null)
                ret = base.get(wrappedKey.getData()); // get previous value
            return ret;
        } finally {
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }

            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();

        try {
            uncommittedCache.put(wrappedKey, null);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        Stream<ByteArrayWrapper> baseKeys;
        Stream<ByteArrayWrapper> committedKeys;
        Stream<ByteArrayWrapper> uncommittedKeys;
        Set<ByteArrayWrapper> uncommittedKeysToRemove;

        this.lock.readLock().lock();

        try {
            baseKeys = base.keys().stream();

            uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } finally {
            this.lock.readLock().unlock();
        }

        Stream<ByteArrayWrapper> knownKeys = Stream.concat(baseKeys, uncommittedKeys);
        return knownKeys.filter(k -> !uncommittedKeysToRemove.contains(k))
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        this.lock.writeLock().lock();

        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
    }

    public String getName() {
        return "transient-"+base.getName() ;
    }

    public void init() {
        base.init();
    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        this.lock.writeLock().lock();

        try {
            flush();
            base.close();
            uncommittedCache.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void emitLogs() {
        if (!logger.isTraceEnabled()) {
            return;
        }

        this.lock.writeLock().lock();

        try {
            logger.trace("Activity: No. Gets: {}. No. Puts: {}. No. Gets from Store: {}",
                    numOfGets.getAndSet(0),
                    numOfPuts.getAndSet(0),
                    numOfGetsFromStore.getAndSet(0));
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Nonnull
    private static Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize) {
        Map<ByteArrayWrapper, byte[]> cache;
        cache = new MaxSizeHashMap<>(cacheSize, true);
        return cache;
    }
}
