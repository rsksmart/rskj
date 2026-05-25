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
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import com.google.common.primitives.UnsignedBytes;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.util.ByteUtil;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of a queue of BTC release
 * transactions waiting for confirmations
 * on the rsk network.
 *
 * @author Ariel Mendelzon
 */
public class PegoutsWaitingForConfirmations {

    private static final Logger logger = LoggerFactory.getLogger(PegoutsWaitingForConfirmations.class);

    private final EntriesStore entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new EntriesStore(entries);
    }

    /**
     * Return entries ordered accoring to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithoutHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() == null).sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    /**
     * Return entries ordered accoring to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() != null).sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    public Collection<Entry> getEntries(ForBlock activations) {
        // TODO: After fork we could try to remove this code and leave only sorted output.
        // Because only after fork it will be possible to prove that it 100% does not break behaviour.
        // And rename it to getEntriesOrdered

        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        if (rskip559) {
            return entries.entriesSet.stream().sorted(Entry.BTC_TX_COMPARATOR).toList();
        }
        return entries.entriesSet.stream().toList();
    }
 
    // public static final Set<Long> WATCH_BLOCKS = Set.of(3345557L,3381087L,3381093L,3441427L,3441438L,3655284L,5002812L,7073815L,1120318L,1865157L,2589068L,2589071L,2589077L,2589086L,2589112L,2589115L,2589121L,2589135L,2589142L,2589148L,2589153L,2589163L,2589169L,2589174L,2589179L,2589190L,2589196L,2589202L,2589209L,2589308L,2589325L,2589332L,2589338L,2589375L,2589380L,2589390L,2589501L,2589515L,2589521L,2589527L,2589645L,3106055L,5788165L,6858657L);

    /**
     * Given a block number and a minimum number of confirmations,
     * returns a subset of transactions within the set that have
     * at least that number of confirmations.
     *
     * Optionally supply a maximum slice size to limit the output size.
     * Sliced items are also removed from the set (thus the name, slice).
     * @param rskTx 
     *
     * @param currentBlockNumber the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param activations activations for a current block that determine entries ordering/filtering.
     *
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(Transaction rskTx, Long currentBlockNumber, Integer minimumConfirmations, ForBlock activations) {
        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);

        // Diff output logic
        var preActivation = this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, false);
        var postActivation = this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, true);

        var manyPegouts = this.entries.getMatchedPegoutsCount(currentBlockNumber, minimumConfirmations);
        if ( manyPegouts> 1 ) {
            logger.error(
                "PEGOUT HARDCODED block:{} minConf:{} -- REF:{}@{} -- RSK_TX:{}",
                currentBlockNumber,
                minimumConfirmations,
                preActivation.map(e -> e.getBtcTransaction().getHash().toString()).orElse("0x0"),
                preActivation.map(e -> e.getPegoutCreationRskBlockNumber()).orElse(-1L),
                Objects.toString(rskTx)
            );
            if (! preActivation.equals(postActivation) ) {
                logger.error(
                    "PEGOUTS DIFF block:{} minConf:{} -- REF:{}@{}",
                    currentBlockNumber,
                    minimumConfirmations,
                    postActivation.map(e -> e.getBtcTransaction().getHash().toString()).orElse("0x0"),
                    postActivation.map(e -> e.getPegoutCreationRskBlockNumber()).orElse(-1L),
                    Objects.toString(rskTx)
                );
            }
        }

        var pegout = this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, rskip559);

        // if (WATCH_BLOCKS.contains(currentBlockNumber)) {
        //     logger.error(
        //         "PEGOUTS WATCH block:{} minConf:{} -- REF:{}@{}",
        //         currentBlockNumber,
        //         minimumConfirmations,
        //         pegout.map(e -> e.getBtcTransaction().getHash().toString()).orElse("0x0"),
        //         pegout.map(e -> e.getPegoutCreationRskBlockNumber()).orElse(-1L)
        //     );
        // }

        return pegout;
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
    public static class EntriesStore {

        // From java SDK
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final HashSet<Entry> entriesSet;

        /**
         * Must be equal to new HashSet() call in Java 17.
         * Uset it to preserve old behaviour in Java21+.
         */
        public static HashSet<Entry> setOfEntries() {
            return new HashSet<>(0, DEFAULT_LOAD_FACTOR);
        }

        /**
         * This is a standard code for `new HashSet<>(entries);` in Java 17.
         * Coefficients were changed in Java 21.
         * Use it to prserve old behaviour in Java21+. 
         */
        public static HashSet<Entry> setOfEntries(Collection<Entry> entries) {
            // Need to hardcode Java 17 init params here to preserve old behaviour in Java 21+
            var ehs = new HashSet<Entry>(Math.max((int) (entries.size()/DEFAULT_LOAD_FACTOR) + 1, 16));
            ehs.addAll(entries);
            return ehs;
        }

        private EntriesStore(Collection<Entry> entries) {
            this.entriesSet = EntriesStore.setOfEntries(entries);
        }

        private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
            return (currentBlockNumber - entry.getPegoutCreationRskBlockNumber()) >= minimumConfirmations;
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
 
        public long getMatchedPegoutsCount(Long currentBlockNumber, Integer minimumConfirmations) {
            return entriesSet
                .stream()
                .filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations))
                .count();
        }

        public void addEntry(Entry entry) {
            if (this.entriesSet.stream().noneMatch(e -> e.getBtcTransaction().equals(entry.getBtcTransaction()))) {
                this.entriesSet.add(entry);
            }
        }

        public boolean removeEntry(Entry entry) {
            return this.entriesSet.remove(entry);
        }
    }
    public static class Entry {

        private final BtcTransaction btcTransaction;

        private final Long pegoutCreationRskBlockNumber;

        private final Keccak256 pegoutCreationRskTxHash;

        /**
         * Compares entries using the lexicographical order of the btc tx's serialized bytes.
         */
        public static final Comparator<Entry> BTC_TX_COMPARATOR = new Comparator<Entry>() {
            private Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            @Override
            public int compare(Entry e1, Entry e2) {
                return comparator.compare(e1.getBtcTransaction().bitcoinSerialize(), e2.getBtcTransaction().bitcoinSerialize());
            }
        };

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber, Keccak256 pegoutCreationRskTxHash) {
            this.btcTransaction = btcTransaction;
            this.pegoutCreationRskBlockNumber = pegoutCreationRskBlockNumber;
            this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        }

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber) {
            this(btcTransaction, pegoutCreationRskBlockNumber, null);
        }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public Long getPegoutCreationRskBlockNumber() {
            return pegoutCreationRskBlockNumber;
        }

        public Keccak256 getPegoutCreationRskTxHash() {
            return pegoutCreationRskTxHash;
        }

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
}
