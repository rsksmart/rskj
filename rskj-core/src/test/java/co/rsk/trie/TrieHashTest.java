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
 * Created by ajlopez on 11/01/2017.
 */
public class TrieHashTest {
    private static Keccak256 emptyHash = makeEmptyHash();

    @Test
    public void getNotNullHashOnEmptyTrie() {
        TrieImpl trie = new TrieImpl();

        Assert.assertNotNull(trie.getHash().getBytes());
    }

    @Test
    public void getHashAs32BytesOnEmptyTrie() {
        TrieImpl trie = new TrieImpl();

        Assert.assertEquals(32, trie.getHash().getBytes().length);
    }

    @Test
    public void emptyTriesHasTheSameHash() {
        TrieImpl trie1 = new TrieImpl();
        TrieImpl trie2 = new TrieImpl();
        TrieImpl trie3 = new TrieImpl();

        Assert.assertEquals(trie1.getHash(), trie1.getHash());
        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void emptyHashForEmptyTrie() {
        TrieImpl trie = new TrieImpl();

        Assert.assertEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrie() {
        TrieImpl trie = new TrieImpl();

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        Assert.assertNotEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrieWithLongValue() {
        TrieImpl trie = new TrieImpl();

        trie = trie.put("foo".getBytes(), TrieValueTest.makeValue(100));

        Assert.assertNotEquals(emptyHash, trie.getHash());
    }

    @Test
    public void triesWithSameKeyValuesHaveSameHash() {
        TrieImpl trie1 = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        TrieImpl trie2 = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesHaveSameHash() {
        TrieImpl trie1 = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", TrieValueTest.makeValue(100));
        TrieImpl trie2 = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", TrieValueTest.makeValue(100));

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        TrieImpl trie2 = new TrieImpl()
                .put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));
        TrieImpl trie2 = new TrieImpl()
                .put("bar", TrieValueTest.makeValue(200))
                .put("foo", TrieValueTest.makeValue(100));

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes())
                .put("baz", "foo".getBytes());
        TrieImpl trie2 = new TrieImpl()
                .put("bar", "baz".getBytes())
                .put("baz", "foo".getBytes())
                .put("foo", "bar".getBytes());
        TrieImpl trie3 = new TrieImpl()
                .put("baz", "foo".getBytes())
                .put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200))
                .put("baz", TrieValueTest.makeValue(300));
        TrieImpl trie2 = new TrieImpl()
                .put("bar", TrieValueTest.makeValue(200))
                .put("baz", TrieValueTest.makeValue(300))
                .put("foo", TrieValueTest.makeValue(100));
        TrieImpl trie3 = new TrieImpl()
                .put("baz", TrieValueTest.makeValue(300))
                .put("bar", TrieValueTest.makeValue(200))
                .put("foo", TrieValueTest.makeValue(100));

        Assert.assertEquals(trie1.getHash(), trie2.getHash());
        Assert.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyValuesHaveDifferentHashes() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", "bar".getBytes())
                .put("bar", "42".getBytes());
        TrieImpl trie2 = new TrieImpl()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assert.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyLongValuesHaveDifferentHashes() {
        TrieImpl trie1 = new TrieImpl()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));
        TrieImpl trie2 = new TrieImpl()
                .put("foo", TrieValueTest.makeValue(150))
                .put("bar", TrieValueTest.makeValue(250));

        Assert.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
