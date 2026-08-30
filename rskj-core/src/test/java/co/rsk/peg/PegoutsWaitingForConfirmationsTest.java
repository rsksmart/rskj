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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.pegout.HistoricalPegoutSelectionsConstants;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PegoutsWaitingForConfirmationsTest {
    private static final ActivationConfig.ForBlock VETIVER_ACTIVATIONS = ActivationConfigsForTest.vetiver900().forBlock(0L);
    private static final ActivationConfig.ForBlock ACTIVATIONS_ALL = ActivationConfigsForTest.all().forBlock(0L);

    // The real historic dataset only contains entries for pre-RSKIP559 calls that had >1 eligible pegout.
    // These fixtures use the real mainnet and testnet tables: this synthetic updateCollections hash is in
    // neither, so the lookup misses against a fully populated table and the legacy pick applies.
    private static final Keccak256 UPDATE_COLLECTIONS_TX_HASH = RskTestUtils.createHash(100);
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final int MINIMUM_CONFIRMATIONS = BRIDGE_CONSTANTS.getRsk2BtcMinimumAcceptableConfirmations();
    // The earliest rsk block at which the createSet() fixture creates a pegout. Every network's eligible set
    // is therefore the same four entries when the current block is its minimum confirmations past this one.
    private static final long EARLIEST_PEGOUT_CREATION_BLOCK = 5L;
    private static final long BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS = MINIMUM_CONFIRMATIONS + EARLIEST_PEGOUT_CREATION_BLOCK;
    private static final long BLOCK_HEIGHT_WITHOUT_ELIGIBLE_PEGOUTS = BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS - 1L;

    // A btc tx hash no fixture entry can ever carry, to drive the "recorded selection is not eligible" path.
    private static final Sha256Hash BTC_TX_HASH_NOT_IN_ANY_SET =
        Sha256Hash.wrap("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    // The entry BTC_TX_COMPARATOR sorts first among the block-BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS, MINIMUM_CONFIRMATIONS-confirmations eligible set.
    private static final String COMPARATOR_PICK_HASH = "fdd781c46b5ad7993b3f133e3af94b2e3cbcc8d19e443dfc6b555a1b0bac1527";

    // Legacy findFirst() pick over this JVM's HashSet order for the block-BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS, MINIMUM_CONFIRMATIONS-confirmations eligible set.
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
        PegoutsWaitingForConfirmations.Entry e5 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction2, 15L, RskTestUtils.createHash(0));
        PegoutsWaitingForConfirmations.Entry e6 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction2, 15L, RskTestUtils.createHash(0));
        PegoutsWaitingForConfirmations.Entry e7 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction3, 15L, null);
        PegoutsWaitingForConfirmations.Entry e8 = new PegoutsWaitingForConfirmations.Entry(uniqueTransaction3, 15L, null);

        assertEquals(e1, e2);
        assertNotEquals(e1, e3);
        assertNotEquals(e1, e4);
        assertEquals(e5, e6);
        assertNotEquals(e5, e7);
        assertEquals(e7, e8);
    }

    @Test
    void entryGetters() {
        PegoutsWaitingForConfirmations.Entry entry = new PegoutsWaitingForConfirmations.Entry(createTransaction(5, Coin.valueOf(100)), 7L);

        assertEquals(createTransaction(5, Coin.valueOf(100)), entry.getBtcTransaction());
        assertEquals(7L, entry.getPegoutCreationRskBlockNumber());
    }

    @Test
    void entryComparators() {
        PegoutsWaitingForConfirmations.Entry e1 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 7L);
        PegoutsWaitingForConfirmations.Entry e2 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 7L);
        PegoutsWaitingForConfirmations.Entry e3 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("aa"), 8L);
        PegoutsWaitingForConfirmations.Entry e4 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("bb"), 7L);
        PegoutsWaitingForConfirmations.Entry e5 = new PegoutsWaitingForConfirmations.Entry(mockTxSerialize("99"), 7L);

        assertEquals(0, PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e2));
        assertEquals(0, PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e3));
        assertTrue(PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e4) < 0);
        assertTrue(PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(e1, e5) > 0);
    }

    @Test
    void entriesCopy() {
        Assertions.assertNotSame(setEntries, set.getEntries(ACTIVATIONS_ALL));
        assertEquals(setEntries, new HashSet<>(set.getEntries(ACTIVATIONS_ALL)));

        Set<PegoutsWaitingForConfirmations.Entry> entryWithoutHash = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        Set<PegoutsWaitingForConfirmations.Entry> entryWithHash = new HashSet<>(Collections.singletonList(
            new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L, RskTestUtils.createHash(0))
        ));

        PegoutsWaitingForConfirmations transactionSetWithoutHash = new PegoutsWaitingForConfirmations(entryWithoutHash);
        PegoutsWaitingForConfirmations transactionSetWithHash = new PegoutsWaitingForConfirmations(entryWithHash);

        var resultCallWithoutHash = transactionSetWithoutHash.getEntriesWithoutHashOrdered();
        assertEquals(entryWithoutHash, new HashSet<>(resultCallWithoutHash));

        var resultCallWithHash = transactionSetWithoutHash.getEntriesWithHashOrdered();
        assertEquals(0, resultCallWithHash.size());

        var resultCallWithoutHash2 = transactionSetWithHash.getEntriesWithoutHashOrdered();
        assertEquals(0, resultCallWithoutHash2.size());

        var resultCallWithHash2 = transactionSetWithHash.getEntriesWithHashOrdered();
        assertEquals(entryWithHash, new HashSet<>(resultCallWithHash2));
    }

    @Test
    void add_nonExisting() {
        assertFalse(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
        set.add(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L));
        assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
    }

    @Test
    void add_existing() {
        var tx = createTransaction(2, Coin.valueOf(150));
        assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 32L)));
        assertEquals(1, set.getEntries(ACTIVATIONS_ALL).stream().filter(e -> e.getBtcTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());

        set.add(new PegoutsWaitingForConfirmations.Entry(tx, 23L));
        assertTrue(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 32L)));

        int size = set.getEntries(ACTIVATIONS_ALL).size();
        set.add(new PegoutsWaitingForConfirmations.Entry(tx, 23L));
        assertEquals(set.getEntries(ACTIVATIONS_ALL).size(), size);
        assertFalse(set.getEntries(ACTIVATIONS_ALL).contains(new PegoutsWaitingForConfirmations.Entry(tx, 23L)));
        assertEquals(1, set.getEntries(ACTIVATIONS_ALL).stream().filter(e -> e.getBtcTransaction().equals(tx)).count());
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

        assertEquals(2, pegouts.getEntries(ACTIVATIONS_ALL).size(), "Must not add multiple pegouts for same TX");
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_no_matches() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITHOUT_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            VETIVER_ACTIVATIONS
        );
        assertFalse(result.isPresent());
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_ok() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            VETIVER_ACTIVATIONS
        );
        assertTrue(result.isPresent());
        assertTrue(set.removeEntry(result.get()));
        assertFalse(set.removeEntry(result.get()));
    }

    @Test
    void getNextPegoutWithEnoughConfirmation_multipleMatch_rskip559Off() {
        int size = set.getEntries(VETIVER_ACTIVATIONS).size();
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            VETIVER_ACTIVATIONS
        );
        assertTrue(result.isPresent());

        var entry = result.get();
        var hash = entry.getBtcTransaction().getHash().toString();

        assertEquals(LEGACY_FIND_FIRST_HASH, hash, "Valid candidate for non deterministic pegouts sorting");
        assertTrue(set.removeEntry(entry));
        assertFalse(set.removeEntry(entry));
        assertEquals(set.getEntries(VETIVER_ACTIVATIONS).size(), size-1);
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_rskip559() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL
        );
        assertTrue(result.isPresent());

        var entry = result.get();
        var hash = entry.getBtcTransaction().getHash().toString();

        assertEquals(COMPARATOR_PICK_HASH, hash, "Valid candidate for non fixed pegouts sorting");
    }

    @Test
    void getNextPegout_beforeRskip559_multipleEligible_usesHistoricalSelection() {
        // With historical data, reproduce the recorded selection instead of the JVM-dependent findFirst().
        PegoutsWaitingForConfirmations.Entry target = firstEligibleEntryOtherThanLegacyPick();
        Sha256Hash targetBtcTxHash = target.getBtcTransaction().getHash();

        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            bridgeConstantsWithHistoricalSelection(targetBtcTxHash),
            VETIVER_ACTIVATIONS
        );

        assertTrue(result.isPresent());

        BtcTransaction pegoutTx = result.get().getBtcTransaction();
        assertEquals(targetBtcTxHash, pegoutTx.getHash());
        assertNotEquals(
            LEGACY_FIND_FIRST_HASH,
            pegoutTx.getHash().toString(),
            "The historic selection must override the legacy findFirst() pick"
        );
    }

    @ParameterizedTest(name = "{displayName} - {0}")
    @MethodSource("networksWithPopulatedHistoricalSelections")
    void getNextPegout_beforeRskip559_datasetMiss_fallsBackToFindFirst(BridgeConstants bridgeConstants) {
        long blockNumber = bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations() + EARLIEST_PEGOUT_CREATION_BLOCK;
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            blockNumber,
            bridgeConstants,
            VETIVER_ACTIVATIONS
        );

        assertTrue(result.isPresent());
        assertEquals(
            LEGACY_FIND_FIRST_HASH,
            result.get().getBtcTransaction().getHash().toString(),
            "Legacy HashSet findFirst() pick changed: the createSet() fixture moved, or this JVM orders "
                + "the set differently. Re-derive LEGACY_FIND_FIRST_HASH from the actual value above."
        );
    }

    private static Stream<Arguments> networksWithPopulatedHistoricalSelections() {
        return Stream.of(
            Arguments.of(named("mainnet", BRIDGE_CONSTANTS)),
            Arguments.of(named("testnet", BridgeTestNetConstants.getInstance()))
        );
    }

    @Test
    void getNextPegout_beforeRskip559_historicalSelectionNotEligible_throws() {
        BridgeConstants bridgeConstants = bridgeConstantsWithHistoricalSelection(BTC_TX_HASH_NOT_IN_ANY_SET);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(
                UPDATE_COLLECTIONS_TX_HASH,
                BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
                bridgeConstants,
                VETIVER_ACTIVATIONS
            )
        );

        // The message must name the eligible set, so a missing record is diagnosable from the log alone.
        assertTrue(thrown.getMessage().contains(BTC_TX_HASH_NOT_IN_ANY_SET.toString()));
        assertTrue(thrown.getMessage().contains(LEGACY_FIND_FIRST_HASH));
    }

    @Test
    void getNextPegout_rskip559_ignoresHistoricalSelection() {
        // From RSKIP559 on the comparator sort decides. Seed a dataset that would pick a different entry:
        // if it were consulted, the result would not be the comparator's.
        PegoutsWaitingForConfirmations.Entry decoy = firstEligibleEntryOtherThan(COMPARATOR_PICK_HASH);
        BridgeConstants bridgeConstants = bridgeConstantsWithHistoricalSelection(decoy.getBtcTransaction().getHash());

        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            bridgeConstants,
            ACTIVATIONS_ALL
        );

        assertTrue(result.isPresent());
        assertEquals(COMPARATOR_PICK_HASH, result.get().getBtcTransaction().getHash().toString());
    }

    /**
     * Bridge constants whose historic table maps the updateCollections tx to {@code selectedBtcTxHash}.
     * The genuine mainnet and testnet tables cannot drive the hit path: their values are real btc tx
     * hashes, and the fixture's synthetic transactions can never produce one.
     */
    private static BridgeConstants bridgeConstantsWithHistoricalSelection(Sha256Hash selectedBtcTxHash) {
        HistoricalPegoutSelectionsConstants historicalSelections = new HistoricalPegoutSelectionsConstants() {
            {
                selections = Map.of(UPDATE_COLLECTIONS_TX_HASH, selectedBtcTxHash);
            }
        };

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations()).thenReturn(MINIMUM_CONFIRMATIONS);
        when(bridgeConstants.getHistoricalPegoutSelectionsConstants()).thenReturn(historicalSelections);

        return bridgeConstants;
    }

    @Test
    void getNextPegout_nullRskTxHash_throws() {
        // Without the tx hash the historic selection cannot be looked up, and the pre-RSKIP559 pick would
        // silently go back to being JVM dependent.
        assertThrows(NullPointerException.class, () -> set.getNextPegoutWithEnoughConfirmations(
            null,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            VETIVER_ACTIVATIONS
        ));
    }

    @Test
    void getNextPegout_nullBridgeConstants_throws() {
        assertThrows(NullPointerException.class, () -> set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            null,
            VETIVER_ACTIVATIONS
        ));
    }

    @Test
    void getNextPegout_nullActivations_throws() {
        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(
                UPDATE_COLLECTIONS_TX_HASH,
                BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
                BRIDGE_CONSTANTS,
                null
            )
        );

        // Assert the guard's own message: without it the dereference below would throw an NPE too.
        assertEquals("activations must not be null", thrown.getMessage());
    }

    @Test
    void getNextPegout_beforeRskip559_historicalSelectionWithNoEligibleEntries_throws() {
        // A recorded selection for a call that had no eligible entry can only be a dataset error. It must
        // fail loudly, and the message must show the empty eligible set, so the log alone diagnoses it.
        BridgeConstants bridgeConstants = bridgeConstantsWithHistoricalSelection(BTC_TX_HASH_NOT_IN_ANY_SET);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> set.getNextPegoutWithEnoughConfirmations(
                UPDATE_COLLECTIONS_TX_HASH,
                BLOCK_HEIGHT_WITHOUT_ELIGIBLE_PEGOUTS,
                bridgeConstants,
                VETIVER_ACTIVATIONS
            )
        );

        assertTrue(thrown.getMessage().contains(BTC_TX_HASH_NOT_IN_ANY_SET.toString()));
        assertTrue(thrown.getMessage().contains("[]"), "the empty eligible set must be visible in the message");
    }

    @Test
    void getNextPegout_beforeRskip559_historicalSelectionIsTheOnlyEligibleEntry_returnsIt() {
        PegoutsWaitingForConfirmations.Entry onlyEligible = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK);
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(onlyEligible, entryWithoutEnoughConfirmations());
        BridgeConstants bridgeConstants = bridgeConstantsWithHistoricalSelection(onlyEligible.getBtcTransaction().getHash());

        Optional<PegoutsWaitingForConfirmations.Entry> result = pegouts.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            bridgeConstants,
            VETIVER_ACTIVATIONS
        );

        assertEquals(Optional.of(onlyEligible), result);
    }

    @Test
    void getNextPegout_beforeRskip559_historicalSelectionDiffersFromTheOnlyEligibleEntry_throws() {
        // With one eligible entry the legacy pick was already deterministic, so a recorded selection that
        // names a different tx is a dataset error too, not a selection to honour.
        PegoutsWaitingForConfirmations.Entry onlyEligible = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK);
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(onlyEligible, entryWithoutEnoughConfirmations());
        BridgeConstants bridgeConstants = bridgeConstantsWithHistoricalSelection(BTC_TX_HASH_NOT_IN_ANY_SET);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> pegouts.getNextPegoutWithEnoughConfirmations(
                UPDATE_COLLECTIONS_TX_HASH,
                BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
                bridgeConstants,
                VETIVER_ACTIVATIONS
            )
        );

        assertTrue(thrown.getMessage().contains(BTC_TX_HASH_NOT_IN_ANY_SET.toString()));
        assertTrue(thrown.getMessage().contains(onlyEligible.getBtcTransaction().getHash().toString()));
    }

    @Test
    void getNextPegout_beforeRskip559_datasetMiss_singleEligible_returnsIt() {
        // No findFirst() ambiguity exists with one eligible entry, so every JVM must return that entry.
        PegoutsWaitingForConfirmations.Entry onlyEligible = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK);
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(onlyEligible, entryWithoutEnoughConfirmations());

        Optional<PegoutsWaitingForConfirmations.Entry> result = pegouts.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            VETIVER_ACTIVATIONS
        );

        assertEquals(Optional.of(onlyEligible), result);
    }

    @Test
    void getNextPegout_beforeRskip559_emptyDataset_fallsBackToFindFirst() {
        // Regtest has no pre-RSKIP559 chain, so its table is empty and every lookup misses. Its minimum
        // confirmations make the eligible set the same four entries the populated-table tests use.
        BridgeConstants regTestConstants = new BridgeRegTestConstants();
        long blockNumber = regTestConstants.getRsk2BtcMinimumAcceptableConfirmations() + EARLIEST_PEGOUT_CREATION_BLOCK;

        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            blockNumber,
            regTestConstants,
            VETIVER_ACTIVATIONS
        );

        assertTrue(result.isPresent());
        assertEquals(LEGACY_FIND_FIRST_HASH, result.get().getBtcTransaction().getHash().toString());
    }

    @Test
    void getNextPegout_rskip559_noEligibleEntries_returnsEmpty() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITHOUT_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL
        );

        assertEquals(Optional.empty(), result);
    }

    @Test
    void getNextPegout_rskip559_singleEligibleEntry_returnsIt() {
        PegoutsWaitingForConfirmations.Entry onlyEligible = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK);
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(onlyEligible, entryWithoutEnoughConfirmations());

        Optional<PegoutsWaitingForConfirmations.Entry> result = pegouts.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL
        );

        assertEquals(Optional.of(onlyEligible), result);
    }

    @ParameterizedTest(name = "{displayName} - {0}")
    @MethodSource("activationsBeforeAndAfterRskip559")
    void getNextPegout_confirmationsExactlyAtMinimum_isEligible(ActivationConfig.ForBlock activations) {
        // Eligibility is confirmations >= minimum. Pin the boundary: turning it into > would silently
        // delay every pegout by one block on both sides of the fork.
        PegoutsWaitingForConfirmations.Entry exactlyAtMinimum = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK);
        PegoutsWaitingForConfirmations.Entry oneConfirmationShort = entryCreatedAt(EARLIEST_PEGOUT_CREATION_BLOCK + 1);
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(exactlyAtMinimum, oneConfirmationShort);

        Optional<PegoutsWaitingForConfirmations.Entry> result = pegouts.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            activations
        );

        assertEquals(Optional.of(exactlyAtMinimum), result);
    }

    @ParameterizedTest(name = "{displayName} - {0}")
    @MethodSource("activationsBeforeAndAfterRskip559")
    void getNextPegout_entryCreatedAfterCurrentBlock_isNotEligible(ActivationConfig.ForBlock activations) {
        // A reorg can leave an entry whose creation block is above the executing one, giving negative
        // confirmations. It must never be eligible.
        PegoutsWaitingForConfirmations pegouts = pegoutsWith(entryCreatedAt(BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS + 1));

        Optional<PegoutsWaitingForConfirmations.Entry> result = pegouts.getNextPegoutWithEnoughConfirmations(
            UPDATE_COLLECTIONS_TX_HASH,
            BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS,
            BRIDGE_CONSTANTS,
            activations
        );

        assertEquals(Optional.empty(), result);
    }

    private static Stream<Arguments> activationsBeforeAndAfterRskip559() {
        return Stream.of(
            Arguments.of(named("before RSKIP559", VETIVER_ACTIVATIONS)),
            Arguments.of(named("from RSKIP559 on", ACTIVATIONS_ALL))
        );
    }

    private PegoutsWaitingForConfirmations pegoutsWith(PegoutsWaitingForConfirmations.Entry... entries) {
        return new PegoutsWaitingForConfirmations(new HashSet<>(Arrays.asList(entries)));
    }

    /**
     * An entry whose btc tx is distinct per creation block, so a set built from several of them holds one
     * entry per block without the deduplication in {@code addEntry} collapsing them.
     */
    private PegoutsWaitingForConfirmations.Entry entryCreatedAt(long pegoutCreationRskBlockNumber) {
        BtcTransaction btcTransaction = createTransaction((int) pegoutCreationRskBlockNumber, Coin.COIN);
        return new PegoutsWaitingForConfirmations.Entry(btcTransaction, pegoutCreationRskBlockNumber);
    }

    private PegoutsWaitingForConfirmations.Entry entryWithoutEnoughConfirmations() {
        return entryCreatedAt(BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS);
    }

    private PegoutsWaitingForConfirmations.Entry firstEligibleEntryOtherThanLegacyPick() {
        return firstEligibleEntryOtherThan(LEGACY_FIND_FIRST_HASH);
    }

    /**
     * An eligible entry whose btc tx hash differs from {@code excludedBtcTxHash}. Tests that prove one
     * selection strategy overrode another need a target the other strategy would not have chosen anyway.
     * Otherwise, they pass whichever strategy ran.
     */
    private PegoutsWaitingForConfirmations.Entry firstEligibleEntryOtherThan(String excludedBtcTxHash) {
        for (PegoutsWaitingForConfirmations.Entry entry : setEntries) {
            boolean eligible = (BLOCK_HEIGHT_WITH_ELIGIBLE_PEGOUTS - entry.getPegoutCreationRskBlockNumber()) >= MINIMUM_CONFIRMATIONS;
            if (eligible && !entry.getBtcTransaction().getHash().toString().equals(excludedBtcTxHash)) {
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
