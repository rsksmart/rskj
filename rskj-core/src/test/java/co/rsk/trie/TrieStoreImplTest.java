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

import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImplTest {

    private HashMapDB map;
    private TrieStoreImpl store;

    @Before
    public void setUp() {
        this.map = spy(new HashMapDB());
        this.store = new TrieStoreImpl(map);
    }

    @Test
    public void hasStore() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        Assert.assertTrue(trie.hasStore());
    }

    @Test
    public void hasNoStore() {
        Trie trie = new Trie(null, false).put("foo", "bar".getBytes());

        Assert.assertFalse(trie.hasStore());
    }

    @Test
    public void saveTrieNode() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveAndRetrieveTrieNodeWith32BytesKey() {
        Trie trie = new Trie(store, false).put(Keccak256Helper.keccak256("foo".getBytes()), "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verifyNoMoreInteractions(map);

        Trie newTrie = store.retrieve(trie.getHash().getBytes());

        Assert.assertNotNull(newTrie);
        Assert.assertEquals(1, newTrie.trieSize());
        Assert.assertNotNull(newTrie.get(Keccak256Helper.keccak256("foo".getBytes())));
    }

    @Test
    public void saveAndRetrieveTrieNodeWith33BytesValue() {
        byte[] key = Keccak256Helper.keccak256("foo".getBytes());
        byte[] value = new byte[33];

        Trie trie = new Trie(store, false).put(key, value);

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash(), trie.getValue());
        verifyNoMoreInteractions(map);

        Trie newTrie = store.retrieve(trie.getHash().getBytes());

        Assert.assertNotNull(newTrie);
        Assert.assertEquals(1, newTrie.trieSize());
        Assert.assertNotNull(newTrie.get(key));
        Assert.assertArrayEquals(value, newTrie.get(key));
    }

    @Test
    public void saveFullTrie() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithLongValue() {
        Trie trie = new Trie(store, false).put("foo", TrieValueTest.makeValue(100));

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash(), trie.getValue());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithTwoLongValues() {
        Trie trie = new Trie(store, false)
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));

        trie.save();

        verify(map, times(trie.trieSize() + 2)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieTwice() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());

        trie.save();

        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgainUsingBinaryTrie() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgain() {
        Trie trie = new Trie(store, false).put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void retrieveUnknownHash() {
        Assert.assertNull(store.retrieve(new byte[] { 0x01, 0x02, 0x03, 0x04 }));
    }

    @Test
    public void retrieveTrieByHash() {
        Trie trie = new Trie(store, false).put("bar", "foo".getBytes())
                .put("foo", "bar".getBytes());

        trie.save();
        int size = trie.trieSize();

        Trie trie2 = store.retrieve(trie.getHash().getBytes());

        verify(map, times(1)).get(any());

        Assert.assertEquals(size, trie2.trieSize());

        verify(map, times(size)).get(any());
    }

    @Test
    public void serializeDeserializeTrieStore() {
        Trie trie = new Trie(store, false)
                .put("foo", "bar".getBytes())
                .put("bar", "foo".getBytes());

        byte[] root = trie.getHash().getBytes();

        trie.save();

        byte[] bytes = store.serialize();

        TrieStoreImpl newStore = TrieStoreImpl.deserialize(bytes);

        Assert.assertNotNull(newStore);

        Trie result = newStore.retrieve(root);

        Assert.assertEquals(trie.trieSize(), result.trieSize());

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo"));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar"));
    }
}
