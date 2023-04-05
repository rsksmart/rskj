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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PegoutsWaitingForConfirmationsTest {
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
            new PegoutsWaitingForConfirmations.Entry(createTransaction(8, Coin.CENT.times(5)), 5L)
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
        Assertions.assertNotSame(setEntries, set.getEntries());
        Assertions.assertEquals(setEntries, set.getEntries());

        Set<PegoutsWaitingForConfirmations.Entry> entryWithoutHash = new HashSet<>(Collections.singletonList(
                new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L)
        ));

        Set<PegoutsWaitingForConfirmations.Entry> entryWithHash = new HashSet<>(Collections.singletonList(
                new PegoutsWaitingForConfirmations.Entry(new BtcTransaction(config.getNetworkConstants().getBridgeConstants().getBtcParams()), 1L, PegTestUtils.createHash3(0))
        ));

        PegoutsWaitingForConfirmations transactionSetWithoutHash = new PegoutsWaitingForConfirmations(entryWithoutHash);
        PegoutsWaitingForConfirmations transactionSetWithHash = new PegoutsWaitingForConfirmations(entryWithHash);

        Set<PegoutsWaitingForConfirmations.Entry> resultCallWithoutHash = transactionSetWithoutHash.getEntriesWithoutHash();
        Assertions.assertEquals(resultCallWithoutHash, entryWithoutHash);

        Set<PegoutsWaitingForConfirmations.Entry> resultCallWithHash = transactionSetWithoutHash.getEntriesWithHash();
        Assertions.assertEquals(0, resultCallWithHash.size());

        Set<PegoutsWaitingForConfirmations.Entry> resultCallWithoutHash2 = transactionSetWithHash.getEntriesWithoutHash();
        Assertions.assertEquals(0, resultCallWithoutHash2.size());

        Set<PegoutsWaitingForConfirmations.Entry> resultCallWithHash2 = transactionSetWithHash.getEntriesWithHash();
        Assertions.assertEquals(resultCallWithHash2, entryWithHash);
    }

    @Test
    void add_nonExisting() {
        Assertions.assertFalse(set.getEntries().contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
        set.add(createTransaction(123, Coin.COIN.multiply(3)), 34L);
        Assertions.assertTrue(set.getEntries().contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
    }

    @Test
    void add_existing() {
        Assertions.assertTrue(set.getEntries().contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(150)), 32L)));
        Assertions.assertEquals(1, set.getEntries().stream().filter(e -> e.getBtcTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());
        set.add(createTransaction(2, Coin.valueOf(150)), 23L);
        Assertions.assertTrue(set.getEntries().contains(new PegoutsWaitingForConfirmations.Entry(createTransaction(2, Coin.valueOf(150)), 32L)));
        int size = set.getEntries().size();
        set.add(createTransaction(2, Coin.valueOf(150)), 23L);
        Assertions.assertEquals(set.getEntries().size(), size);
        Assertions.assertFalse(set.getEntries().contains(new PegoutsWaitingForConfirmations.Entry(createUniqueTransaction(2, Coin.valueOf(150)), 23L)));
        Assertions.assertEquals(1, set.getEntries().stream().filter(e -> e.getBtcTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_no_matches() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(9L, 5);
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    void getNextPegoutWithEnoughConfirmations_ok() {
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5);
        Assertions.assertTrue(result.isPresent());
        Assertions.assertTrue(set.removeEntry(result.get()));
        Assertions.assertFalse(set.removeEntry(result.get()));
    }

    @Test
    void getNextPegoutWithEnoughConfirmation_multipleMatch() {
        int size = set.getEntries().size();
        Optional<PegoutsWaitingForConfirmations.Entry> result = set.getNextPegoutWithEnoughConfirmations(10L, 5);
        Assertions.assertTrue(result.isPresent());
        Assertions.assertTrue(set.removeEntry(result.get()));
        Assertions.assertFalse(set.removeEntry(result.get()));
        Assertions.assertEquals(set.getEntries().size(), size-1);
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
