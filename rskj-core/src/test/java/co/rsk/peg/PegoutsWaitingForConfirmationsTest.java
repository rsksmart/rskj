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

import co.rsk.bitcoinj.core.*;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.constants.HistoricalPegoutSelectionsConstants;
import co.rsk.peg.constants.HistoricalPegoutSelectionsMainNetConstants;
import co.rsk.peg.constants.HistoricalPegoutSelectionsTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PegoutsWaitingForConfirmationsTest {
    private static final ActivationConfig.ForBlock ACTIVATIONS_ALL = ActivationConfigsForTest.all().forBlock(0L);

    // The historic dataset is only consulted before RSKIP559 and when more than one entry is eligible.
    // These fixtures use the real mainnet and testnet tables: this synthetic updateCollections hash is in
    // neither, so the lookup misses against a fully populated table and the legacy pick applies.
    private static final Keccak256 UPDATE_COLLECTIONS_TX_HASH = PegTestUtils.createHash3(100);
    private static final HistoricalPegoutSelectionsConstants MAINNET_SELECTIONS =
        HistoricalPegoutSelectionsMainNetConstants.getInstance();
    private static final HistoricalPegoutSelectionsConstants TESTNET_SELECTIONS =
        HistoricalPegoutSelectionsTestNetConstants.getInstance();

    // The entry BTC_TX_COMPARATOR sorts first among the block-10, 5-confirmations eligible set.
    private static final String COMPARATOR_PICK_HASH = "fdd781c46b5ad7993b3f133e3af94b2e3cbcc8d19e443dfc6b555a1b0bac1527";

    // Legacy findFirst() pick over this JVM's HashSet order for the block-10, 5-confirmations eligible set.
    // Verified identical on Java 17 (Zulu 17.0.13) and Java 21 (Temurin 21.0.5). If it stops matching, the
    // fixture in createSet() changed, or a JDK reordered it: re-derive it from the actual value reported by
    // getNextPegout_beforeRskip559_datasetMiss_fallsBackToFindFirst, which is the pure legacy path.
    private static final String LEGACY_FIND_FIRST_HASH = "53efc6f78eb9d159cfee76ec45bcffb08fd11f85c762e1eacf54e5c014da219d";

    private Set<PegoutsWaitingForConfirmations.Entry> setEntries;
    private PegoutsWaitingForConfirmations set;
    private final TestSystemProperties config = new TestSystemProperties();

    @BeforeEach
    void createSet() {
        setEntries = new HashSet<>(Arrays.asList(
            new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(150)), 32L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(5, Coin.COIN), 100L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(4, Coin.FIFTY_COINS), 7L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(3, Coin.MILLICOIN), 10L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(8, Coin.CENT.times(5)), 5L),

            // pegouts for same block
            new PegoutsWaitingForConfirmations.Entry(createTransaction(11, Coin.MILLICOIN), 5L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(12, Coin.MILLICOIN), 5L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(13, Coin.MILLICOIN), 5L)

        ));
        set = new PegoutsWaitingForConfirmations(setEntries);
    }

    @Test
    void entryEquals() {

        BtcTransaction uniqueTransaction1 = createUniqueTransaction(2, Coin.valueOf(150));
        BtcTransaction uniqueTransaction2 = createUniqueTransaction(5, Coin.valueOf(230));
        BtcTransaction uniqueTransaction3 = createUniqueTransaction(5, Coin.valueOf(230));

        PegoutsWaitingForConfirmations.Entry e1 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction1, 15L);
        PegoutsWaitingForConfirmations.Entry e2 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction1, 15L);
        PegoutsWaitingForConfirmations.Entry e3 = new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(149)), 14L);
        PegoutsWaitingForConfirmations.Entry e4 = new PegoutsWaitingForConfirmations.Entry(createTransaction(5, Coin.valueOf(230)), 15L);
        PegoutsWaitingForConfirmations.Entry e5 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction2, 15L, PegTestUtils.createHash3(0));
        PegoutsWaitingForConfirmations.Entry e6 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction2, 15L, PegTestUtils.createHash3(0));
        PegoutsWaitingForConfirmations.Entry e7 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction3, 15L, null);
        PegoutsWaitingForConfirmations.Entry e8 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction3, 15L, null);

        Assertions.assertEquals(e1, e2);
        Assertions.assertNotEquals(e1, e3);
        Assertions.assertNotEquals(e1, e4);
        Assertions.assertEquals(e5, e6);
        Assertions.assertNotEquals(e5, e7);
        Assertions.assertEquals(e7, e8);
    }

    @Test
    void entryGetters() {
        PegoutsWaitingForConfirmations.Entry entry = new PegoutsWaitingForConfirmations.Entry(createTransaction(5, Coin.valueOf(100)), 7L);

        Assertions.assertEquals(createTransaction(5, Coin.valueOf(100)), entry.getBtcTransaction());
        Assertions.assertEquals(7L, entry.getPegoutCreationRskBlockNumber().longValue());
    }

    @Test
    void entryComparators() {
        PegoutsWaitingForConfirmations.Entry e1 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 7L);
        PegoutsWaitingForConfirmations.Entry e2 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 7L);
        PegoutsWaitingForConfirmations.Entry e3 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 8L);
        PegoutsWaitingForConfirmations.Entry e4 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("bb"), 7L);
        PegoutsWaitingForConfirmations.Entry e5 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("99"), 7L);

        Assertions.assertEquals(0, PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e2));
        Assertions.assertEquals(0, PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e3));
        Assertions.assertTrue(PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e4) < 0);
        Assertions.assertTrue(PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e5) > 0);
    }

    @Test
    void entriesCopy() {
        Assertions.assertNotSame(setEntries, set.getEntries(ACTIVATIONS_ALL));
        Assertions.assertEquals(setEntries, new HashSet<>(set.getEntries(ACTIVATIONS_ALL)));

        Set<PegoutsWaitingForConfirmations.Entry> entryWithoutHash = new HashSet<>(Collections.singletonList(
                new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        Set<PegoutsWaitingForConfirmations.Entry> entryWithHash = new HashSet<>(Collections.singletonList(
                new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L, PegTestUtils.createHash3(0))
        ));

        PegoutsWaitingForConfirmations transactionSetWithoutHash = new PegoutsWaitingForConfirmations(entryWithoutHash);
        PegoutsWaitingForConfirmations transactionSetWithHash = new PegoutsWaitingForConfirmations(entryWithHash);

        var resultCallWithoutHash = transactionSetWithoutHash.getEntriesWithoutHashOrdered();
        Assertions.assertEquals(entryWithoutHash, new HashSet<>(resultCallWithoutHash));

        var resultCallWithHash = transactionSetWithoutHash.getEntriesWithHashOrdered();
        Assertions.assertEquals(0, resultCallWithHash.size());

        var resultCallWithoutHash2 = transactionSetWithHash.getEntriesWithoutHashOrdered();
        Assertions.assertEquals(0, resultCallWithoutHash2.size());

        var resultCallWithHash2 = transactionSetWithHash.getEntriesWithHashOrdered();
        Assertions.assertEquals(entryWithHash, new HashSet<>(resultCallWithHash2));
    }

    @Test
    void add_nonExisting() {
        Assertions.assertFalse(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
        set.add(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L));
        Assertions.assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
    }

    @Test
    void add_existing() {
        var tx = createTransaction(2, Coin.valueOf(150));
        Assertions.assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 32L)));
        Assertions.assertEquals(1, set.getEntries(ACTIVATIONS_ALL).stream().filter(e -> e.getBtcTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());

        set.add(new PegoutsWaitingForConfirmations.Entry(tx, 23L));
        Assertions.assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 32L)));

        int size = set.getEntries(ACTIVATIONS_ALL).size();
        set.add(new PegoutsWaitingForConfirmations.Entry(tx, 23L));
        Assertions.assertEquals(set.getEntries(ACTIVATIONS_ALL).size(), size);
        Assertions.assertFalse(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 23L)));
        Assertions.assertEquals(1, set.getEntries(ACTIVATIONS_ALL).stream().filter(e -> e.getBtcTransaction().equals(tx)).count());
    }

    @Test
    void verifyDeduplication() {
        // Just another version of more simple deduplication test
        var pegouts = new PegoutsWaitingForConfirmations(Collections.emptySet());

        var eTx = createTransaction(42, Coin.valueOf(42));
        var e10 = new PegoutsWaitingForConfirmations.Entry(eTx, 42L);
        var e11 = new PegoutsWaitingForConfirmations.Entry(eTx, 55L);
        var e12 = new PegoutsWaitingForConfirmations.Entry(eTx, 66L);
        var e13 = new PegoutsWaitingForConfirmations.Entry(eTx, 77L);
        var e20 = new PegoutsWaitingForConfirmations.Entry(createTransaction(64, Coin.CENT), 77L);

        pegouts.add(e10);
        pegouts.add(e11);
        pegouts.add(e12);
        pegouts.add(e13);
        pegouts.add(e20);

        Assertions.assertEquals(2, pegouts.getEntries(ACTIVATIONS_ALL).size(), "Must not add multiple pegouts for same TX");
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_no_matches() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(9L, 5, ActivationConfigsForTest.vetiver900().forBlock(9L), UPDATE_COLLECTIONS_TX_HASH, MAINNET_SELECTIONS);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_ok() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.vetiver900().forBlock(10L), UPDATE_COLLECTIONS_TX_HASH, MAINNET_SELECTIONS);
        Assertions.assertTrue(result.isPresent());
        Assertions.assertTrue(set.removeEntry(result.get()));
        Assertions.assertFalse(set.removeEntry(result.get()));
    }

    @Test
    void getNextPegoutWithEnoughConfirmation_multipleMatch_rskip559Off() {
        int size = set.getEntries(ACTIVATIONS_ALL).size();
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.vetiver900().forBlock(10L), UPDATE_COLLECTIONS_TX_HASH, MAINNET_SELECTIONS);
        Assertions.assertTrue(result.isPresent());

        var entry = result.get();
        var hash = entry.getBtcTransaction().getHash().toString();

        Assertions.assertEquals(
            "53efc6f78eb9d159cfee76ec45bcffb08fd11f85c762e1eacf54e5c014da219d",
            hash,
            "Valid candidate for non deterministic pegouts sorting"
        );

        Assertions.assertTrue(set.removeEntry(entry));
        Assertions.assertFalse(set.removeEntry(entry));
        Assertions.assertEquals(set.getEntries(ACTIVATIONS_ALL).size(), size-1);
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_rskip559() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.all().forBlock(1L), UPDATE_COLLECTIONS_TX_HASH, MAINNET_SELECTIONS);
        Assertions.assertTrue(result.isPresent());

        var entry = result.get();
        var hash = entry.getBtcTransaction().getHash().toString();

        Assertions.assertEquals(
            COMPARATOR_PICK_HASH,
            hash,
            "Valid candidate for non fixed pegouts sorting"
        );
    }

    @Test
    void getNextPegout_beforeRskip559_multipleEligible_usesHistoricalSelection() {
        // With historical data, reproduce the recorded selection instead of the JVM-dependent findFirst().
        PegoutsWaitingForConfirmations.Entry target = firstEligibleEntryOtherThanLegacyPick();
        Sha256Hash targetBtcTxHash = target.getBtcTransaction().getHash();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.vetiver900().forBlock(10L);
        HistoricalPegoutSelectionsConstants historicalSelections = selectionsMapping(targetBtcTxHash);

        Optional<PegoutsWaitingForConfirmations.Entry> result =
            set.getNextPegoutWithEnoughConfirmations(10L, 5, activations, UPDATE_COLLECTIONS_TX_HASH, historicalSelections);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(targetBtcTxHash, result.get().getBtcTransaction().getHash());
        Assertions.assertNotEquals(LEGACY_FIND_FIRST_HASH, result.get().getBtcTransaction().getHash().toString(),
            "The historic selection must override the legacy findFirst() pick");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("populatedDatasets")
    void getNextPegout_beforeRskip559_datasetMiss_fallsBackToFindFirst(
            String network, HistoricalPegoutSelectionsConstants historicalSelections) {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.vetiver900().forBlock(10L);

        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            10L, 5, activations, UPDATE_COLLECTIONS_TX_HASH, historicalSelections);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(LEGACY_FIND_FIRST_HASH, result.get().getBtcTransaction().getHash().toString(),
            "Legacy HashSet findFirst() pick changed: the createSet() fixture moved, or this JVM orders "
                + "the set differently. Re-derive LEGACY_FIND_FIRST_HASH from the actual value above.");
    }

    private static Stream<Arguments> populatedDatasets() {
        return Stream.of(
            Arguments.of("mainnet", MAINNET_SELECTIONS),
            Arguments.of("testnet", TESTNET_SELECTIONS)
        );
    }

    @Test
    void getNextPegout_beforeRskip559_historicalSelectionNotEligible_throws() {
        Sha256Hash notEligibleBtcTxHash =
            Sha256Hash.wrap("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.vetiver900().forBlock(10L);
        HistoricalPegoutSelectionsConstants historicalSelections = selectionsMapping(notEligibleBtcTxHash);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(10L, 5, activations, UPDATE_COLLECTIONS_TX_HASH, historicalSelections));

        // The message must name the eligible set, so a missing record is diagnosable from the log alone.
        Assertions.assertTrue(thrown.getMessage().contains(notEligibleBtcTxHash.toString()));
        Assertions.assertTrue(thrown.getMessage().contains(LEGACY_FIND_FIRST_HASH));
    }

    @Test
    void getNextPegout_rskip559_ignoresHistoricalSelection() {
        // From RSKIP559 on the comparator sort decides. Seed a dataset that would pick a different entry:
        // if it were consulted, the result would not be the comparator's.
        PegoutsWaitingForConfirmations.Entry decoy = firstEligibleEntryOtherThan(COMPARATOR_PICK_HASH);
        HistoricalPegoutSelectionsConstants selections =
            selectionsMapping(decoy.getBtcTransaction().getHash());

        Optional<PegoutsWaitingForConfirmations.Entry> result =
            set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.all().forBlock(1L), UPDATE_COLLECTIONS_TX_HASH, selections);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(
            COMPARATOR_PICK_HASH,
            result.get().getBtcTransaction().getHash().toString());
    }

    /**
     * A real dataset mapping the updateCollections tx to {@code selected}. The genuine mainnet and testnet
     * tables cannot be used for the hit path: their values are real btc tx hashes, and the fixture's
     * synthetic transactions can never produce one.
     */
    private static HistoricalPegoutSelectionsConstants selectionsMapping(Sha256Hash selected) {
        return new HistoricalPegoutSelectionsConstants() {
            {
                selections = Map.of(UPDATE_COLLECTIONS_TX_HASH, selected);
            }
        };
    }

    @Test
    void getNextPegout_nullRskTxHash_throws() {
        // Without the tx hash the historic selection cannot be looked up, and the pre-RSKIP559 pick would
        // silently go back to being JVM dependent.
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.vetiver900().forBlock(10L);

        Assertions.assertThrows(NullPointerException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(10L, 5, activations, null, MAINNET_SELECTIONS));
    }

    @Test
    void getNextPegout_nullHistoricalSelections_throws() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.vetiver900().forBlock(10L);

        Assertions.assertThrows(NullPointerException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(10L, 5, activations, UPDATE_COLLECTIONS_TX_HASH, null));
    }

    private PegoutsWaitingForConfirmations.Entry firstEligibleEntryOtherThanLegacyPick() {
        return firstEligibleEntryOtherThan(LEGACY_FIND_FIRST_HASH);
    }

    /**
     * An eligible entry whose btc tx hash differs from {@code excludedBtcTxHash}. Tests that prove one
     * selection strategy overrode another need a target the other strategy would not have chosen anyway,
     * otherwise they pass whichever strategy ran.
     */
    private PegoutsWaitingForConfirmations.Entry firstEligibleEntryOtherThan(String excludedBtcTxHash) {
        for (PegoutsWaitingForConfirmations.Entry entry : setEntries) {
            boolean eligibleAtBlock10 = (10L - entry.getPegoutCreationRskBlockNumber()) >= 5;
            if (eligibleAtBlock10 && !entry.getBtcTransaction().getHash().toString().equals(excludedBtcTxHash)) {
                return entry;
            }
        }
        throw new IllegalStateException("test fixture has no alternative eligible entry");
    }

    private BtcTransaction createTransaction(int toPk, Coin value) {
        return createTransaction(toPk, value, BtcECKey.fromPrivate(BigInteger.valueOf(123456)));
    }

    private BtcTransaction createUniqueTransaction(int toPk, Coin value) {
        return createTransaction(toPk, value, new BtcECKey());
    }

    private BtcTransaction createTransaction(int toPk, Coin value, BtcECKey btcECKey) {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BtcTransaction input = new BtcTransaction(params);

        input.addOutput(Coin.FIFTY_COINS, btcECKey.toAddress(params));

        Address to = BtcECKey.fromPrivate(BigInteger.valueOf(toPk)).toAddress(params);

        BtcTransaction result = new BtcTransaction(params);
        result.addInput(input.getOutput(0));
        result.getInput(0).disconnect();
        result.addOutput(value, to);
        return result;
    }

    private BtcTransaction mockTxSerialize(String serializationHex) {
        BtcTransaction result = mock(BtcTransaction.class);
        when(result.bitcoinSerialize()).thenReturn(Hex.decode(serializationHex));
        return result;
    }
}
