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
