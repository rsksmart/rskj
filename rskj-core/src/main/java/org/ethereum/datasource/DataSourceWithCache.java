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

import co.rsk.util.FormatUtils;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");

    private final int cacheSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> unsafeCommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;
    private final Path cacheSnapshotPath;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize, @Nullable Path cacheSnapshotPath) {
        this.cacheSize = cacheSize;
        this.base = Objects.requireNonNull(base);
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float)0.75, false);
        this.unsafeCommittedCache = makeCommittedCache(cacheSize, cacheSnapshotPath);
        this.committedCache = Collections.synchronizedMap(this.unsafeCommittedCache);
        this.cacheSnapshotPath = cacheSnapshotPath;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();

        try {
            if (committedCache.containsKey(wrappedKey)) {
                return committedCache.get(wrappedKey);
            }

            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            value = base.get(key);

            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

            //null value, as expected, is allowed here to be stored in committedCache
            committedCache.put(wrappedKey, value);
        }
        finally {
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
            // here I could check for equal data or just move to the uncommittedCache.
            byte[] priorValue = committedCache.get(wrappedKey);

            if (priorValue != null && Arrays.equals(priorValue, value)) {
                return value;
            }

            committedCache.remove(wrappedKey);
            this.putKeyValue(wrappedKey, value);
        }
        finally {
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }

            this.lock.writeLock().unlock();
        }

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        uncommittedCache.put(key, value);

        if (uncommittedCache.size() > cacheSize) {
            this.flush();
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();

        try {
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
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() {
        Stream<ByteArrayWrapper> baseKeys;
        Stream<ByteArrayWrapper> committedKeys;
        Stream<ByteArrayWrapper> uncommittedKeys;
        Set<ByteArrayWrapper> uncommittedKeysToRemove;

        this.lock.readLock().lock();

        try {
            baseKeys = base.keys().stream().map(ByteArrayWrapper::new);
            committedKeys = committedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        finally {
            this.lock.readLock().unlock();
        }

        Set<ByteArrayWrapper> knownKeys = Stream.concat(Stream.concat(baseKeys, committedKeys), uncommittedKeys)
                .collect(Collectors.toSet());
        knownKeys.removeAll(uncommittedKeysToRemove);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return knownKeys.stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toSet());
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
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.lock.writeLock().lock();

        try {
            long saveTime = System.nanoTime();

            this.uncommittedCache.forEach((key, value) -> {
                if (value != null) {
                    uncommittedBatch.put(key, value);
                }
            });

            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            committedCache.putAll(uncommittedCache);
            uncommittedCache.clear();

            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
        }
        finally {
            this.lock.writeLock().unlock();
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
        this.lock.writeLock().lock();

        try {
            flush();
            base.close();
            if (cacheSnapshotPath != null) {
                saveCommittedCache(unsafeCommittedCache, cacheSnapshotPath);
            }
            uncommittedCache.clear();
            committedCache.clear();
        }
        finally {
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
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    private static Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize, @Nullable Path cacheSnapshotPath) {
        Map<ByteArrayWrapper, byte[]> cache = new MaxSizeHashMap<>(cacheSize, true);
        if (cacheSnapshotPath == null) {
            return cache;
        }

        File cacheSnapshotFile = cacheSnapshotPath.toFile();
        if (!cacheSnapshotFile.exists()) {
            return cache;
        }

        try (DataInputStream inStream = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheSnapshotFile)))) {
            int count = inStream.readInt();
            if (count < 1) {
                throw new IOException("Invalid data: number of entries");
            }
            int readSize;
            for (int i = 0; i < count; i++) {
                int keySize = inStream.readInt();
                if (keySize < 1) {
                    throw new IOException("Invalid data: key size");
                }
                byte[] key = new byte[keySize];
                readSize = inStream.read(key);
                if (readSize != keySize) {
                    throw new IOException("Invalid data: actual key length");
                }

                int valueSize = inStream.readInt();
                if (valueSize < -1) {
                    throw new IOException("Invalid data: value size");
                }
                byte[] value = null;
                if (valueSize > 0) {
                    value = new byte[valueSize];
                    readSize = inStream.read(value);
                    if (readSize != valueSize) {
                        throw new IOException("Invalid data: actual value length");
                    }
                } else if (valueSize == 0) {
                    value = new byte[0];
                }

                cache.put(ByteUtil.wrap(key), value);
            }
        } catch (IOException e) {
            cache.clear();
            boolean delResult = cacheSnapshotFile.delete();
            logger.error("Cannot read from cache snapshot file. File deletion result: '{}'", delResult, e);
        }

        return cache;
    }

    private static void saveCommittedCache(@Nonnull Map<ByteArrayWrapper, byte[]> cache, @Nonnull Path cacheSnapshotPath) {
        File tempFile = null;
        try {
            File cacheSnapshotFile = cacheSnapshotPath.toFile();
            String relativePath = Optional.ofNullable(cacheSnapshotFile.getParentFile())
                    .map(File::getName)
                    .orElse("") + "/" + cacheSnapshotFile.getName();

            if (cache.isEmpty()) {
                logger.info("Cache is empty. Nothing to save to '{}'", relativePath);

                if (cacheSnapshotFile.exists() && !cacheSnapshotFile.delete()) {
                    throw new IOException("Cannot delete existing cache snapshot file '" + relativePath + "'");
                }
                return;
            }

            tempFile = File.createTempFile("prefix-", "-suffix");

            int count = cache.size();
            try (DataOutputStream outStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
                outStream.writeInt(count);

                for (Map.Entry<ByteArrayWrapper, byte[]> entry : cache.entrySet()) {
                    byte[] key = entry.getKey().getData();
                    outStream.writeInt(key.length);
                    outStream.write(key);

                    byte[] value = entry.getValue();
                    if (value == null) {
                        outStream.writeInt(-1);
                    } else {
                        outStream.writeInt(value.length);
                        if (value.length > 0) {
                            outStream.write(value);
                        }
                    }
                }
            }

            if (cacheSnapshotFile.exists() && !cacheSnapshotFile.delete()) {
                throw new IOException("Cannot delete existing cache snapshot file '" + relativePath + "'");
            }

            if (!tempFile.renameTo(cacheSnapshotFile)) {
                throw new IOException("Cannot move temp file to '" + relativePath + "'");
            }

            logger.info("Saved {} cache entries in '{}'", count, relativePath);
        } catch (IOException e) {
            logger.error("Cannot save cache snapshot to file", e);

            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }
}
