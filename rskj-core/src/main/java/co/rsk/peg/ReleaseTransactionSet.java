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
import org.ethereum.core.Block;

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

    public void add(BtcTransaction transaction, Block rskBlock) {
        entries.add(new Entry(transaction, rskBlock.getNumber()));
    }

    public Set<BtcTransaction> sliceWithConfirmations(Block rskBlock, Long minimumConfirmations) {
        Set<BtcTransaction> output = entries.stream()
            .filter(e -> hasEnoughConfirmations(e, rskBlock, minimumConfirmations))
            .map(e -> e.getTransaction())
            .collect(Collectors.toSet());

        entries.removeIf(e -> hasEnoughConfirmations(e, rskBlock, minimumConfirmations));

        return output;
    }

    private boolean hasEnoughConfirmations(Entry entry, Block rskBlock, Long minimumConfirmations) {
        return (rskBlock.getNumber() - entry.getRskBlockNumber()) >= minimumConfirmations;
    }
}
