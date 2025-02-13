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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class FilterManager {

    private static final Logger logger = LoggerFactory.getLogger(FilterManager.class);

    private static final long FILTER_TIMEOUT = Duration.ofMinutes(5).toMillis(); // 5 minutes in milliseconds
    private static final long FILTER_CLEANUP_PERIOD = Duration.ofMinutes(1).toMillis(); // 1 minute in milliseconds

    private final Object filterLock = new Object();
    private final AtomicInteger filterCounter = new AtomicInteger(1);

    @GuardedBy("filterLock")
    private final Map<Integer, Filter> installedFilters = new HashMap<>();

    private long latestFilterCleanup = System.currentTimeMillis();

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

            logger.debug("[{}] installed with id: [{}]", filter.getClass().getSimpleName(), id);

            return id;
        }
    }

    public boolean removeFilter(int id) {
        synchronized (filterLock) {
            Filter filter = installedFilters.remove(id);
            boolean removed = filter != null;
            if (removed) {
                logger.debug("[{}] with id: [{}] uninstalled", filter.getClass().getSimpleName(), id);
            } else {
                logger.debug("Cannot uninstalled filter with id: [{}] - not found", id);
            }

            return removed;
        }
    }

    public Object[] getFilterEvents(int id, boolean newEvents) {
        synchronized (filterLock) {
            filtersCleanup();

            Filter filter = installedFilters.get(id);

            if (filter == null) {
                throw filterNotFound("filter not found");
            }

            if (newEvents) {
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

        if (latestFilterCleanup + FILTER_CLEANUP_PERIOD > now) {
            return;
        }

        List<Integer> toremove = new ArrayList<>();

        for (Map.Entry<Integer, Filter> entry : installedFilters.entrySet()) {
            Filter f = entry.getValue();

            if (f.hasExpired(FILTER_TIMEOUT)) {
                toremove.add(entry.getKey());
                logger.debug("[{}] with id: [{}] expired", f.getClass().getSimpleName(), entry.getKey());
            }
        }

        for (Integer id : toremove) {
            installedFilters.remove(id);
        }

        latestFilterCleanup = now;
    }
}
