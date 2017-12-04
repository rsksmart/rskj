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
import org.junit.Test;

import static org.ethereum.crypto.SHA3Helper.sha3;

/**
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImplTest {
    @Test
    public void hasStore() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", "bar".getBytes());

        Assert.assertTrue(trie.hasStore());
    }

    @Test
    public void hasNoStore() {
        Trie trie = new TrieImpl(null, false).put("foo", "bar".getBytes());

        Assert.assertFalse(trie.hasStore());
    }

    @Test
    public void saveTrieNode() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", "bar".getBytes());

        store.save(trie);

        Assert.assertEquals(1, map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(1, store.getSaveCount());
    }

    @Test
    public void saveAndRetrieveTrieNodeWith32BytesKey() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put(sha3("foo".getBytes()), "bar".getBytes());

        store.save(trie);

        Assert.assertEquals(1, map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(1, store.getSaveCount());

        Trie newTrie = store.retrieve(trie.getHash());

        Assert.assertNotNull(newTrie);
        Assert.assertEquals(1, newTrie.trieSize());
        Assert.assertNotNull(newTrie.get(sha3("foo".getBytes())));
    }

    @Test
    public void saveAndRetrieveTrieNodeWith33BytesValue() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        byte[] key = sha3("foo".getBytes());
        byte[] value = new byte[33];

        Trie trie = new TrieImpl(store, false).put(key, value);

        store.save(trie);

        Assert.assertEquals(2, map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(2, store.getSaveCount());

        Trie newTrie = store.retrieve(trie.getHash());

        Assert.assertNotNull(newTrie);
        Assert.assertEquals(1, newTrie.trieSize());
        Assert.assertNotNull(newTrie.get(key));
        Assert.assertArrayEquals(value, newTrie.get(key));
    }

    @Test
    public void saveFullTrie() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", "bar".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize(), map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(trie.trieSize(), store.getSaveCount());
    }

    @Test
    public void saveFullTrieWithLongValue() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", new byte[100]);

        trie.save();

        Assert.assertEquals(trie.trieSize() + 1, map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(trie.trieSize() + 1, store.getSaveCount());
    }

    @Test
    public void saveFullTrieWithTwoLongValues() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false)
                .put("foo", new byte[100])
                .put("bar", new byte[200]);

        trie.save();

        Assert.assertEquals(trie.trieSize() + 2, map.keys().size());
        Assert.assertNotNull(map.get(trie.getHash()));
        Assert.assertArrayEquals(trie.toMessage(), map.get(trie.getHash()));

        Assert.assertEquals(trie.trieSize() + 2, store.getSaveCount());
    }

    @Test
    public void saveFullTrieTwice() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", "bar".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize(), store.getSaveCount());

        trie.save();

        Assert.assertEquals(trie.trieSize(), store.getSaveCount());
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgainUsingBinaryTrie() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("foo", "bar".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize(), store.getSaveCount());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize() + 1, store.getSaveCount());
    }

    @Test
    public void saveFullTrieUpdateAndSaveAgainUsingArity16() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(16, store, false).put("foo", "bar".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize(), store.getSaveCount());

        trie = trie.put("foo", "bar2".getBytes());

        trie.save();

        Assert.assertEquals(trie.trieSize() + 1, store.getSaveCount());
    }

    @Test
    public void retrieveUnknownHash() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Assert.assertNull(store.retrieve(new byte[] { 0x01, 0x02, 0x03, 0x04 }));
    }

    @Test
    public void retrieveTrieByHash() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false).put("bar", "foo".getBytes())
                .put("foo", "bar".getBytes());

        trie.save();
        int size = trie.trieSize();

        Trie trie2 = store.retrieve(trie.getHash());

        Assert.assertEquals(1, store.getRetrieveCount());

        Assert.assertEquals(size, trie2.trieSize());

        Assert.assertEquals(size, store.getRetrieveCount());
    }

    @Test
    public void serializeDeserializeTrieStore() {
        HashMapDB map = new HashMapDB();
        TrieStoreImpl store = new TrieStoreImpl(map);

        Trie trie = new TrieImpl(store, false)
                .put("foo", "bar".getBytes())
                .put("bar", "foo".getBytes());

        byte[] root = trie.getHash();

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
