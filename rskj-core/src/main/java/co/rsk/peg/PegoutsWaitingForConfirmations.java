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
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.pegout.HistoricalPegoutSelectionsConstants;
import com.google.common.primitives.UnsignedBytes;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    private final Set<Entry> entries;

    public PegoutsWaitingForConfirmations(Set<Entry> entries) {
        this.entries = new HashSet<>(entries);
    }

    /**
     * Return entries ordered according to {@link Entry#BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithoutHashOrdered() {
        return entries.stream().filter(e -> e.getPegoutCreationRskTxHash() == null).sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    /**
     * Return entries ordered according to {@link Entry#BTC_TX_COMPARATOR}.
     */
    public Collection<Entry> getEntriesWithHashOrdered() {
        return entries.stream().filter(e -> e.getPegoutCreationRskTxHash() != null).sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    public Collection<Entry> getEntries(ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP559)) {
            return entries.stream().toList();
        }

        return entries.stream().sorted(Entry.BTC_TX_COMPARATOR).toList();
    }

    /**
     * Returns the next entry with at least {@code minimumConfirmations} confirmations, or an empty
     * optional if no entry qualifies.
     *
     * <p>From RSKIP559 on, the pick among several eligible entries is made by sorting them with
     * {@link Entry#BTC_TX_COMPARATOR}. Before RSKIP559 it reproduces the selection historically recorded
     * for this {@code updateCollections}.</p>
     *
     * @param currentRskTxHash the confirming {@code updateCollections} rsk tx hash. Must not be null: it keys the
     *                         historic selection, and without it the pre-RSKIP559 pick would be JVM dependent
     *                         again, which is the bug the dataset exists to avoid.
     * @param currentBlockNumber the current execution block number (height).
     * @param bridgeConstants the network's Bridge constants, which expose the network's historic pegout selections and minimum required confirmations.
     * @param activations activations for a current block that determine entries ordering/filtering. Must not be null.
     *
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(
        Keccak256 currentRskTxHash,
        long currentBlockNumber,
        BridgeConstants bridgeConstants,
        ForBlock activations
    ) {
        Objects.requireNonNull(currentRskTxHash, "currentRskTxHash must not be null");
        Objects.requireNonNull(bridgeConstants, "bridgeConstants must not be null");
        Objects.requireNonNull(activations, "activations must not be null");

        int minimumConfirmations = bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations();
        if (!activations.isActive(ConsensusRule.RSKIP559)) {
            return getNextPegoutFromHistoricalSelection(
                currentRskTxHash,
                currentBlockNumber,
                minimumConfirmations,
                bridgeConstants.getHistoricalPegoutSelectionsConstants()
            );
        }

        return getNextPegoutSortedByBtcTx(currentBlockNumber, minimumConfirmations);
    }

    public void add(Entry entry) {
        if (entries.stream().noneMatch(e -> e.getBtcTransaction().equals(entry.getBtcTransaction()))) {
            entries.add(entry);
        }
    }

    public boolean removeEntry(Entry entry){
        return entries.remove(entry);
    }

    /**
     * From RSKIP559 on, selection is deterministic via the btc tx comparator.
     */
    private Optional<Entry> getNextPegoutSortedByBtcTx(long currentBlockNumber, int minimumConfirmations) {
        return eligibleEntries(currentBlockNumber, minimumConfirmations).stream()
            .min(Entry.BTC_TX_COMPARATOR);
    }

    /**
     * Before RSKIP559 the legacy code picked with {@code findFirst()} over a {@link HashSet}, whose
     * iteration order changed between Java 17 and Java 21. Reproduce the selection historically recorded
     * for this {@code updateCollections} so that every JVM agrees.
     *
     * @param currentRskTxHash the confirming updateCollections rsk tx hash, keying the historic selection.
     * @param currentBlockNumber the current execution block number (height).
     * @param minimumConfirmations the network's minimum required confirmations
     * @param historicalSelections the network's historic pegout selections
     */
    private Optional<Entry> getNextPegoutFromHistoricalSelection(
        Keccak256 currentRskTxHash,
        long currentBlockNumber,
        int minimumConfirmations,
        HistoricalPegoutSelectionsConstants historicalSelections
    ) {
        List<Entry> eligibleEntries = eligibleEntries(currentBlockNumber, minimumConfirmations);

        // A call the dataset does not record uses the legacy findFirst() pick — its selection was already
        // deterministic, or this network has no pre-RSKIP559 chain to reproduce.
        return getEntryFromHistoricalPegoutsData(eligibleEntries, currentRskTxHash, historicalSelections)
                .or(() -> eligibleEntries.stream().findFirst());
    }

    private List<Entry> eligibleEntries(long currentBlockNumber, int minimumConfirmations) {
        return entries.stream()
            .filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations))
            .toList();
    }

    private static boolean hasEnoughConfirmations(Entry entry, long currentBlockNumber, int minimumConfirmations) {
        long pegoutConfirmations = currentBlockNumber - entry.getPegoutCreationRskBlockNumber();
        return pegoutConfirmations >= minimumConfirmations;
    }

    private static Optional<Entry> getEntryFromHistoricalPegoutsData(
        List<Entry> eligibleEntries,
        Keccak256 currentRskTxHash,
        HistoricalPegoutSelectionsConstants historicalSelections
    ) {
        return historicalSelections.getSelectedPegoutBtcTxHash(currentRskTxHash)
            .map(targetBtcTxHash -> eligibleEntries.stream()
                .filter(entry -> entry.getBtcTransaction().getHash().equals(targetBtcTxHash))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format(
                    "Historic pegout selection %s for updateCollections %s is not among the eligible entries %s",
                    targetBtcTxHash,
                    currentRskTxHash,
                    eligibleBtcTxHashes(eligibleEntries)
                ))));
    }

    private static String eligibleBtcTxHashes(List<Entry> eligibleEntries) {
        return eligibleEntries.stream()
            .map(entry -> entry.getBtcTransaction().getHash().toString())
            .sorted()
            .collect(Collectors.joining(", ", "[", "]"));
    }

    public static class Entry {
        /**
         * Compares entries using the lexicographical order of the btc tx's serialized bytes.
         */
        public static final Comparator<Entry> BTC_TX_COMPARATOR = Comparator.comparing(
            entry -> entry.getBtcTransaction().bitcoinSerialize(),
            UnsignedBytes.lexicographicalComparator()
        );

        private final BtcTransaction btcTransaction;
        private final long pegoutCreationRskBlockNumber;
        private final Keccak256 pegoutCreationRskTxHash;

        public Entry(BtcTransaction btcTransaction, long pegoutCreationRskBlockNumber, Keccak256 pegoutCreationRskTxHash) {
            this.btcTransaction = btcTransaction;
            this.pegoutCreationRskBlockNumber = pegoutCreationRskBlockNumber;
            this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        }

        public Entry(BtcTransaction btcTransaction, long pegoutCreationRskBlockNumber) {
            this(btcTransaction, pegoutCreationRskBlockNumber, null);
        }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public long getPegoutCreationRskBlockNumber() {
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
            return Objects.equals(btcTransaction, otherEntry.btcTransaction) &&
                pegoutCreationRskBlockNumber == otherEntry.pegoutCreationRskBlockNumber &&
                Objects.equals(pegoutCreationRskTxHash, otherEntry.pegoutCreationRskTxHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getBtcTransaction(), getPegoutCreationRskBlockNumber());
        }
    }
}
