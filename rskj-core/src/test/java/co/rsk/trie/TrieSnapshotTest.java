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

import co.rsk.crypto.Keccak256;
import org.ethereum.datasource.HashMapDB;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 05/04/2017.
 */
public class TrieSnapshotTest {
    @Test
    public void getSnapshotToTrie() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(store);

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        Keccak256 hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), "foo".getBytes());

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));

        Trie snapshot = trie.getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }


    @Test
    public void getSnapshotToTrieWithLongValues() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(store);

        trie = trie.put("foo".getBytes(), TrieValueTest.makeValue(100));

        Keccak256 hash = trie.getHash();

        trie.save();

        trie = trie.put("bar".getBytes(), TrieValueTest.makeValue(200));

        Assert.assertNotNull(trie.get("foo".getBytes()));
        Assert.assertNotNull(trie.get("bar".getBytes()));

        Trie snapshot = trie.getSnapshotTo(hash);

        Assert.assertNotNull(snapshot);
        Assert.assertEquals(hash, snapshot.getHash());

        Assert.assertNotNull(snapshot.get("foo".getBytes()));
        Assert.assertNull(snapshot.get("bar".getBytes()));
    }

    @Test
    public void getSnapshotToTheSameTrie() {
        TrieStore store = mock(TrieStore.class);
        Trie trie = new Trie(store);
        trie = trie.put("key", "value".getBytes());
        trie.save();

        Trie snapshotTrie = trie.getSnapshotTo(trie.getHash());

        Assert.assertThat(snapshotTrie, is(trie));
        verify(store, never()).retrieve(any(byte[].class));
    }
}
