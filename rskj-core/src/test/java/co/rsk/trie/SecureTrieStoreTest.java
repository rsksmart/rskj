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

import org.ethereum.datasource.HashMapDB;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class SecureTrieStoreTest {

    private HashMapDB map;
    private TrieStoreImpl store;

    @Before
    public void setUp() {
        this.map = spy(new HashMapDB());
        this.store = new TrieStoreImpl(map);
    }

    @Test
    public void saveTrieNode() {
        Trie trie = new Trie(store, true).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveAndRetrieveTrieNodeWith33BytesValue() {
        byte[] key = "foo".getBytes();
        byte[] value = new byte[33];

        Trie trie = new Trie(store, true).put(key, value);

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
        Trie trie = new Trie(store, true).put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithLongValue() {
        Trie trie = new Trie(store, true)
                .put("foo", TrieValueTest.makeValue(100));

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash(), trie.getValue());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithTwoLongValues() {
        Trie trie = new Trie(store, true)
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));

        trie.save();

        verify(map, times(trie.trieSize() + 2)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieTwice() {
        Trie trie = new Trie(store, true)
                .put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());

        trie.save();

        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithLongValueTwice() {
        Trie trie = new Trie(store, true)
                .put("foo", TrieValueTest.makeValue(100));

        trie.save();

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash(), trie.getValue());

        trie.save();

        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgainUsingBinaryTrie() {
        Trie trie = new Trie(store, true)
                .put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithLongValueUpdateAndSaveAgainUsingBinaryTrie() {
        Trie trie = new Trie(store, true)
                .put("foo", TrieValueTest.makeValue(100));

        trie.save();

        verify(map, times(2)).put(any(), any());

        trie = trie.put("foo", TrieValueTest.makeValue(200));

        trie.save();

        verify(map, times(4)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgain() {
        Trie trie = new Trie(store, true)
                .put("foo", "bar".getBytes());

        trie.save();

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void saveFullTrieWithLongValueUpdateAndSaveAgain() {
        Trie trie = new Trie(store, true)
                .put("foo", TrieValueTest.makeValue(100));

        trie.save();

        verify(map, times(2)).put(any(), any());

        trie = trie.put("foo", TrieValueTest.makeValue(200));

        trie.save();

        verify(map, times(4)).put(any(), any());
        verifyNoMoreInteractions(map);
    }

    @Test
    public void retrieveUnknownHash() {
        Assert.assertNull(store.retrieve(new byte[] { 0x01, 0x02, 0x03, 0x04 }));
    }

    @Test
    public void retrieveTrieByHash() {
        Trie trie = new Trie(store, true)
                .put("bar", "foo".getBytes())
                .put("foo", "bar".getBytes());


        trie.save();
        int size = trie.trieSize();

        Trie trie2 = store.retrieve(trie.getHash().getBytes());

        verify(map, times(1)).get(any());

        Assert.assertEquals(size, trie2.trieSize());

        verify(map, times(size)).get(any());
    }

    @Test
    public void retrieveTrieWithLongValuesByHash() {
        Trie trie = new Trie(store, true)
                .put("bar", TrieValueTest.makeValue(100))
                .put("foo", TrieValueTest.makeValue(200));

        trie.save();

        store.retrieve(trie.getHash().getBytes());

        verify(map, times(1)).get(any());
    }
}
