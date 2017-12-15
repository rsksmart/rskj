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

import co.rsk.bitcoinj.core.BtcTransaction;
import com.google.common.primitives.UnsignedBytes;

import java.util.*;

/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class ReleaseTransactionSet {
    public static class Entry {
        // Compares entries using the lexicographical order of the btc tx's serialized bytes
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<Entry>() {
            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                return comparator.compare(e1.getTransaction().bitcoinSerialize(), e2.getTransaction().bitcoinSerialize());
            }
        };

        private BtcTransaction transaction;
        private Long rskBlockNumber;

        public Entry(BtcTransaction transaction, Long rskBlockNumber) {
            this.transaction = transaction;
            this.rskBlockNumber = rskBlockNumber;
        }

        public BtcTransaction getTransaction() {
            return transaction;
        }

        public Long getRskBlockNumber() {
            return rskBlockNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;
            return otherEntry.getTransaction().equals(getTransaction()) &&
                    otherEntry.getRskBlockNumber().equals(getRskBlockNumber());
         }

        @Override
        public int hashCode() {
            return Objects.hash(getTransaction(), getRskBlockNumber());
        }
    }

    private Set<Entry> entries;

    public ReleaseTransactionSet(Set<Entry> entries) {
        this.entries = new HashSet<>(entries);
    }

    public Set<Entry> getEntries() {
        return new HashSet<>(entries);
    }

    public void add(BtcTransaction transaction, Long blockNumber) {
        // Disallow duplicate transactions
        if (!entries.stream().anyMatch(e -> e.getTransaction().equals(transaction))) {
            entries.add(new Entry(transaction, blockNumber));
        }
    }

    /**
     * Given a block number and a minimum number of confirmations,
     * returns a subset of transactions within the set that have
     * at least that number of confirmations.
     * Optionally supply a maximum slice size to limit the output
     * size.
     * Sliced items are also removed from the set (thus the name, slice).
     * @param currentBlockNumber the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param maximumSliceSize (optional) the maximum number of elements in the slice.
     * @return the slice of btc transactions.
     */
    public Set<BtcTransaction> sliceWithConfirmations(Long currentBlockNumber, Integer minimumConfirmations, Optional<Integer> maximumSliceSize) {
        Set<BtcTransaction> output = new HashSet<>();

        int count = 0;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations) && (!maximumSliceSize.isPresent() || count < maximumSliceSize.get())) {
                output.add(entry.getTransaction());
                iterator.remove();
                count++;
                if (maximumSliceSize.isPresent() && count == maximumSliceSize.get()) {
                    break;
                }
            }
        }

        return output;
    }

    private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
        return (currentBlockNumber - entry.getRskBlockNumber()) >= minimumConfirmations;
    }
}
