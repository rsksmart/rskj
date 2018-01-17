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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 17/01/2018.
 */

public class Filter {
    abstract static class FilterEvent {
        public abstract Object getJsonEventObject();
    }

    List<FilterEvent> events = new ArrayList<>();
    int processedEvents = 0;
    long accessTime = System.currentTimeMillis();

    public boolean hasExpired(long timeout) {
        long nowTime = System.currentTimeMillis();

        return accessTime + timeout <= nowTime;
    }

    public synchronized Object[] getNewEvents() {
        this.accessTime = System.currentTimeMillis();

        Object[] ret = events.stream().skip(processedEvents).map(fe -> fe.getJsonEventObject()).collect(Collectors.toList()).toArray();

        processedEvents = events.size();

        return ret;
    }

    public synchronized Object[] getEvents() {
        this.accessTime = System.currentTimeMillis();

        return events.stream().map(fe -> fe.getJsonEventObject()).collect(Collectors.toList()).toArray();
    }

    protected synchronized void add(FilterEvent evt) {
        events.add(evt);
    }

    public void newBlockReceived(Block b) {
    }

    public void newPendingTx(Transaction tx) {
        // add TransactionReceipt for PendingTx
    }
}
