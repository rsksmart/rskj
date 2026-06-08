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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PegoutsWaitingForConfirmationsTest {
    private static final ActivationConfig.ForBlock ACTIVATIONS_ALL = ActivationConfigsForTest.all().forBlock(0L);

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
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(9L, 5, ActivationConfigsForTest.vetiver900().forBlock(9L));
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_ok() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.vetiver900().forBlock(10L));
        Assertions.assertTrue(result.isPresent());
        Assertions.assertTrue(set.removeEntry(result.get()));
        Assertions.assertFalse(set.removeEntry(result.get()));
    }

    @Test
    void getNextPegoutWithEnoughConfirmation_multipleMatch_rskip559Off() {
        int size = set.getEntries(ACTIVATIONS_ALL).size();
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.vetiver900().forBlock(10L));
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
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5, ActivationConfigsForTest.all().forBlock(1L));
        Assertions.assertTrue(result.isPresent());

        var entry = result.get();
        var hash = entry.getBtcTransaction().getHash().toString();

        Assertions.assertEquals(
            "fdd781c46b5ad7993b3f133e3af94b2e3cbcc8d19e443dfc6b555a1b0bac1527",
            hash,
            "Valid candidate for non fixed pegouts sorting"
        );
    }

    // RSKIP559 disabled (pre-fork): selection follows HashSet iteration order.
    private static final ActivationConfig.ForBlock ACTIVATIONS_RSKIP559_OFF = ActivationConfigsForTest.vetiver900().forBlock(10L);
    // RSKIP559 enabled (post-fork): selection follows Entry.BTC_TX_COMPARATOR.
    private static final ActivationConfig.ForBlock ACTIVATIONS_RSKIP559_ON = ActivationConfigsForTest.all().forBlock(1L);

    // ----- Mechanism B (RSKIP559 on): deterministic sorted selection -----

    @Test
    void getNextPegoutWithEnoughConfirmations_rskip559On_picksComparatorMinAndIsOrderIndependent() {
        // All created at block 5; with current=10, min=5 every entry has enough confirmations,
        // so the selected one must be the BTC_TX_COMPARATOR minimum of the whole set.
        List<BtcTransaction> txs = Arrays.asList(
            createTransaction(8, Coin.CENT.times(5)),
            createTransaction(11, Coin.MILLICOIN),
            createTransaction(12, Coin.MILLICOIN),
            createTransaction(13, Coin.MILLICOIN),
            createTransaction(21, Coin.COIN),
            createTransaction(34, Coin.SATOSHI)
        );
        BtcTransaction expected = txs.stream()
            .min((a, b) -> com.google.common.primitives.UnsignedBytes.lexicographicalComparator()
                .compare(a.bitcoinSerialize(), b.bitcoinSerialize()))
            .orElseThrow();

        // Build with two different insertion orders; selection must be identical and equal to expected.
        List<BtcTransaction> reversed = new ArrayList<>(txs);
        Collections.reverse(reversed);
        for (List<BtcTransaction> order : Arrays.asList(txs, reversed)) {
            PegoutsWaitingForConfirmations pegouts = new PegoutsWaitingForConfirmations(new HashSet<>());
            order.forEach(tx -> pegouts.add(new PegoutsWaitingForConfirmations.Entry(tx, 5L)));

            Optional<PegoutsWaitingForConfirmations.Entry> selected =
                pegouts.getNextPegoutWithEnoughConfirmations(10L, 5, ACTIVATIONS_RSKIP559_ON);

            Assertions.assertTrue(selected.isPresent());
            Assertions.assertEquals(expected, selected.get().getBtcTransaction(),
                "Post-fork selection must be the comparator minimum, independent of insertion order");
        }
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_rskip559On_drainsInComparatorOrder() {
        // current high enough that every entry is confirmed; draining must yield ascending comparator order.
        List<PegoutsWaitingForConfirmations.Entry> drained = new ArrayList<>();
        Optional<PegoutsWaitingForConfirmations.Entry> next;
        while ((next = set.getNextPegoutWithEnoughConfirmations(1000L, 5, ACTIVATIONS_RSKIP559_ON)).isPresent()) {
            drained.add(next.get());
            set.removeEntry(next.get());
        }

        List<PegoutsWaitingForConfirmations.Entry> expected = new ArrayList<>(drained);
        expected.sort(PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR);

        Assertions.assertEquals(expected, drained, "Post-fork draining must be in BTC_TX_COMPARATOR order");
        Assertions.assertEquals(0, set.getEntries(ACTIVATIONS_RSKIP559_ON).size());
    }

    @Test
    void getEntries_rskip559On_returnsSortedByBtcTxComparator() {
        List<PegoutsWaitingForConfirmations.Entry> entries = new ArrayList<>(set.getEntries(ACTIVATIONS_RSKIP559_ON));
        assertSortedByComparator(entries);
    }

    @Test
    void orderedGetters_areSortedAndPartitionedByHash() {
        Set<PegoutsWaitingForConfirmations.Entry> mixed = new HashSet<>(Arrays.asList(
            new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(150)), 32L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(5, Coin.COIN), 100L),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(4, Coin.FIFTY_COINS), 7L, PegTestUtils.createHash3(1)),
            new PegoutsWaitingForConfirmations.Entry(createTransaction(3, Coin.MILLICOIN), 10L, PegTestUtils.createHash3(2))
        ));
        PegoutsWaitingForConfirmations pegouts = new PegoutsWaitingForConfirmations(mixed);

        List<PegoutsWaitingForConfirmations.Entry> withoutHash = new ArrayList<>(pegouts.getEntriesWithoutHashOrdered());
        List<PegoutsWaitingForConfirmations.Entry> withHash = new ArrayList<>(pegouts.getEntriesWithHashOrdered());

        Assertions.assertEquals(2, withoutHash.size());
        Assertions.assertEquals(2, withHash.size());
        withoutHash.forEach(e -> Assertions.assertNull(e.getPegoutCreationRskTxHash()));
        withHash.forEach(e -> Assertions.assertNotNull(e.getPegoutCreationRskTxHash()));
        assertSortedByComparator(withoutHash);
        assertSortedByComparator(withHash);
    }

    @Test
    void edgeCases_emptyAndSingle() {
        PegoutsWaitingForConfirmations empty = new PegoutsWaitingForConfirmations(Collections.emptySet());
        Assertions.assertTrue(empty.getEntries(ACTIVATIONS_RSKIP559_ON).isEmpty());
        Assertions.assertFalse(empty.getNextPegoutWithEnoughConfirmations(10L, 5, ACTIVATIONS_RSKIP559_ON).isPresent());
        Assertions.assertFalse(empty.getNextPegoutWithEnoughConfirmations(10L, 5, ACTIVATIONS_RSKIP559_OFF).isPresent());

        BtcTransaction onlyTx = createTransaction(7, Coin.COIN);
        PegoutsWaitingForConfirmations single = new PegoutsWaitingForConfirmations(
            new HashSet<>(Collections.singletonList(new PegoutsWaitingForConfirmations.Entry(onlyTx, 1L))));

        Optional<PegoutsWaitingForConfirmations.Entry> onSel = single.getNextPegoutWithEnoughConfirmations(10L, 5, ACTIVATIONS_RSKIP559_ON);
        Optional<PegoutsWaitingForConfirmations.Entry> offSel = single.getNextPegoutWithEnoughConfirmations(10L, 5, ACTIVATIONS_RSKIP559_OFF);
        Assertions.assertEquals(onlyTx, onSel.orElseThrow().getBtcTransaction());
        Assertions.assertEquals(onlyTx, offSel.orElseThrow().getBtcTransaction());
    }

    // ----- Activation transition (Layer 5): selection strategy flips exactly at the RSKIP559 fork height -----

    @Test
    void selectionFlipsExactlyAtRskip559ActivationHeight() {
        long activationBlock = 500L;
        ActivationConfig config = ActivationConfigsForTest.nextReleaseWithRskip559ActivatingAt(activationBlock);

        // Sanity: the rule is off one block before the fork and on at the fork height.
        Assertions.assertFalse(config.forBlock(activationBlock - 1).isActive(ConsensusRule.RSKIP559));
        Assertions.assertTrue(config.forBlock(activationBlock).isActive(ConsensusRule.RSKIP559));

        // Same pool as the off/on unit tests (current=10, min=5 -> only the block-5 pegouts are confirmed).
        Optional<PegoutsWaitingForConfirmations.Entry> before =
            set.getNextPegoutWithEnoughConfirmations(10L, 5, config.forBlock(activationBlock - 1));
        Optional<PegoutsWaitingForConfirmations.Entry> atActivation =
            set.getNextPegoutWithEnoughConfirmations(10L, 5, config.forBlock(activationBlock));

        Assertions.assertEquals(
            "53efc6f78eb9d159cfee76ec45bcffb08fd11f85c762e1eacf54e5c014da219d",
            before.orElseThrow().getBtcTransaction().getHash().toString(),
            "Before activation: HashSet-order (pre-fork) selection");
        Assertions.assertEquals(
            "fdd781c46b5ad7993b3f133e3af94b2e3cbcc8d19e443dfc6b555a1b0bac1527",
            atActivation.orElseThrow().getBtcTransaction().getHash().toString(),
            "At activation height: deterministic sorted (post-fork) selection");

        // Querying does not mutate the set; it is identical across the boundary (no entry lost/duplicated).
        Assertions.assertEquals(
            set.getEntries(config.forBlock(activationBlock - 1)).size(),
            set.getEntries(config.forBlock(activationBlock)).size());
    }

    // ----- Mechanism A (RSKIP559 off): Java-17 HashSet iteration order must be reproduced on every JVM -----
    // This golden vector pins the pre-fork (HashSet-order) selection sequence across a set whose size
    // crosses the 16 -> 32 resize boundary. It MUST hold identically on Java 17 and Java 21; a mismatch
    // means EntriesStore.setOfEntries() failed to emulate the Java-17 HashSet sizing.
    private static final List<String> PREFORK_GOLDEN_ORDER = Arrays.asList(
        // Captured on Java 17 (Zulu 17.0.15). Must reproduce identically on Java 21.
        "4c34f81d9c9457cb1d395ae10f88511fb3584d881a3b9e7b8797396ea459ac2b",
        "b0ecfeddcc2ee5f40f58c0abeabc40f859aed5e8639dd0007f313e690002b514",
        "8af5a2bffa8b359b4d3435231a447412513789bc41b2ec3566793b9c20ef7ee2",
        "0cf28a426732b021d20ae36a34626e846e6f85a9c76f847b1e369a14705219f2",
        "2cdc2ebb2f388ab1d4479f696703cccb542a7c615e2782000f60f7a3a2dd90f4",
        "edac23065ab1ebb61f52ce58b3bb9cdd3340409eab63a0f5237cdbeaef1a02fa",
        "0358b772c57699952c872e59c457e73b384ced5c4c11f77d050d53ca4e7ddbd5",
        "356c2824ce2f08ba4be11d8bda0b93a6d6538c5904051ba4d1c4d4ed4c2308b4",
        "93bd066c67f9c364c76866e583368da5300b791b6f566b4146be1f57d35de435",
        "43392d1a44c3612dc3c26a59005a99ff79637aa6361d7415e71fd698358cae00",
        "b6c9d511bc475a7f6e7038cef2e84924c29f3e43b6e1378e567b12e62d470cf1",
        "0ea1cb058fe39af2ff4a288837355d7ca380accc3e1dc7efa1b5312276dd22de",
        "741513d232d76898009645a2fdd26f48266e8daecee7a49b3b86ea6439e4d75f",
        "3f479267e4830e42d1f51b9b1310054ee42390eb79c615ca60a06e8a0b4046ec",
        "a7eb503aed07d066cf61fb56085c1e8fd330f2e74f455b049086ec037b2e1de0",
        "8fe7ee429e03e66d81950dbed40d2767f4c251f9566b280e03665de83d15cef3",
        "b25c1f7fe98d5f67b937eacae92f2057bcca7013a1d6a4ce1a5a4c57ab8613e1",
        "eeb3b9601e6ce787d9e300035739e71663a70d3a7ccbd5da8c61c8da648ad5d1",
        "25ee51c7ee37f585d8c748a757afe86e927bda29a1043a3c1b9293f5988017e8",
        "697be80f3d26a42f8baa63579a3ba385967e8995421058cdb8a8ad0eee25c8fa"
    );

    @Test
    void getNextPegoutWithEnoughConfirmations_rskip559Off_drainOrderMatchesJava17Golden() {
        PegoutsWaitingForConfirmations pegouts = new PegoutsWaitingForConfirmations(new HashSet<>());
        // 20 distinct txs (size crosses the 16 -> 32 HashSet resize boundary); vary the output amount
        // to keep each tx unique while using a known-good key for the destination address.
        for (int i = 1; i <= 20; i++) {
            pegouts.add(new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(i * 7L + 1)), 5L));
        }

        List<String> drainOrder = new ArrayList<>();
        Optional<PegoutsWaitingForConfirmations.Entry> next;
        while ((next = pegouts.getNextPegoutWithEnoughConfirmations(1000L, 5, ACTIVATIONS_RSKIP559_OFF)).isPresent()) {
            drainOrder.add(next.get().getBtcTransaction().getHash().toString());
            pegouts.removeEntry(next.get());
        }

        // To refresh the golden: temporarily print `drainOrder`, run on Java 17, and paste it into PREFORK_GOLDEN_ORDER.
        Assertions.assertEquals(PREFORK_GOLDEN_ORDER, drainOrder,
            "Pre-fork HashSet iteration order diverged from the Java-17 golden (Java 17/21 mismatch?)");
    }

    private static void assertSortedByComparator(List<PegoutsWaitingForConfirmations.Entry> entries) {
        for (int i = 1; i < entries.size(); i++) {
            Assertions.assertTrue(
                PegoutsWaitingForConfirmations.Entry.BTC_TX_COMPARATOR.compare(entries.get(i - 1), entries.get(i)) <= 0,
                "Entries must be sorted by BTC_TX_COMPARATOR");
        }
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
