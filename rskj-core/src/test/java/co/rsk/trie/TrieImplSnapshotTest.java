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

package co.rsk.trie;

import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import static org.ethereum.crypto.HashUtil.keccak256;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 05/04/2017.
 */
public class TrieImplSnapshotTest {
    private static byte[] emptyHash = makeEmptyHash();

    @Test
    public void getSnapshotToEmptyTrie() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new TrieImpl(store, false);

        Trie snapshot = trie.getSnapshotTo(trie.getHash());

        Assert.assertNotNull(snapshot);
        Assert.assertEquals(trie.trieSize(), snapshot.trieSize());
        Assert.assertArrayEquals(emptyHash, snapshot.getHash());
    }

    @Test
    public void getSnapshotToTrie() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new TrieImpl(store, false);

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        byte[] hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), "foo".getBytes());

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));

        Trie snapshot = trie.getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertArrayEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }


    @Test
    public void getSnapshotToTrieWithLongValues() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new TrieImpl(store, false);

        trie = trie.put("foo".getBytes(), TrieImplValueTest.makeValue(100));

        byte[] hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), TrieImplValueTest.makeValue(200));

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));

        Trie snapshot = trie.getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertArrayEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }

    @Test
    public void getSnapshotToTrieUsingDeserializedTrie() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new TrieImpl(store, false);

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        byte[] hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), "foo".getBytes());

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));

        Trie snapshot = TrieImpl.deserialize(trie.serialize()).getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertArrayEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }

    @Test
    public void getSnapshotToTrieUsingDeserializedTrieWithLongValues() {
        byte[] value1 = TrieImplValueTest.makeValue(100);
        byte[] value2 = TrieImplValueTest.makeValue(200);

        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new TrieImpl(store, false);

        trie = trie.put("foo".getBytes(), value1);

        byte[] hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), value2);

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertArrayEquals(value1, trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));
        Assert.assertArrayEquals(value2, trie.get("bar".getBytes()));

        Trie snapshot = TrieImpl.deserialize(trie.serialize()).getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertArrayEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertArrayEquals(value1, snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }

    public static byte[] makeEmptyHash() {
        return HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    }
}
