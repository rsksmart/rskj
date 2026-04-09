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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class PegoutsWaitingForConfirmations {

    private static final Logger logger = LoggerFactory.getLogger(PegoutsWaitingForConfirmations.class);

    /**
     * Wraps some Set functionality with preserving legacy entries order.
     */
    static class Entries {

        /**
         * From JDK.
         */
        static final int MAXIMUM_CAPACITY = 1 << 30;

        /**
         * From JDK.
         */
        static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private Set<Entry> entries = new HashSet<>();

        /**
         * Buckets size in HashSet used for legacy sorting.
         */
        private int legacyBuckets;

        private Collection<LegacyEntry> legacyEntries = new ArrayList<>();

        private Map<Integer, Integer> legacyIndexes = new HashMap<>();

        /**
         * From JDK.
         */
        static final int tableSizeFor(int cap) {
            int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
            return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
        }

        /**
         * Reproducing steps for calculating initial capacity from JDK.
         */
        static final int calculateBuckets(int inputSize) {
            // Core variables from HashMap constructor
            var initialCapacity = Math.max((int) (inputSize/.75f) + 1, 16);
            var threshold = tableSizeFor(initialCapacity);

            // From HashMap resize() method
            // Initially oldTab will be empty so oldCap = 0
            // if oldCap == 0 and oldThr > -> newCap will be equal to threshold
            //
            // TODO: Will need a threshold logic only if there willI be
            // bucket collisions (input order affects current sorting)
            // var newCap = threshold;
            // float ft = (float)newCap * threshold;
            // var newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
            //           (int)ft : Integer.MAX_VALUE);

            return threshold;
        }

        public Entries(Collection<Entry> entries) {
            if (entries.size() > 0) {
                logger.warn("PEGOUTS storing {} entries", entries.size());
            }

            this.legacyBuckets = calculateBuckets(entries.size());

            // Security issue for legacy ordering
            // The actual order depends on input entries order :(
            // But the issues could only appear if there will be hash collision in the bucket
            // It will be ok as long as one entry sits in one bucket.
            for (Entry entry : entries) {
                this.addEntry(entry);
            }

            // possible ordering determinizm fix
            // entries
            //     .stream()
            //     .sorted(Comparator.comparing(Entry::hashCode))
            //     .forEachOrdered((e) ->{
            //         this.addEntry(e);
            //     });
        }

        public Collection<Entry> getEntries() {
            return Collections.unmodifiableCollection(entries);
        }

        public Collection<Entry> getEntriesOrdered() {
            return this.legacyEntries.stream()
                .sorted(Comparator.comparing(LegacyEntry::bucketId).thenComparing(LegacyEntry::orderId))
                .map(LegacyEntry::entry)
                .collect(Collectors.toUnmodifiableList());
        }

        boolean remove(Entry e) {
            var removed = this.entries.remove(e);
            if (removed) {
                // Meant for tests so no need to do it cool
                this.legacyEntries.removeIf(item -> item.entry == e);
            }
            return removed;
        }

        /**
         * The only valid way to add Entry into this structure.
         */
        public final void addEntry(Entry e) {
            if (this.entries.contains(e)) {
                // Entry exists nothing to do
                return;
            }

            this.entries.add(e);

            var hash = hash(e);

            var bucketId = Integer.valueOf((this.legacyBuckets - 1) & hash);

            // Mocking hash collision ordering
            int orderId = 0;
            if (this.legacyIndexes.containsKey(bucketId)) {
                // I hope we will not have such events untill hardfork
                logger.warn("PEGOUTS hash collision found for RSK block {} TxHash {}", e.getPegoutCreationRskBlockNumber(), e.getPegoutCreationRskTxHash() );
                orderId = this.legacyIndexes.get(bucketId) + 1;
            }
            this.legacyIndexes.put(bucketId, orderId);


            var legacyEntry = new LegacyEntry(e, bucketId, orderId);

            this.legacyEntries.add(legacyEntry);
        }

        private static final int hash(Object key) {
            int h;
            return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
        }
    }

    /**
     * Wraps entry with additional legacy sorting ordering information.
     */
    static record LegacyEntry(Entry entry, int bucketId, int orderId) {};

    public static class Entry {
        // Compares entries using the lexicographical order of the btc tx's serialized bytes
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<Entry>() {
            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                return comparator.compare(e1.getBtcTransaction().bitcoinSerialize(), e2.getBtcTransaction().bitcoinSerialize());
            }
        };

        private BtcTransaction btcTransaction;

        private Long pegoutCreationRskBlockNumber;

        private Keccak256 pegoutCreationRskTxHash;

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber, Keccak256 pegoutCreationRskTxHash) {
            this.btcTransaction = btcTransaction;
            this.pegoutCreationRskBlockNumber = pegoutCreationRskBlockNumber;
            this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        }

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber) { this(btcTransaction, pegoutCreationRskBlockNumber, null); }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public Long getPegoutCreationRskBlockNumber() {
            return pegoutCreationRskBlockNumber;
        }

        public Keccak256 getPegoutCreationRskTxHash() { return pegoutCreationRskTxHash; }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            Entry otherEntry = (Entry) o;
            return otherEntry.getBtcTransaction().equals(getBtcTransaction()) &&
                otherEntry.getPegoutCreationRskBlockNumber().equals(getPegoutCreationRskBlockNumber()) &&
                (otherEntry.getPegoutCreationRskTxHash() == null && getPegoutCreationRskTxHash() == null ||
                 otherEntry.getPegoutCreationRskTxHash() != null && otherEntry.getPegoutCreationRskTxHash().equals(getPegoutCreationRskTxHash()));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(getBtcTransaction(), getPegoutCreationRskBlockNumber());
        }
    }

    private Entries entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new Entries(entries);
    }

    public Set<Entry> getEntriesWithoutHash() {
        return this.entries.getEntries().stream().filter(e -> e.getPegoutCreationRskTxHash() == null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntriesWithHash() {
        return this.entries.getEntries().stream().filter(e -> e.getPegoutCreationRskTxHash() != null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntries() {
        return new HashSet<>(this.entries.getEntries());
    }

    public void add(BtcTransaction transaction, Long blockNumber) {
        add(transaction, blockNumber, null);
    }

    /**
     * Assuming that this method should be used in tests only.
     */
    public void add(BtcTransaction transaction, Long blockNumber, Keccak256 rskTxHash) {
        var items = this.entries.getEntries();
        if (items.stream().noneMatch(e -> e.getBtcTransaction().equals(transaction))) {
            this.entries.addEntry(new Entry(transaction, blockNumber, rskTxHash));
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
        return this.entries.getEntriesOrdered().stream().filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations)).findFirst();
    }

    /**
     * WARNING! Units tests only relted method.
     */
    public boolean removeEntry(Entry entry){
        return entries.remove(entry);
    }

    private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
        return (currentBlockNumber - entry.getPegoutCreationRskBlockNumber()) >= minimumConfirmations;
    }
}
