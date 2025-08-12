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
import org.ethereum.util.ByteUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class PegoutsWaitingForConfirmations {
    public static class Entry {
        // Compares entries using the lexicographical order of the btc tx's serialized bytes
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<Entry>() {
            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                System.out.println("BTC_TX_COMPARATOR---------------------------------------------");
                System.out.println("BTC_TX_COMPARATOR---------------------------------------------");
                System.out.println("ByteUtil.toHexString(e1.getPegoutCreationRskTxHash().getBytes())");
                Optional.ofNullable(e1.getPegoutCreationRskTxHash()).ifPresent(h -> System.out.println(ByteUtil.toHexString(h.getBytes())));
                System.out.println("ByteUtil.toHexString(e2.getPegoutCreationRskTxHash().getBytes())");
                Optional.ofNullable(e2.getPegoutCreationRskTxHash()).ifPresent(h -> System.out.println(ByteUtil.toHexString(h.getBytes())));

                System.out.println("ByteUtil.toHexString(e1.getBtcTransaction().bitcoinSerialize().getBytes())");
                System.out.println(ByteUtil.toHexString(e1.getBtcTransaction().bitcoinSerialize()));
                System.out.println("ByteUtil.toHexString(e2.getBtcTransaction().bitcoinSerialize().getBytes())");
                System.out.println(ByteUtil.toHexString(e2.getBtcTransaction().bitcoinSerialize()));

                System.out.println("e1.getPegoutCreationRskBlockNumber()");
                System.out.println(e1.getPegoutCreationRskBlockNumber());
                System.out.println("e2.getPegoutCreationRskBlockNumber()");
                System.out.println(e2.getPegoutCreationRskBlockNumber());

                var compareResult = comparator.compare(e1.getBtcTransaction().bitcoinSerialize(), e2.getBtcTransaction().bitcoinSerialize());

                System.out.println("compareResult");
                System.out.println(compareResult);

                return compareResult;
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
            return Objects.hash(getBtcTransaction(), getPegoutCreationRskBlockNumber());
        }
    }

    private Set<Entry> entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new HashSet<>(entries);
    }

    public Set<Entry> getEntriesWithoutHash() {
        return entries.stream().filter(e -> e.getPegoutCreationRskTxHash() == null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntriesWithHash() {
        return entries.stream().filter(e -> e.getPegoutCreationRskTxHash() != null).collect(Collectors.toSet());
    }

    public Set<Entry> getEntries() {
        return new HashSet<>(entries);
    }

    public void add(BtcTransaction transaction, Long blockNumber) {
        add(transaction, blockNumber, null);
    }

    public void add(BtcTransaction transaction, Long blockNumber, Keccak256 rskTxHash) {
        if (entries.stream().noneMatch(e -> e.getBtcTransaction().equals(transaction))) {
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
        System.out.println("getNextPegoutWithEnoughConfirmations------------------------------");
        System.out.println("getNextPegoutWithEnoughConfirmations------------------------------");

        System.out.println("currentBlockNumber");
        System.out.println(currentBlockNumber);
        System.out.println("minimumConfirmations");
        System.out.println(minimumConfirmations);

        System.out.println("getNextPegoutWithEnoughConfirmations-entries------------------------------");

        entries.stream().forEach(e -> {
            System.out.println("ByteUtil.toHexString(e.getPegoutCreationRskTxHash().getBytes())");
            Optional.ofNullable(e.getPegoutCreationRskTxHash()).ifPresent(h -> System.out.println(ByteUtil.toHexString(h.getBytes())));
        });

        System.out.println("getNextPegoutWithEnoughConfirmations-entries-filter------------------------------");
        return entries.stream().filter(entry -> {
            System.out.println("getNextPegoutWithEnoughConfirmations-entries-filter-entry------------------------------");

            System.out.println("ByteUtil.toHexString(entry.getPegoutCreationRskTxHash().getBytes())");
            Optional.ofNullable(entry.getPegoutCreationRskTxHash()).ifPresent(h -> System.out.println(ByteUtil.toHexString(h.getBytes())));

            System.out.println("currentBlockNumber");
            System.out.println(currentBlockNumber);
            System.out.println("minimumConfirmations");
            System.out.println(minimumConfirmations);

            var hasEnoughConfirmationsResult = hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations);

            System.out.println("hasEnoughConfirmationsResult");
            System.out.println(hasEnoughConfirmationsResult);

            return hasEnoughConfirmationsResult;
        }).findFirst();
    }

    public boolean removeEntry(Entry entry){
        return entries.remove(entry);
    }

    private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
        return (currentBlockNumber - entry.getPegoutCreationRskBlockNumber()) >= minimumConfirmations;
    }
}
