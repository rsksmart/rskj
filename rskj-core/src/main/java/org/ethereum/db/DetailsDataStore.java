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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.ContractDetailsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

/**
 * A store for contract details.
 */
public class DetailsDataStore {

    private static final Logger gLogger = LoggerFactory.getLogger("general");

    private final Map<RskAddress, ContractDetails> cache = new ConcurrentHashMap<>();
    private final Set<RskAddress> removes = new HashSet<>();

    private final RskSystemProperties config;
    private final DatabaseImpl db;

    public DetailsDataStore(RskSystemProperties config, DatabaseImpl db) {
        this.config = config;
        this.db = db;
    }

    public synchronized ContractDetails get(RskAddress addr) {
        ContractDetails details = cache.get(addr);

        if (details == null) {

            if (removes.contains(addr)) {
                return null;
            }
            byte[] data = db.get(addr.getBytes());
            if (data == null) {
                return null;
            }

            details = createContractDetails(data);
            cache.put(addr, details);

            float out = ((float) data.length) / 1048576;
            if (out > 10) {
                String sizeFmt = format("%02.2f", out);
                gLogger.debug("loaded: address: {}, size: {}MB", addr, sizeFmt);
            }
        }

        return details;
    }

    protected ContractDetails createContractDetails(byte[] data) {
        return new ContractDetailsImpl(config, data);
    }

    public synchronized void update(RskAddress addr, ContractDetails contractDetails) {
        contractDetails.setAddress(addr.getBytes());
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

        db.getDb().updateBatch(batch);

        for (RskAddress key : removes) {
            db.delete(key.getBytes());
        }

        cache.clear();
        removes.clear();

        return totalSize;
    }


    public synchronized Set<RskAddress> keys() {
        Set<RskAddress> keys = new HashSet<>();
        keys.addAll(cache.keySet());
        keys.addAll(db.dumpKeys(RskAddress::new));

        return keys;
    }

}
