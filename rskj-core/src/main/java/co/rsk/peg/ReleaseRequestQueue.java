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
    private int entriesHashStartsAtIndex;
    private static final int NO_HASH_ENTRIES = -1;

    public ReleaseRequestQueue(List<Entry> entries) {
        this(entries, NO_HASH_ENTRIES);
    }

    public ReleaseRequestQueue(List<Entry> entries, int entriesHashStartsAtIndex) {
        this.entries = new ArrayList<>(entries);
        this.entriesHashStartsAtIndex = entriesHashStartsAtIndex;
    }

    public List<Entry> getEntriesWithoutHash() {
        if (entriesHashStartsAtIndex == NO_HASH_ENTRIES) {
            return getEntries();
        }
        return new ArrayList<>(entries.subList(0, entriesHashStartsAtIndex));
    }

    public List<Entry> getEntriesWithHash() {
        if (entriesHashStartsAtIndex == NO_HASH_ENTRIES) {
            return new ArrayList<>();
        }
        return new ArrayList<>(entries.subList(entriesHashStartsAtIndex, entries.size()));
    }

    public List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    public void add(Address destination, Coin amount, Keccak256 rskTxHash) {
        entries.add(new Entry(destination, amount, rskTxHash));

        if (entriesHashStartsAtIndex == NO_HASH_ENTRIES){
            entriesHashStartsAtIndex = entries.size() - 1;
        }
    }

    public void add(Address destination, Coin amount) {
        if(entriesHashStartsAtIndex != NO_HASH_ENTRIES){
            throw new IllegalArgumentException("No entries without hash are accepted once an entry with hash has already been added");
        }

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
}
