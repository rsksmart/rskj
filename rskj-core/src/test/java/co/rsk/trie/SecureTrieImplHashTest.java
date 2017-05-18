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

import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class SecureTrieImplHashTest {
    private static byte[] emptyHash = makeEmptyHash();

    @Test
    public void getNotNullHashOnEmptyTrie() {
        Trie trie = new TrieImpl(true);

        Assert.assertNotNull(trie.getHash());
    }

    @Test
    public void getHashAs32BytesOnEmptyTrie() {
        Trie trie = new TrieImpl(true);

        Assert.assertEquals(32, trie.getHash().length);
    }

    @Test
    public void emptyTriesHasTheSameHash() {
        Trie trie1 = new TrieImpl(true);
        Trie trie2 = new TrieImpl(true);
        Trie trie3 = new TrieImpl(true);

        Assert.assertArrayEquals(trie1.getHash(), trie1.getHash());
        Assert.assertArrayEquals(trie1.getHash(), trie2.getHash());
        Assert.assertArrayEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void emptyHashForEmptyTrie() {
        Trie trie = new TrieImpl(true);

        Assert.assertArrayEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrie() {
        Trie trie = new TrieImpl(true);

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        Assert.assertFalse(Arrays.equals(emptyHash, trie.getHash()));
    }

    @Test
    public void triesWithSameKeyValuesHaveSameHash() {
        Trie trie1 = new TrieImpl(true).put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        Trie trie2 = new TrieImpl(true).put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertArrayEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new TrieImpl(true).put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        Trie trie2 = new TrieImpl(true).put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assert.assertArrayEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new TrieImpl(true).put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "baz".getBytes())
                .put("baz".getBytes(), "foo".getBytes());
        Trie trie2 = new TrieImpl(true).put("bar".getBytes(), "baz".getBytes())
                .put("baz".getBytes(), "foo".getBytes())
                .put("foo".getBytes(), "bar".getBytes());
        Trie trie3 = new TrieImpl(true).put("baz".getBytes(), "foo".getBytes())
                .put("bar".getBytes(), "baz".getBytes())
                .put("foo".getBytes(), "bar".getBytes());

        Assert.assertArrayEquals(trie1.getHash(), trie2.getHash());
        Assert.assertArrayEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyValuesHaveDifferentHashes() {
        Trie trie1 = new TrieImpl(true).put("foo", "bar".getBytes())
                .put("bar", "42".getBytes());
        Trie trie2 = new TrieImpl(true).put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertFalse(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    public static byte[] makeEmptyHash() {
        return sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    }
}
