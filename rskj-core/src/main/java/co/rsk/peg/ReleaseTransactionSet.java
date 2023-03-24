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
import co.rsk.crypto.Keccak256;
import com.google.common.primitives.UnsignedBytes;

import java.util.*;
import java.util.stream.Collectors;

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
        private Keccak256 rskTxHash;

        public Entry(BtcTransaction transaction, Long rskBlockNumber, Keccak256 rskTxHash) {
            this.transaction = transaction;
            this.rskBlockNumber = rskBlockNumber;
            this.rskTxHash = rskTxHash;
        }

        public Entry(BtcTransaction transaction, Long rskBlockNumber) { this(transaction, rskBlockNumber, null); }

        public BtcTransaction getTransaction() {
            return transaction;
        }

        public Long getRskBlockNumber() {
            return rskBlockNumber;
        }

        public Keccak256 getRskTxHash() { return rskTxHash; }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;
            return otherEntry.getTransaction().equals(getTransaction()) &&
                    otherEntry.getRskBlockNumber().equals(getRskBlockNumber()) &&
                            (otherEntry.getRskTxHash() == null && getRskTxHash() == null ||
                                    otherEntry.getRskTxHash() != null && otherEntry.getRskTxHash().equals(getRskTxHash()));
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

    public Set<Entry> getEntriesWithoutHash() {
        return entries.stream().filter(e -> e.getRskTxHash() == null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntriesWithHash() {
        return entries.stream().filter(e -> e.getRskTxHash() != null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntries() {
        return new HashSet<>(entries);
    }

    public void add(BtcTransaction transaction, Long blockNumber) {
        add(transaction, blockNumber, null);
    }

    public void add(BtcTransaction transaction, Long blockNumber, Keccak256 rskTxHash) {
        // Disallow duplicate transactions
        if (entries.stream().noneMatch(e -> e.getTransaction().equals(transaction))) {
            entries.add(new Entry(transaction, blockNumber, rskTxHash));
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
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(Long currentBlockNumber, Integer minimumConfirmations) {
        return entries.stream().filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations)).findFirst();
    }

    public boolean removeEntry(Entry entry){
        return entries.remove(entry);
    }

    private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
        return (currentBlockNumber - entry.getRskBlockNumber()) >= minimumConfirmations;
    }
}
