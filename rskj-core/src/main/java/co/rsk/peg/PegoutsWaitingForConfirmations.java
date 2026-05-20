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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class PegoutsWaitingForConfirmations {

    private final EntriesStore entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new EntriesStore(entries);
    }

    public List<Entry> getEntriesWithoutHash() {
        return entries.entriesSet.stream().filter(e -> e.pegoutCreationRskTxHash() == null).toList();
    }

    public List<Entry> getEntriesWithHash() {
        return entries.entriesSet.stream().filter(e -> e.pegoutCreationRskTxHash() != null).toList();
    }

    public List<Entry> getEntries() {
        return entries.entriesSet.stream().toList();
    }

    /**
     * Given a block number and a minimum number of confirmations,
     * returns a subset of transactions within the set that have
     * at least that number of confirmations.
     *
     * Optionally supply a maximum slice size to limit the output size.
     * Sliced items are also removed from the set (thus the name, slice).
     *
     * @param currentBlockNumber the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param activations activations for a current block that determine entries ordering/filtering.
     *
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(Long currentBlockNumber, Integer minimumConfirmations, ForBlock activations) {
        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        return this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, rskip559);
    }

    public void add(Entry entry) {
        this.entries.addEntry(entry);
    }

    public boolean removeEntry(Entry entry){
        return entries.removeEntry(entry);
    }

    /**
     * Encapsulate entries while preserving sorting order before fork.
     */
    private static class EntriesStore {

        // From java SDK
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final HashSet<Entry> entriesSet;

        public EntriesStore(Collection<Entry> entries) {
            // This is a standard code for `new HashSet<>(entries);` in Java 17
            // Coefficients were changed in Java 21
            // Need to hardcode Java 17 init params here to preserve old behaviour in Java 21+
            this.entriesSet = new HashSet<>(Math.max((int) (entries.size()/DEFAULT_LOAD_FACTOR) + 1, 16));
            this.entriesSet.addAll(entries);
        }

        private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
            return (currentBlockNumber - entry.pegoutCreationRskBlockNumber()) >= minimumConfirmations;
        }

        /**
         * @param withTxComparator turns on deterministic order for entries before filtering.
         */
        public Optional<Entry> getNextPegoutWithEnoughConfirmations(Long currentBlockNumber, Integer minimumConfirmations, boolean withTxComparator) {
            var entries = entriesSet.stream().filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations));
            if (withTxComparator) {
                entries = entries.sorted(Entry.BTC_TX_COMPARATOR);
            }
            return entries.findFirst();
        }

        public void addEntry(Entry entry) {
            if (this.entriesSet.stream().noneMatch(e -> e.btcTransaction().equals(entry.btcTransaction()))) {
                this.entriesSet.add(entry);
            }
        }

        public boolean removeEntry(Entry entry) {
            return this.entriesSet.remove(entry);
        }
    }

    public record Entry(
        BtcTransaction btcTransaction,
        Long pegoutCreationRskBlockNumber,
        Keccak256 pegoutCreationRskTxHash
    ) {
        /**
         * Compares entries using the lexicographical order of the btc tx's serialized bytes.
         */
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<>() {
            private final Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                return comparator.compare(e1.btcTransaction().bitcoinSerialize(),
                    e2.btcTransaction().bitcoinSerialize());
            }
        };

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber) {
            this(btcTransaction, pegoutCreationRskBlockNumber, null);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;
            return otherEntry.btcTransaction().equals(btcTransaction()) &&
                otherEntry.pegoutCreationRskBlockNumber().equals(pegoutCreationRskBlockNumber()) &&
                (otherEntry.pegoutCreationRskTxHash() == null && pegoutCreationRskTxHash() == null
                    ||
                    otherEntry.pegoutCreationRskTxHash() != null
                        && otherEntry.pegoutCreationRskTxHash().equals(pegoutCreationRskTxHash()));
        }

        @Override
        public int hashCode() {
            return Objects.hash(btcTransaction(), pegoutCreationRskBlockNumber());
        }
    }
}
