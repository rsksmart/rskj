/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.crypto.Keccak256;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Representation of a queue of btc release
 * requests waiting to be processed by the bridge.
 *
 * @author Ariel Mendelzon
 */
public class ReleaseRequestQueue {
    public static class Entry {
        private Address destination;
        private Coin amount;
        private Keccak256 rskTxHash;

        public Entry(Address destination, Coin amount, Keccak256 rskTxHash) {
            this.destination = destination;
            this.amount = amount;
            this.rskTxHash = rskTxHash;
        }

        public Entry(Address destination, Coin amount) {
            this(destination, amount, null);
        }

        public Address getDestination() {
            return destination;
        }

        public Coin getAmount() {
            return amount;
        }

        public Keccak256 getRskTxHash() {
            return rskTxHash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;

            return otherEntry.getDestination().equals(getDestination()) &&
                    otherEntry.getAmount().equals(getAmount()) &&
                    (otherEntry.getRskTxHash() == null && getRskTxHash() == null ||
                            otherEntry.getRskTxHash() != null && otherEntry.getRskTxHash().equals(getRskTxHash()));

        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getDestination(), this.getAmount());
        }
    }

    public interface Processor {
        boolean process(Entry entry);
    }

    private List<Entry> entries;

    public ReleaseRequestQueue(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public List<Entry> getEntriesWithoutHash() {
        return entries.stream().filter((entry) -> entry.getRskTxHash() == null).collect(Collectors.toList());
    }

    public List<Entry> getEntriesWithHash() {
        return entries.stream().filter((entry) -> entry.getRskTxHash() != null).collect(Collectors.toList());
    }

    public List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    public void removeEntries(List<Entry> entriesToRemove) {
        entries.removeAll(entriesToRemove);
    }

    public void add(Address destination, Coin amount, Keccak256 rskTxHash) {
        entries.add(new Entry(destination, amount, rskTxHash));
    }

    public void add(Address destination, Coin amount) {
        entries.add(new Entry(destination, amount, null));
    }

    /**
     * This methods iterates the requests in the queue
     * and calls the processor for each. If the
     * processor returns true, then the item is removed
     * (i.e., processing was successful). Otherwise it is
     * sent to the back of the queue for future processing.
     */
    public void process(int maxIterations, Processor processor) {
        ListIterator<Entry> iterator = entries.listIterator();
        List<Entry> toRetry = new ArrayList<>();
        int i = 0;
        while (iterator.hasNext() && i < maxIterations) {
            Entry entry = iterator.next();
            iterator.remove();
            ++i;

            if (!processor.process(entry)) {
                toRetry.add(entry);
            }
        }

        entries.addAll(toRetry);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReleaseRequestQueue)) return false;

        ReleaseRequestQueue that = (ReleaseRequestQueue) o;

        return entries != null ? entries.equals(that.entries) : that.entries == null;
    }

    @Override
    public int hashCode() {
        return entries != null ? entries.hashCode() : 0;
    }
}
