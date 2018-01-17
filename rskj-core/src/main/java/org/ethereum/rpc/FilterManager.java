/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class FilterManager {
    private static final long filterTimeout = 5 * 60 * 1000; // 5 minutes in milliseconds
    private static final long filterCleanupPeriod = 1 * 60 * 1000; // 1 minute in milliseconds

    private long latestFilterCleanup = System.currentTimeMillis();

    private final Object filterLock = new Object();

    AtomicInteger filterCounter = new AtomicInteger(1);
    Map<Integer, Filter> installedFilters = new Hashtable<>();

    public int registerFilter(Filter filter) {
        synchronized (filterLock) {
            filtersCleanup();

            int id = filterCounter.getAndIncrement();
            installedFilters.put(id, filter);
            return id;
        }
    }

    public boolean removeFilter(int id) {
        synchronized (filterLock) {
            return installedFilters.remove(id) != null;
        }
    }

    public Object[] getFilterEvents(int id, boolean newevents) {
        synchronized (filterLock) {
            filtersCleanup();

            Filter filter = installedFilters.get(id);

            if (filter == null) {
                return null;
            }

            if (newevents) {
                return filter.getNewEvents();
            }
            else {
                return filter.getEvents();
            }
        }
    }

    public void newBlockReceived(Block block) {
        synchronized (filterLock) {
            filtersCleanup();

            for (Filter filter : installedFilters.values()) {
                filter.newBlockReceived(block);
            }
        }
    }

    public void newPendingTx(List<Transaction> transactions) {
        synchronized (filterLock) {
            filtersCleanup();

            for (Filter filter : installedFilters.values()) {
                for (Transaction tx : transactions) {
                    filter.newPendingTx(tx);
                }
            }
        }
    }

    private void filtersCleanup() {
        long now = System.currentTimeMillis();

        if (latestFilterCleanup + filterCleanupPeriod > now)
            return;

        List<Integer> toremove = new ArrayList<>();

        for (Integer id : installedFilters.keySet()) {
            Filter f = installedFilters.get(id);

            if (f.hasExpired(filterTimeout))
                toremove.add(id);
        }

        for (Integer id : toremove)
            installedFilters.remove(id);

        latestFilterCleanup = now;
    }
}
