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
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class ReleaseTransactionSetTest {
    private Set<ReleaseTransactionSet.Entry> setEntries;
    private ReleaseTransactionSet set;

    @Before
    public void createSet() {
        setEntries = new HashSet<>(Arrays.asList(
            new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 32L),
            new ReleaseTransactionSet.Entry(createTransaction(5, Coin.COIN), 100L),
            new ReleaseTransactionSet.Entry(createTransaction(4, Coin.FIFTY_COINS), 7L),
            new ReleaseTransactionSet.Entry(createTransaction(3, Coin.MILLICOIN), 10L),
            new ReleaseTransactionSet.Entry(createTransaction(8, Coin.CENT.times(5)), 5L)
        ));
        set = new ReleaseTransactionSet(setEntries);
    }

    @Test
    public void entryEquals() {
        ReleaseTransactionSet.Entry e1 = new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 15L);
        ReleaseTransactionSet.Entry e2 = new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 15L);
        ReleaseTransactionSet.Entry e3 = new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(149)), 14L);
        ReleaseTransactionSet.Entry e4 = new ReleaseTransactionSet.Entry(createTransaction(5, Coin.valueOf(230)), 15L);

        Assert.assertEquals(e1, e2);
        Assert.assertNotEquals(e1, e3);
        Assert.assertNotEquals(e1, e4);
    }

    @Test
    public void entryGetters() {
        ReleaseTransactionSet.Entry entry = new ReleaseTransactionSet.Entry(createTransaction(5, Coin.valueOf(100)), 7L);

        Assert.assertEquals(createTransaction(5, Coin.valueOf(100)), entry.getTransaction());
        Assert.assertEquals(7L, entry.getRskBlockNumber().longValue());
    }

    @Test
    public void entryComparators() {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        ReleaseTransactionSet.Entry e1 = new ReleaseTransactionSet.Entry(mockTxSerialize("aa"), 7L);
        ReleaseTransactionSet.Entry e2 = new ReleaseTransactionSet.Entry(mockTxSerialize("aa"), 7L);
        ReleaseTransactionSet.Entry e3 = new ReleaseTransactionSet.Entry(mockTxSerialize("aa"), 8L);
        ReleaseTransactionSet.Entry e4 = new ReleaseTransactionSet.Entry(mockTxSerialize("bb"), 7L);
        ReleaseTransactionSet.Entry e5 = new ReleaseTransactionSet.Entry(mockTxSerialize("99"), 7L);

        Assert.assertTrue(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR.compare(e1, e2) == 0);
        Assert.assertTrue(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR.compare(e1, e3) == 0);
        Assert.assertTrue(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR.compare(e1, e4) < 0);
        Assert.assertTrue(ReleaseTransactionSet.Entry.BTC_TX_COMPARATOR.compare(e1, e5) > 0);
    }

    @Test
    public void entriesCopy() {
        Assert.assertNotSame(setEntries, set.getEntries());
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void add_nonExisting() {
        Assert.assertFalse(set.getEntries().contains(new ReleaseTransactionSet.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
        set.add(createTransaction(123, Coin.COIN.multiply(3)), 34L);
        Assert.assertTrue(set.getEntries().contains(new ReleaseTransactionSet.Entry(createTransaction(123, Coin.COIN.multiply(3)), 34L)));
    }

    @Test
    public void add_existing() {
        Assert.assertTrue(set.getEntries().contains(new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 32L)));
        Assert.assertEquals(1, set.getEntries().stream().filter(e -> e.getTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());
        set.add(createTransaction(2, Coin.valueOf(150)), 23L);
        Assert.assertTrue(set.getEntries().contains(new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 32L)));
        Assert.assertFalse(set.getEntries().contains(new ReleaseTransactionSet.Entry(createTransaction(2, Coin.valueOf(150)), 23L)));
        Assert.assertEquals(1, set.getEntries().stream().filter(e -> e.getTransaction().equals(createTransaction(2, Coin.valueOf(150)))).count());
    }

    @Test
    public void sliceWithConfirmations_withLimit_none() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(9L, 5, Optional.of(1));
        Assert.assertEquals(0, result.size());
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void sliceWithConfirmations_withLimit_singleMatch() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(10L, 5, Optional.of(2));
        Assert.assertEquals(1, result.size());
        setEntries.remove(new ReleaseTransactionSet.Entry(createTransaction(8, Coin.CENT.times(5)), 5L));
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void sliceWithConfirmations_withLimit_multipleMatch() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(15L, 5, Optional.of(2));
        Assert.assertEquals(2, result.size());
        Iterator<BtcTransaction> resultIterator = result.iterator();
        while (resultIterator.hasNext()) {
            BtcTransaction itemTx = resultIterator.next();
            ReleaseTransactionSet.Entry item = setEntries.stream().filter(e -> e.getTransaction().equals(itemTx)).findFirst().get();
            Assert.assertTrue(15L - item.getRskBlockNumber() >= 5);
            setEntries.remove(item);
        }
        Assert.assertEquals(3, set.getEntries().size());
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void sliceWithConfirmations_noLimit_none() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(9L, 5, Optional.empty());
        Assert.assertEquals(0, result.size());
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void sliceWithConfirmations_noLimit_singleMatch() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(10L, 5, Optional.empty());
        Assert.assertEquals(1, result.size());
        setEntries.remove(new ReleaseTransactionSet.Entry(createTransaction(8, Coin.CENT.times(5)), 5L));
        Assert.assertEquals(setEntries, set.getEntries());
    }

    @Test
    public void sliceWithConfirmations_noLimit_multipleMatch() {
        Set<BtcTransaction> result = set.sliceWithConfirmations(15L, 5, Optional.empty());
        Assert.assertEquals(3, result.size());
        Iterator<BtcTransaction> resultIterator = result.iterator();
        while (resultIterator.hasNext()) {
            BtcTransaction itemTx = resultIterator.next();
            ReleaseTransactionSet.Entry item = setEntries.stream().filter(e -> e.getTransaction().equals(itemTx)).findFirst().get();
            Assert.assertTrue(15L - item.getRskBlockNumber() >= 5);
            setEntries.remove(item);
        }
        Assert.assertEquals(2, set.getEntries().size());
        Assert.assertEquals(setEntries, set.getEntries());
    }

    private BtcTransaction createTransaction(int toPk, Coin value) {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BtcTransaction input = new BtcTransaction(params);
        input.addOutput(Coin.FIFTY_COINS, BtcECKey.fromPrivate(BigInteger.valueOf(123456)).toAddress(params));

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
