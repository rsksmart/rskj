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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import com.google.common.primitives.UnsignedBytes;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * For Testnet/Mainnet, returns the entry matching the historical pegout for {@code rskTransactionHexHash} when multiple entries exists.
     * @param currentBlockNumber the current execution block number (height).
     * @param minimumConfirmations the minimum desired confirmations for the slice elements.
     * @param rskTransactionHexHash the RSK transaction hex hash that is used as the key for historical pegout's transactions.
     * @param networkId the networkId in which this code is being executed.
     * @return an optional with an entry with enough confirmations if found. If not, an empty optional.
     * @throws IllegalStateException if a required historical pegout mapping is missing or inconsistent with confirmed entries
     */
    public Optional<Entry> getNextPegoutWithEnoughConfirmations(Long currentBlockNumber, Integer minimumConfirmations,
                                                                String rskTransactionHexHash,
                                                                String networkId) {
        Stream<Entry> eligibleStream = entries.stream().filter(entry -> hasEnoughConfirmations(entry, currentBlockNumber, minimumConfirmations));
        List<Entry> confirmedPegoutTransactions = eligibleStream.toList();
        if (confirmedPegoutTransactions.isEmpty()){
            return Optional.empty();
        }
        if (confirmedPegoutTransactions.size() == 1 ||  !isNetworkWithHistoricalPegoutTransactions(networkId)) {
            return Optional.of(confirmedPegoutTransactions.get(0));
        }
        return Optional.of(getHistoricalPegoutTransaction(currentBlockNumber, rskTransactionHexHash, confirmedPegoutTransactions, networkId));
    }


    /**
     * Determines whether the given network supports historical pegout transaction
     * resolution.
     *
     * @param networkId the network identifier
     * @return {@code true} if the network is Testnet or Mainnet; {@code false} otherwise
     */

    private boolean isNetworkWithHistoricalPegoutTransactions(String networkId) {
        return NetworkParameters.ID_TESTNET.equals(networkId) || NetworkParameters.ID_MAINNET.equals(networkId);
    }

    /**
     * Resolves and returns the pegout entry that matches the historical pegout
     * transaction associated with the given RSK transaction hash.
     *
     * <p>This method assumes that multiple confirmed pegout transactions exist and that
     * the network supports historical pegout resolution.</p>
     *
     * @param currentBlockNumber the current execution block height
     * @param rskTransactionHexHash the RSK transaction hash used to look up the historical mapping
     * @param confirmedPegoutTransactions the list of pegout entries with sufficient confirmations
     * @param networkId the network identifier
     * @return the pegout entry whose Bitcoin transaction hash matches the historical mapping
     * @throws IllegalStateException if no historical mapping exists or if the mapped transaction is not found among confirmed entries.
     */
    private  Entry getHistoricalPegoutTransaction(Long currentBlockNumber, String rskTransactionHexHash, List<Entry> confirmedPegoutTransactions, String networkId)  {
        String historicalPegoutTransactionHash = HistoricalPegoutTransactions.get(rskTransactionHexHash, networkId).orElseThrow(() -> noHistoricalPegoutEntry(currentBlockNumber, rskTransactionHexHash));
        for (Entry entry : confirmedPegoutTransactions) {
            if (historicalPegoutTransactionHash.equals(entry.getBtcTransaction().getHashAsString())){
                return entry;
            }
        }
        // This should be unreachable by protocol invariant
        throw historicalPegoutTransactionHashMismatch(currentBlockNumber, rskTransactionHexHash, historicalPegoutTransactionHash);
    }

    private static RuntimeException noHistoricalPegoutEntry(Long currentBlockNumber, String rskTransactionHexHash) {
        return new IllegalStateException(String.format("No historical pegout transaction entry found for RSK transaction '%s' at at block %d:", rskTransactionHexHash, currentBlockNumber));
    }

    private RuntimeException historicalPegoutTransactionHashMismatch(Long currentBlockNumber, String rskTransactionHexHash, String historicalPegoutTransactionHash) {
        return new IllegalStateException(
                String.format(
                        "Protocol invariant violation at block %d: historical pegout transaction hash '%s' for RSK tx '%s' "
                                + "was not found among confirmed pegout transactions.",
                        currentBlockNumber, historicalPegoutTransactionHash, rskTransactionHexHash
                )
        );
    }


    public boolean removeEntry(Entry entry){
        return entries.remove(entry);
    }

    private boolean hasEnoughConfirmations(Entry entry, Long currentBlockNumber, Integer minimumConfirmations) {
        return (currentBlockNumber - entry.getPegoutCreationRskBlockNumber()) >= minimumConfirmations;
    }
}
