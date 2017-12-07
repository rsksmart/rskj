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
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import com.google.common.primitives.UnsignedBytes;
import org.ethereum.core.Block;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Representation of a queue of btc release
 * requests waiting to be processed by the bridge.
 *
 * @author Ariel Mendelzon
 */
public class ReleaseRequestQueue {
    public static class Entry {
        // Compares entries using destination address and then amount order
        // (address compared on the lexicographical order of its bytes, amount ascending)
        public static final Comparator<Entry> DESTINATION_AMOUNT_COMPARATOR = new Comparator<Entry>() {
            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                int addressComparison = comparator.compare(e1.getDestination().getHash160(), e2.getDestination().getHash160());

                if (addressComparison != 0) {
                    return addressComparison;
                }

                return e1.getAmount().compareTo(e2.getAmount());
            }
        };

        private Address destination;
        private Coin amount;

        public Entry(Address destination, Coin amount) {
            this.destination = destination;
            this.amount = amount;
        }

        public Address getDestination() {
            return destination;
        }

        public Coin getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;

            return otherEntry.getDestination().equals(getDestination()) &&
                    otherEntry.getAmount().equals(getAmount());
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

    public List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    public void add(Address destination, Coin amount) {
        entries.add(new Entry(destination, amount));
    }

    /**
     * This methods iterates the requests in the queue
     * and calls the processor for each. If the
     * processor returns true, then the item is removed
     * (i.e., processing was successful). Otherwise
     * it is kept for future processing.
     */
    public void process(Processor processor) {
        ListIterator<Entry> iterator = entries.listIterator();
        while (iterator.hasNext()) {
            boolean result = processor.process(iterator.next());
            if (result) {
                iterator.remove();
            }
        }
    }
}
