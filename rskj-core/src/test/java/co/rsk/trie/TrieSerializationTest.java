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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 06/04/2017.
 */
public class TrieSerializationTest {
    private static Keccak256 emptyHash = makeEmptyHash();

    @Test
    public void serializeEmptyTrie() throws IOException {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false);

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(emptyHash.getBytes(), root);
    }

    @Test
    public void serializeTrieWithTwoValues() throws IOException {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(trie.getHash().getBytes(), root);
    }

    @Test
    public void serializeTrieWithTwoLongValues() throws IOException {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(trie.getHash().getBytes(), root);
    }

    @Test
    public void deserializeEmptyTrie() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false);

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.trieSize());
        Assert.assertEquals(emptyHash, result.getHash());
    }

    @Test
    public void deserializeTrieWithTwoValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeTrieWithTwoLongValues() {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals(value1, result.get("foo".getBytes()));
        Assert.assertArrayEquals(value2, result.get("bar".getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithTwoValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithTwoLongValues() {
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals(value1, result.get("foo".getBytes()));
        Assert.assertArrayEquals(value2, result.get("bar".getBytes()));
    }

    @Test
    public void deserializeTrieWithOneHundredValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeTrieWithOneHundredLongValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), TrieValueTest.makeValue(k + 200));

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(TrieValueTest.makeValue(k + 200), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithOneHundredValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), true);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithOneHundredLongValues() {
        Trie trie = new Trie(new TrieStoreImpl(new HashMapDB()), true);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), TrieValueTest.makeValue(k + 200));

        byte[] bytes = trie.serialize();

        Trie result = Trie.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(TrieValueTest.makeValue(k + 200), result.get(("foo" + k).getBytes()));
    }

    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
