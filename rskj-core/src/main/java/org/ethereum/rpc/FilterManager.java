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

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.filterNotFound;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class FilterManager {
    private static final long filterTimeout = 5 * 60 * 1000L; // 5 minutes in milliseconds
    private static final long filterCleanupPeriod = 1 * 60 * 1000L; // 1 minute in milliseconds

    private long latestFilterCleanup = System.currentTimeMillis();

    private final Object filterLock = new Object();

    private AtomicInteger filterCounter = new AtomicInteger(1);

    @GuardedBy("filterLock")
    private Map<Integer, Filter> installedFilters = new HashMap<>();

    public FilterManager(Ethereum eth) {
        eth.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                newBlockReceived(block);
            }

            @Override
            public void onPendingTransactionsReceived(List<Transaction> transactions) {
                newPendingTx(transactions);
            }
        });
    }

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
                throw filterNotFound("filter not found");
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

        if (latestFilterCleanup + filterCleanupPeriod > now) {
            return;
        }

        List<Integer> toremove = new ArrayList<>();

        for (Map.Entry<Integer, Filter> entry : installedFilters.entrySet()) {
            Filter f = entry.getValue();

            if (f.hasExpired(filterTimeout)) {
                toremove.add(entry.getKey());
            }
        }

        for (Integer id : toremove) {
            installedFilters.remove(id);
        }

        latestFilterCleanup = now;
    }
}
