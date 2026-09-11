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
import co.rsk.peg.constants.PegoutsHistory;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.primitives.UnsignedBytes;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.jspecify.annotations.Nullable;
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
     * Return entries ordered according to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithoutHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() == null)
                .sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    /**
     * Return entries ordered according to {@link Entry.BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithHashOrdered() {
        return entries.entriesSet.stream().filter(e -> e.getPegoutCreationRskTxHash() != null)
                .sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    public Collection<Entry> getEntries(ForBlock activations) {
        // TODO: after fork, leave only sorted output no need for rskip559 switch
        // And rename it to getEntriesOrdered

        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        if (rskip559) {
            return entries.entriesSet.stream().sorted(Entry.BTC_TX_COMPARATOR).toList();
        }
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
     * @param currentBlockNumber   the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param activations          activations for a current block that determine entries ordering/filtering.
     *
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(
        Long currentBlockNumber,
        Integer minimumConfirmations,
        ForBlock activations
    ) {
        var pegoutOverwrite = this.getOutputOverwrite(currentBlockNumber, activations);

        var rskip559 = activations.isActive(ConsensusRule.RSKIP559);
        var pegout = this.entries.getNextPegoutWithEnoughConfirmations(currentBlockNumber, minimumConfirmations, rskip559);

        // TODO: must be moved to the top of function after hardfork
        // If diff overwrite exists - just return it
        if (pegoutOverwrite != null) {
            logger.info(
                    "PreRSKIP559 pegout applied! Current block:{} Pegout ref:{}@{}",
                    currentBlockNumber,
                    pegoutOverwrite.getBtcTransaction().getHash(),
                    pegoutOverwrite.getPegoutCreationRskBlockNumber());

            // TODO: no need for this log message after a hardfork
            // it is used only to validate how node syncing with hardcoded outputs
            if (pegout.isPresent()) {
                logger.info(
                        "Replaced pegout is: current block:{} Pegout ref:{}@{}",
                        currentBlockNumber,
                        pegout.get().getBtcTransaction().getHash(),
                        pegout.get().getPegoutCreationRskBlockNumber());
            }
            return Optional.of(pegoutOverwrite);
        }

        return pegout;
    }

    @Nullable
    private Entry getOutputOverwrite(Long blk, ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP559)) {
            return null;
        }

        var hardcoded = PegoutsHistory.getHardcodedPegouts(activations.getNetworkName());
        if (hardcoded == null) {
            // Only main and testnet (v3) has hardcoded outputs other don't
            return null;
        }

        var records = hardcoded.get(blk);
        if (records == null) {
            return null;
        }

        /*
         * When we have multiple TX that require next pegout in the block
         * then this code will be called multiple time during block processing.
         * Next pegout is consumed by TX (it will be removed from collection).
         * Thus we have ordered sequence of hardcoded `records`
         * that matches how historically pegouts were retrieved during processing some block.
         */
        for (var ref : records) {
            var pegout = this.getPegoutByRef(ref);
            if (pegout.isPresent()) {
                return pegout.get();
            } else {
                // Expected behaviour if called second time for the same block
                logger.debug("Skip hardcoded reference {}", ref);
            }
        }

        return null;
    }

    /**
     * Search entries by pegout reference.
     */
    Optional<Entry> getPegoutByRef(PegoutsHistory.Ref pegoutRef) {
        var btcTxHash = pegoutRef.btcTxHash();
        var rskBlock = Long.valueOf(pegoutRef.rskBlock());
        return this.entries.entriesSet
            .stream()
            .filter(e -> btcTxHash.equals(e.getBtcTransaction().getHash())
                    && rskBlock.equals(e.getPegoutCreationRskBlockNumber())
            ).findFirst();
    }

    public void add(Entry entry) {
        this.entries.addEntry(entry);
    }

    public boolean removeEntry(Entry entry) {
        return entries.removeEntry(entry);
    }

    /**
     * Encapsulate entries while preserving sorting order before fork.
     *
     * @deprecated should be removed after RSKIP559 activation.
     * Historical outputs will be hardcoded thus no need for this Java 21+ tricks at all.
     */
    @Deprecated
    public static class EntriesStore {

        // From java SDK
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final HashSet<Entry> entriesSet;

        /**
         * Must be equal to new HashSet() call in Java 17.
         * Use it to preserve old behaviour in Java21+.
         */
        public static HashSet<Entry> setOfEntries() {
            return new HashSet<>(16, DEFAULT_LOAD_FACTOR);
        }

        /**
         * This is a standard code for `new HashSet<>(entries);` in Java 17.
         * Coefficients were changed in Java 21.
         * Use it to preserve old behaviour in Java21+.
         */
        public static HashSet<Entry> setOfEntries(Collection<Entry> entries) {
            // Need to hardcode Java 17 init params here to preserve old behaviour in Java 21+
            var ehs = new HashSet<Entry>(Math.max((int) (entries.size() / DEFAULT_LOAD_FACTOR) + 1, 16));
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
         * @param isRskip559 turns on deterministic order for entries before filtering.
         */
        public Optional<Entry> getNextPegoutWithEnoughConfirmations(
                Long currentBlockNumber,
                Integer minimumConfirmations,
                boolean isRskip559 // TODO remove after RSKIP559 activation
        ) {
            var entries = entriesSet.stream()
                    .filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations));
            // TODO: In next release after RSKIP559 activation we must use only properly sorted entries
            // all historical difference will be hardcoded
            if (isRskip559) {
                entries = entries.sorted(Entry.BTC_TX_COMPARATOR);
            }
            return entries.findFirst();
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
                return comparator.compare(
                    e1.getBtcTransaction().bitcoinSerialize(),
                    e2.getBtcTransaction().bitcoinSerialize()
                );
            }
        };

        public Entry(BtcTransaction btcTransaction, Long pegoutCreationRskBlockNumber,
                Keccak256 pegoutCreationRskTxHash) {
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
            return otherEntry.getBtcTransaction().equals(getBtcTransaction())
                && otherEntry.getPegoutCreationRskBlockNumber().equals(getPegoutCreationRskBlockNumber())
                && Objects.equals(getPegoutCreationRskTxHash(), otherEntry.getPegoutCreationRskTxHash());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getBtcTransaction(), getPegoutCreationRskBlockNumber());
        }
    }
}
