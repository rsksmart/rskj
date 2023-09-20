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

package co.rsk.bridge;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class ReleaseRequestQueueTest {
    private List<ReleaseRequestQueue.Entry> queueEntries;
    private ReleaseRequestQueue queue;

    @BeforeEach
    void createQueue() {
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
    void entryEquals() {
        ReleaseRequestQueue.Entry e1 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e2 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e3 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(149));
        ReleaseRequestQueue.Entry e4 = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(150));
        ReleaseRequestQueue.Entry e5 = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(151));
        ReleaseRequestQueue.Entry e6 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));
        ReleaseRequestQueue.Entry e7 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));
        ReleaseRequestQueue.Entry e8 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), null);
        ReleaseRequestQueue.Entry e9 = new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150), null);

        assertEquals(e1, e2);
        Assertions.assertNotEquals(e1, e3);
        Assertions.assertNotEquals(e1, e4);
        Assertions.assertNotEquals(e1, e5);
        assertEquals(e6, e7);
        assertEquals(e8, e9);
        Assertions.assertNotEquals(e6, e8);
    }

    @Test
    void entryGetters() {
        ReleaseRequestQueue.Entry entry = new ReleaseRequestQueue.Entry(mockAddress(5), Coin.valueOf(100));

        assertEquals(mockAddress(5), entry.getDestination());
        assertEquals(Coin.valueOf(100), entry.getAmount());
    }

    @Test
    void entriesCopy() {
        Assertions.assertNotSame(queueEntries, queue.getEntries());
        assertEquals(queueEntries, queue.getEntries());

        List<ReleaseRequestQueue.Entry> entry = Collections.singletonList(new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150)));
        ReleaseRequestQueue queueWithoutHash = new ReleaseRequestQueue(entry);

        List<ReleaseRequestQueue.Entry> resultCallWithoutHash = queueWithoutHash.getEntriesWithoutHash();
        assertEquals(entry, resultCallWithoutHash);

        List<ReleaseRequestQueue.Entry> resultCallWithHash = queueWithoutHash.getEntriesWithHash();
        assertEquals(new ArrayList<>(), resultCallWithHash);
    }

    @Test
    void get_entries_without_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(Collections.emptyList());

        assertEquals(0, queue.getEntriesWithoutHash().size());

        queue.add(mock(Address.class), Coin.COIN, mock(Keccak256.class));
        assertEquals(0, queue.getEntriesWithoutHash().size());

        queue.add(mock(Address.class), Coin.COIN);
        assertEquals(1, queue.getEntriesWithoutHash().size());
    }

    @Test
    void get_entries_with_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(Collections.emptyList());

        assertEquals(0, queue.getEntriesWithHash().size());

        queue.add(mock(Address.class), Coin.COIN);
        assertEquals(0, queue.getEntriesWithHash().size());

        queue.add(mock(Address.class), Coin.COIN, mock(Keccak256.class));
        assertEquals(1, queue.getEntriesWithHash().size());

    }

    @Test
    void add() {
        Assertions.assertFalse(queue.getEntries().contains(new ReleaseRequestQueue.Entry(mockAddress(10), Coin.valueOf(10))));
        queue.add(mockAddress(10), Coin.valueOf(10), null);
        Assertions.assertTrue(queue.getEntries().contains(new ReleaseRequestQueue.Entry(mockAddress(10), Coin.valueOf(10))));
    }

    @Test
    void adding_entry_without_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(new ArrayList<>());
        queue.add(mockAddress(2), Coin.valueOf(150));

        assertEquals(1, queue.getEntriesWithoutHash().size());
        assertEquals(0, queue.getEntriesWithHash().size());
    }

    @Test
    void adding_entry_with_hash() {
        ReleaseRequestQueue queue = new ReleaseRequestQueue(new ArrayList<>());
        queue.add(mockAddress(2), Coin.valueOf(150), PegTestUtils.createHash3(0));

        assertEquals(0, queue.getEntriesWithoutHash().size());
        assertEquals(1, queue.getEntriesWithHash().size());
    }

    @Test
    void process() {
        class Indexer {
            public int index = 0;
        }

        Indexer idx = new Indexer();
        queue.process(30, entry -> {
            assertEquals(entry, queueEntries.get(idx.index));
            return idx.index++ % 2 == 0;
        });
        assertEquals(5, idx.index);
        assertEquals(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.MILLICOIN)
        ), queue.getEntries());
    }

    @Test
    void processUpToMaxIterationEntries() {
        class Indexer {
            public int index = 0;
        }

        Indexer idx = new Indexer();
        queue.process(3, entry -> {
            assertEquals(entry, queueEntries.get(idx.index));
            return idx.index++ % 2 == 0;
        });
        assertEquals(3, idx.index);
        assertEquals(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.MILLICOIN), // this wasn't processed
            new ReleaseRequestQueue.Entry(mockAddress(8), Coin.CENT.times(5)), // this wasn't processed
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN) // this was sent to the back
        ), queue.getEntries());
    }

    @Test
    void test_removeEntries_all() {
        queue.removeEntries(queueEntries);
        Assertions.assertTrue(queue.getEntries().isEmpty());
    }

    @Test
    void test_removeEntries_part() {
        List<ReleaseRequestQueue.Entry> queueEntriesToRemove = Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.valueOf(150)),
            new ReleaseRequestQueue.Entry(mockAddress(5), Coin.COIN),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.FIFTY_COINS)
        );
        queue.removeEntries(queueEntriesToRemove);
        Assertions.assertEquals(2, queue.getEntries().size());
        Assertions.assertEquals(queueEntries.get(3), queue.getEntries().get(0));
        Assertions.assertEquals(queueEntries.get(4), queue.getEntries().get(1));
    }

    @Test
    void test_ReleaseRequestQueue_equals() {
        ReleaseRequestQueue emptyReleaseRequestQueue = new ReleaseRequestQueue(
            Collections.emptyList()
        );

        Object nonReleaseRequestQueueAsObject = new ArrayList<>();
        Object emptyReleaseRequestQueueAsObject = emptyReleaseRequestQueue;

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5), PegTestUtils.createHash3(0)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3))
        ));

        ReleaseRequestQueue releaseRequestQueueCopy = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5), PegTestUtils.createHash3(0)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3))
        ));

        ReleaseRequestQueue differentOrderReleaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3)),
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4))
        ));

        ReleaseRequestQueue differentReleaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(10), Coin.COIN.multiply(10)),
            new ReleaseRequestQueue.Entry(mockAddress(9), Coin.COIN.multiply(9)),
            new ReleaseRequestQueue.Entry(mockAddress(8), Coin.COIN.multiply(8)),
            new ReleaseRequestQueue.Entry(mockAddress(7), Coin.COIN.multiply(7)),
            new ReleaseRequestQueue.Entry(mockAddress(6), Coin.COIN.multiply(6)),
            new ReleaseRequestQueue.Entry(mockAddress(6), Coin.COIN.multiply(6))
        ));

        assertNotEquals(null, emptyReleaseRequestQueue);
        assertNotEquals(null, emptyReleaseRequestQueue);
        assertNotEquals(emptyReleaseRequestQueue, nonReleaseRequestQueueAsObject);
        assertEquals(emptyReleaseRequestQueue, emptyReleaseRequestQueueAsObject);

        assertNotEquals(releaseRequestQueue, emptyReleaseRequestQueue);
        assertNotEquals(releaseRequestQueueCopy, emptyReleaseRequestQueue);
        assertEquals(emptyReleaseRequestQueue, emptyReleaseRequestQueue);

        assertEquals(releaseRequestQueueCopy, releaseRequestQueue);
        assertEquals(releaseRequestQueue, releaseRequestQueueCopy);
        assertEquals(releaseRequestQueue, releaseRequestQueue);

        assertNotEquals(differentOrderReleaseRequestQueue, releaseRequestQueue);

        assertNotEquals(differentReleaseRequestQueue, releaseRequestQueue);
        assertNotEquals(differentReleaseRequestQueue, differentOrderReleaseRequestQueue);
    }

    @Test
    void test_ReleaseRequestQueue_hashcode() {
        ReleaseRequestQueue emptyReleaseRequestQueue = new ReleaseRequestQueue(
            Collections.emptyList()
        );

        Object emptyReleaseRequestQueueAsObject = emptyReleaseRequestQueue;

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5), PegTestUtils.createHash3(0)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3))
        ));

        ReleaseRequestQueue releaseRequestQueueCopy = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5), PegTestUtils.createHash3(0)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3))
        ));

        ReleaseRequestQueue differentOrderReleaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(4), Coin.COIN.multiply(3)),
            new ReleaseRequestQueue.Entry(mockAddress(2), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(mockAddress(3), Coin.COIN.multiply(4))
        ));

        ReleaseRequestQueue differentReleaseRequestQueue = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(mockAddress(10), Coin.COIN.multiply(10)),
            new ReleaseRequestQueue.Entry(mockAddress(9), Coin.COIN.multiply(9)),
            new ReleaseRequestQueue.Entry(mockAddress(8), Coin.COIN.multiply(8)),
            new ReleaseRequestQueue.Entry(mockAddress(7), Coin.COIN.multiply(7)),
            new ReleaseRequestQueue.Entry(mockAddress(6), Coin.COIN.multiply(6)),
            new ReleaseRequestQueue.Entry(mockAddress(6), Coin.COIN.multiply(6))
        ));

        assertEquals(emptyReleaseRequestQueue.hashCode(), emptyReleaseRequestQueueAsObject.hashCode());

        assertNotEquals(releaseRequestQueue.hashCode(), emptyReleaseRequestQueue.hashCode());
        assertNotEquals(releaseRequestQueueCopy.hashCode(), emptyReleaseRequestQueue.hashCode());
        assertEquals(emptyReleaseRequestQueue.hashCode(), emptyReleaseRequestQueue.hashCode());

        assertEquals(releaseRequestQueueCopy.hashCode(), releaseRequestQueue.hashCode());
        assertEquals(releaseRequestQueue.hashCode(), releaseRequestQueueCopy.hashCode());
        assertEquals(releaseRequestQueue.hashCode(), releaseRequestQueue.hashCode());

        assertNotEquals(differentOrderReleaseRequestQueue.hashCode(), releaseRequestQueue.hashCode());

        assertNotEquals(differentReleaseRequestQueue.hashCode(), releaseRequestQueue.hashCode());
        assertNotEquals(differentReleaseRequestQueue.hashCode(), differentOrderReleaseRequestQueue.hashCode());
    }

    private Address mockAddress(int pk) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
    }
}
