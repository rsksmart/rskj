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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 11/01/2017.
 */
public class TrieHashTest {
    private static Keccak256 emptyHash = makeEmptyHash();

    @Test
    public void getNotNullHashOnEmptyTrie() {
        Trie trie = new Trie();

        Assertions.assertNotNull(trie.getHash().getBytes());
    }

    @Test
    public void getHashAs32BytesOnEmptyTrie() {
        Trie trie = new Trie();

        Assertions.assertEquals(32, trie.getHash().getBytes().length);
    }

    @Test
    public void emptyTriesHasTheSameHash() {
        Trie trie1 = new Trie();
        Trie trie2 = new Trie();
        Trie trie3 = new Trie();

        Assertions.assertEquals(trie1.getHash(), trie1.getHash());
        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
        Assertions.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void emptyHashForEmptyTrie() {
        Trie trie = new Trie();

        Assertions.assertEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrie() {
        Trie trie = new Trie();

        trie = trie.put("foo".getBytes(), "bar".getBytes());

        Assertions.assertNotEquals(emptyHash, trie.getHash());
    }

    @Test
    public void nonEmptyHashForNonEmptyTrieWithLongValue() {
        Trie trie = new Trie();

        trie = trie.put("foo".getBytes(), TrieValueTest.makeValue(100));

        Assertions.assertNotEquals(emptyHash, trie.getHash());
    }

    @Test
    public void triesWithSameKeyValuesHaveSameHash() {
        Trie trie1 = new Trie().put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        Trie trie2 = new Trie().put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesHaveSameHash() {
        Trie trie1 = new Trie().put("foo", "bar".getBytes())
                .put("bar", TrieValueTest.makeValue(100));
        Trie trie2 = new Trie().put("foo", "bar".getBytes())
                .put("bar", TrieValueTest.makeValue(100));

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new Trie()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());
        Trie trie2 = new Trie()
                .put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new Trie()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));
        Trie trie2 = new Trie()
                .put("bar", TrieValueTest.makeValue(200))
                .put("foo", TrieValueTest.makeValue(100));

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new Trie()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes())
                .put("baz", "foo".getBytes());
        Trie trie2 = new Trie()
                .put("bar", "baz".getBytes())
                .put("baz", "foo".getBytes())
                .put("foo", "bar".getBytes());
        Trie trie3 = new Trie()
                .put("baz", "foo".getBytes())
                .put("bar", "baz".getBytes())
                .put("foo", "bar".getBytes());

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
        Assertions.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void threeTriesWithSameKeyLongValuesInsertedInDifferentOrderHaveSameHash() {
        Trie trie1 = new Trie()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200))
                .put("baz", TrieValueTest.makeValue(300));
        Trie trie2 = new Trie()
                .put("bar", TrieValueTest.makeValue(200))
                .put("baz", TrieValueTest.makeValue(300))
                .put("foo", TrieValueTest.makeValue(100));
        Trie trie3 = new Trie()
                .put("baz", TrieValueTest.makeValue(300))
                .put("bar", TrieValueTest.makeValue(200))
                .put("foo", TrieValueTest.makeValue(100));

        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
        Assertions.assertEquals(trie3.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyValuesHaveDifferentHashes() {
        Trie trie1 = new Trie()
                .put("foo", "bar".getBytes())
                .put("bar", "42".getBytes());
        Trie trie2 = new Trie()
                .put("foo", "bar".getBytes())
                .put("bar", "baz".getBytes());

        Assertions.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    @Test
    public void triesWithDifferentKeyLongValuesHaveDifferentHashes() {
        Trie trie1 = new Trie()
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));
        Trie trie2 = new Trie()
                .put("foo", TrieValueTest.makeValue(150))
                .put("bar", TrieValueTest.makeValue(250));

        Assertions.assertNotEquals(trie1.getHash(), trie2.getHash());
    }

    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
