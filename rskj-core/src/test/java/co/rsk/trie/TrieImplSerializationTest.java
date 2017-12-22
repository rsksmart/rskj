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

import org.ethereum.crypto.SHA3Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 06/04/2017.
 */
public class TrieImplSerializationTest {
    private static byte[] emptyHash = makeEmptyHash();

    @Test
    public void serializeEmptyTrie() throws IOException {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false);

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[SHA3Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(emptyHash, root);
    }

    @Test
    public void serializeTrieWithTwoValues() throws IOException {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[SHA3Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(trie.getHash(), root);
    }

    @Test
    public void serializeTrieWithTwoLongValues() throws IOException {
        byte[] value1 = TrieImplValueTest.makeValue(100);
        byte[] value2 = TrieImplValueTest.makeValue(200);

        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Assert.assertNotNull(bytes);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());

        byte[] root = new byte[SHA3Helper.DEFAULT_SIZE_BYTES];
        ostream.read(root);

        Assert.assertArrayEquals(trie.getHash(), root);
    }

    @Test
    public void deserializeEmptyTrie() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false);

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.trieSize());
        Assert.assertArrayEquals(emptyHash, result.getHash());
    }

    @Test
    public void deserializeTrieWithTwoValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeTrieWithTwoLongValues() {
        byte[] value1 = TrieImplValueTest.makeValue(100);
        byte[] value2 = TrieImplValueTest.makeValue(200);

        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        Assert.assertArrayEquals(value1, result.get("foo".getBytes()));
        Assert.assertArrayEquals(value2, result.get("bar".getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithTwoValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithTwoLongValues() {
        byte[] value1 = TrieImplValueTest.makeValue(100);
        byte[] value2 = TrieImplValueTest.makeValue(200);

        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)
                .put("foo".getBytes(), value1)
                .put("bar".getBytes(), value2);

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        Assert.assertArrayEquals(value1, result.get("foo".getBytes()));
        Assert.assertArrayEquals(value2, result.get("bar".getBytes()));
    }

    @Test
    public void deserializeTrieWithOneHundredValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeTrieWithOneHundredLongValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), TrieImplValueTest.makeValue(k + 200));

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(TrieImplValueTest.makeValue(k + 200), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithOneHundredValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), true);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithOneHundredLongValues() {
        Trie trie = new TrieImpl(new TrieStoreImpl(new HashMapDB()), true);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), TrieImplValueTest.makeValue(k + 200));

        byte[] bytes = trie.serialize();

        Trie result = TrieImpl.deserialize(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertFalse(Arrays.equals(emptyHash, result.getHash()));

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(TrieImplValueTest.makeValue(k + 200), result.get(("foo" + k).getBytes()));
    }

    public static byte[] makeEmptyHash() {
        return sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    }
}
