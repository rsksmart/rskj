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

package org.ethereum.db;

import co.rsk.core.RskAddress;
import co.rsk.db.ContractDetailsImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * A store for contract details.
 */
public class DetailsDataStore {

    private static final Logger gLogger = LoggerFactory.getLogger("general");

    private final Map<RskAddress, ContractDetails> cache = new ConcurrentHashMap<>();
    private final Set<RskAddress> removes = new HashSet<>();

    private final KeyValueDataSource db;
    private final int memoryStorageLimit;
    private TrieStore.Pool trieStorePool;

    public DetailsDataStore(KeyValueDataSource db, TrieStore.Pool trieStorePool, int memoryStorageLimit) {
        this.db = db;
        this.trieStorePool = trieStorePool;
        this.memoryStorageLimit = memoryStorageLimit;
    }

    public synchronized ContractDetails get(RskAddress addr, byte[] codeHash) {
        ContractDetails details = cache.get(addr);
        boolean isDifferentCodeHash = false;

        if (details == null) {

            if (removes.contains(addr)) {
                return null;
            }
            byte[] data = db.get(addr.getBytes());
            if (data == null) {
                return null;
            }

            details = createContractDetails(data, trieStorePool, memoryStorageLimit);
            cache.put(addr, details);

            float out = ((float) data.length) / 1048576;
            if (out > 10) {
                String sizeFmt = format("%02.2f", out);
                gLogger.debug("loaded: address: {}, size: {}MB", addr, sizeFmt);
            }
        } else {
            isDifferentCodeHash = !Arrays.equals(codeHash, details.getCodeHash());
            if (details.getCode() != null && isDifferentCodeHash) {
                byte[] oldCode = details.getCode();
                String dataSourceName = ((ContractDetailsImpl) details).getDataSourceName();
                TrieStoreImpl trieStore = (TrieStoreImpl) trieStorePool.getInstanceFor(dataSourceName);
                trieStore.storeValue(Keccak256Helper.keccak256(oldCode), oldCode);
            }
        }

        // we avoid doing twice codeHash comparison
        if (isDifferentCodeHash || !Arrays.equals(codeHash, details.getCodeHash())) {
            ((ContractDetailsImpl) details).fixCodeBy(codeHash);
        }

        return details;
    }

    private ContractDetails createContractDetails(
            byte[] data,
            TrieStore.Pool trieStorePool,
            int memoryStorageLimit) {
        return new ContractDetailsImpl(data, trieStorePool, memoryStorageLimit);
    }

    public synchronized void update(RskAddress addr, ContractDetails contractDetails) {
        contractDetails.setAddress(addr.getBytes());
        ContractDetails cachedDetails = cache.get(addr);
        if (cachedDetails != null && cachedDetails.getCode() != null) {
            byte[] oldCode = cachedDetails.getCode();
            String dataSourceName = ((ContractDetailsImpl) contractDetails).getDataSourceName();
            TrieStoreImpl trieStore = (TrieStoreImpl) trieStorePool.getInstanceFor(dataSourceName);
            trieStore.storeValue(Keccak256Helper.keccak256(oldCode), oldCode);
        }

        cache.put(addr, contractDetails);
        removes.remove(addr);
    }

    public synchronized void remove(RskAddress addr) {
        cache.remove(addr);
        removes.add(addr);
    }

    public synchronized void flush() {
        long keys = cache.size();

        long start = System.nanoTime();
        long totalSize = flushInternal();
        long finish = System.nanoTime();

        float flushSize = (float) totalSize / 1_048_576;
        float flushTime = (float) (finish - start) / 1_000_000;
        gLogger.trace(format("Flush details in: %02.2f ms, %d keys, %02.2fMB", flushTime, keys, flushSize));
    }

    private long flushInternal() {
        long totalSize = 0;

        Map<byte[], byte[]> batch = new HashMap<>();
        for (Map.Entry<RskAddress, ContractDetails> entry : cache.entrySet()) {
            ContractDetails details = entry.getValue();
            details.syncStorage();

            byte[] key = entry.getKey().getBytes();

            byte[] value = details.getEncoded();

            batch.put(key, value);
            totalSize += value.length;
        }

        db.updateBatch(batch);

        for (RskAddress key : removes) {
            db.delete(key.getBytes());
        }

        cache.clear();
        removes.clear();

        return totalSize;
    }


    public synchronized Set<RskAddress> keys() {
        Stream<RskAddress> keys = Stream.concat(
                cache.keySet().stream(),
                db.keys().stream().map(RskAddress::new)
        );
        return keys.collect(Collectors.toSet());
    }

}
