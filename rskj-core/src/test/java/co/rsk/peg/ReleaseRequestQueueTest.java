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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReleaseRequestQueueTest {
    private List<ReleaseRequestQueue.Entry> queueEntries;
    private ReleaseRequestQueue queue;

    @Before
    public void createQueue() {
        queueEntries = Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150)),
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.FIFTY_COINS),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.MILLICOIN),
            new ReleaseRequestQueue.Entry(mockAddress(8), Coin.CENT.times(5))
        );
        queue = new ReleaseRequestQueue(queueEntries);
    }

    @Test
    public void entryEquals() {
        ReleaseRequestQueue.Entry e1 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e2 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e3 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(149));
        ReleaseRequestQueue.Entry e4 = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e5 = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(151));
        ReleaseRequestQueue.Entry e6 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));
        ReleaseRequestQueue.Entry e7 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));
        ReleaseRequestQueue.Entry e8 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), null);
        ReleaseRequestQueue.Entry e9 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), null);

        Assert.assertEquals(e1, e2);
        Assert.assertNotEquals(e1, e3);
        Assert.assertNotEquals(e1, e4);
        Assert.assertNotEquals(e1, e5);
        Assert.assertEquals(e6, e7);
        Assert.assertEquals(e8, e9);
        Assert.assertNotEquals(e6, e8);
    }

    @Test
    public void entryGetters() {
        ReleaseRequestQueue.Entry entry = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(100));

        Assert.assertEquals(mockAddress(5), entry.getDestination());
        Assert.assertEquals(Coin.valueOf(100), entry.getAmount());
    }

    @Test
    public void entriesCopy() {
        Assert.assertNotSame(queueEntries, queue.getEntries());
        Assert.assertEquals(queueEntries, queue.getEntries());

        List<ReleaseRequestQueue.Entry> entry = Collections.singletonList(new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150)));
        ReleaseRequestQueue queueWithoutHash = new ReleaseRequestQueue(entry);

        List<ReleaseRequestQueue.Entry> resultCallWithoutHash = queueWithoutHash.getEntriesWithoutHash();
        Assert.assertEquals(resultCallWithoutHash, entry);

        List<ReleaseRequestQueue.Entry> resultCallWithHash = queueWithoutHash.getEntriesWithHash();
        Assert.assertEquals(resultCallWithHash, new ArrayList<>());

        ReleaseRequestQueue queueWithHashIndex0 = new ReleaseRequestQueue(entry, 0);
        List<ReleaseRequestQueue.Entry> resultCallWithoutHash2 = queueWithHashIndex0.getEntriesWithoutHash();
        Assert.assertEquals(0, resultCallWithoutHash2.size());

        List<ReleaseRequestQueue.Entry> resultCallWithoutHash2b = queueWithHashIndex0.getEntriesWithHash();
        Assert.assertEquals(resultCallWithoutHash2b, entry);

        ReleaseRequestQueue queueWithHashIndex1 = new ReleaseRequestQueue(entry, 1);
        List<ReleaseRequestQueue.Entry> resultCallWithoutHash3 = queueWithHashIndex1.getEntriesWithoutHash();
        Assert.assertEquals(resultCallWithoutHash3, entry);

        List<ReleaseRequestQueue.Entry> resultCallWithoutHash4 = queueWithHashIndex1.getEntriesWithHash();
        Assert.assertEquals(0, resultCallWithoutHash4.size());
    }

    @Test
    public void add() {
        Assert.assertFalse(queue.getEntries().contains(new ReleaseRequestQueue.Entry(mockAddress(10), Coin.valueOf(10))));
        queue.add(mockAddress(10), Coin.valueOf(10), null);
        Assert.assertTrue(queue.getEntries().contains(new ReleaseRequestQueue.Entry(mockAddress(10), Coin.valueOf(10))));
    }

    @Test
    public void adding_entry_without_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(new ArrayList<>());
        queue.add(mockAddress(2), Coin.valueOf(150));

        Assert.assertEquals(queue.getEntriesWithoutHash().size(), 1);
        Assert.assertEquals(queue.getEntriesWithHash().size(), 0);
    }

    @Test
    public void adding_entry_with_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(new ArrayList<>());
        queue.add(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));

        Assert.assertEquals(queue.getEntriesWithoutHash().size(), 0);
        Assert.assertEquals(queue.getEntriesWithHash().size(), 1);
    }

    @Test
    public void process() {
        class Indexer {
            public int index = 0;
        }

        Indexer idx = new Indexer();
        queue.process(30, entry -> {
            Assert.assertEquals(entry, queueEntries.get(idx.index));
            return idx.index++ % 2 == 0;
        });
        Assert.assertEquals(5, idx.index);
        Assert.assertEquals(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.MILLICOIN)
        ), queue.getEntries());
    }

    @Test
    public void processUpToMaxIterationEntries() {
        class Indexer {
            public int index = 0;
        }

        Indexer idx = new Indexer();
        queue.process(3, entry -> {
            Assert.assertEquals(entry, queueEntries.get(idx.index));
            return idx.index++ % 2 == 0;
        });
        Assert.assertEquals(3, idx.index);
        Assert.assertEquals(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.MILLICOIN), // this wasn't processed
            new ReleaseRequestQueue.Entry(mockAddress(8), Coin.CENT.times(5)), // this wasn't processed
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN) // this was sent to the back
        ), queue.getEntries());
    }

    private Address mockAddress(int pk) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
    }
}
