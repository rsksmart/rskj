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
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class SecureTrieHashTest {
    private static Keccak256 emptyHash = makeEmptyHash();

    @Test
    public void getNotNullHashOnEmptyTrie() {
        TrieImpl trie = new TrieImpl(true);

        Assert.assertNotNull(trie.getHash().getBytes());
    }

    @Test
    public void getHashAs32BytesOnEmptyTrie() {
        TrieImpl trie = new TrieImpl(true);

        Assert.assertEquals(32, trie.getHash().getBytes().length);
    }

    @Test
    public void emptyTriesHasTheSameHash() {
        TrieImpl trie1 = new TrieImpl(true);
        TrieImpl trie2 = new TrieImpl(true);
        TrieImpl trie3 = new TrieImpl(true);

        Assert.assertEquals(trie1.getHash(), trie1.getHash());
        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void emptyHashForEmptyTrie() {
        TrieImpl trie = new TrieImpl(true);

        Assert.assertEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrie() {
        TrieImpl trie = new TrieImpl(true);

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        Assert.assertNotEquals(emptyHash, trie.getHash());
    }

    @Test
    public void triesWithSameKeyValuesHaveSameHash() {
        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        TrieImpl trie2 = new TrieImpl(true)
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesHaveSameHash() {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", value1)
                .put("bar", value2);
        TrieImpl trie2 = new TrieImpl(true)
                .put("foo", value1)
                .put("bar", value2);

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        TrieImpl trie2 = new TrieImpl(true)
                .put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", value1)
                .put("bar", value2);
        TrieImpl trie2 = new TrieImpl(true)
                .put("bar", value2)
                .put("foo", value1);

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl(true)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "baz".getBytes())
                .put("baz".getBytes(), "foo".getBytes());
        TrieImpl trie2 = new TrieImpl(true)
                .put("bar".getBytes(), "baz".getBytes())
                .put("baz".getBytes(), "foo".getBytes())
                .put("foo".getBytes(), "bar".getBytes());
        TrieImpl trie3 = new TrieImpl(true)
                .put("baz".getBytes(), "foo".getBytes())
                .put("bar".getBytes(), "baz".getBytes())
                .put("foo".getBytes(), "bar".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(150);
        byte[] value3 = TrieValueTest.makeValue(200);

        TrieImpl trie1 = new TrieImpl(true)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2)
                .put("baz".getBytes(), value3);
        TrieImpl trie2 = new TrieImpl(true)
                .put("bar".getBytes(), value2)
                .put("baz".getBytes(), value3)
                .put("foo".getBytes(), value1);
        TrieImpl trie3 = new TrieImpl(true)
                .put("baz".getBytes(), value3)
                .put("bar".getBytes(), value2)
                .put("foo".getBytes(), value1);

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyValuesHaveDifferentHashes() {
        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", "bar".getBytes())
                .put("bar", "42".getBytes());
        TrieImpl trie2 = new TrieImpl(true)
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyLongValuesHaveDifferentHashes() {
        TrieImpl trie1 = new TrieImpl(true)
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(110));
        TrieImpl trie2 = new TrieImpl(true)
                .put("foo", TrieValueTest.makeValue(120))
                .put("bar", TrieValueTest.makeValue(130));

        Assert.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
