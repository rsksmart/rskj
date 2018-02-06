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

<<<<<<< 13e30355b6aa6cbaee004919fd0eb800a4e0cbce
import co.rsk.crypto.Keccak256;
=======
>>>>>>> Rename sha3 too keccak256
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

<<<<<<< 13e30355b6aa6cbaee004919fd0eb800a4e0cbce
=======
import static org.ethereum.crypto.HashUtil.keccak256;
>>>>>>> Rename sha3 too keccak256
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 07/04/2017.
 */
public class TrieImplTrieSerializationTests {
    private static Keccak256 emptyHash = makeEmptyHash();

    @Test
    public void serializeEmptyTrie() throws IOException {
        TrieImpl trie = new TrieImpl();

        byte[] bytes = trie.serializeTrie();

        Assert.assertNotNull(bytes);

        byte[] message = trie.toMessage();

        int length = Short.BYTES * 2 + Integer.BYTES * 2 + message.length;

        Assert.assertEquals(length, bytes.length);

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());
        Assert.assertEquals(0, ostream.readShort());
        Assert.assertEquals(message.length, ostream.readInt());
        Assert.assertEquals(message.length, ostream.readInt());
    }

    @Test
    public void serializeTrieWithTwoValues() throws IOException {
        Trie trie = new TrieImpl()
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Assert.assertNotNull(bytes);

        byte[] message = trie.toMessage();

        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream ostream = new DataInputStream(bstream);

        Assert.assertEquals(0, ostream.readShort());
        Assert.assertEquals(2, ostream.readShort());
        Assert.assertEquals(message.length, ostream.readInt());
    }

    @Test
    public void deserializeEmptyTrie() {
        Trie trie = new TrieImpl();

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Trie result = TrieImpl.deserializeTrie(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.trieSize());
        Assert.assertEquals(emptyHash, result.getHash());
    }

    @Test
    public void deserializeTrieWithTwoValues() {
        Trie trie = new TrieImpl()
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Trie result = TrieImpl.deserializeTrie(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithTwoValues() {
        Trie trie = new TrieImpl(null, true)
                .put("foo".getBytes(), "bar".getBytes())
                .put("bar".getBytes(), "foo".getBytes());

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Trie result = TrieImpl.deserializeTrie(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        Assert.assertArrayEquals("bar".getBytes(), result.get("foo".getBytes()));
        Assert.assertArrayEquals("foo".getBytes(), result.get("bar".getBytes()));
    }

    @Test
    public void deserializeTrieWithOneHundredValues() {
        Trie trie = new TrieImpl();

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Trie result = TrieImpl.deserializeTrie(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

    @Test
    public void deserializeSecureTrieWithOneHundredValues() {
        Trie trie = new TrieImpl(null, true);

        for (int k = 0; k < 100; k++)
            trie = trie.put(("foo" + k).getBytes(), ("bar" + k).getBytes());

        byte[] bytes = ((TrieImpl)trie).serializeTrie();

        Trie result = TrieImpl.deserializeTrie(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.trieSize(), result.trieSize());
        Assert.assertNotEquals(emptyHash, result.getHash());

        for (int k = 0; k < 100; k++)
            Assert.assertArrayEquals(("bar" + k).getBytes(), result.get(("foo" + k).getBytes()));
    }

<<<<<<< 13e30355b6aa6cbaee004919fd0eb800a4e0cbce
    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
=======
    public static byte[] makeEmptyHash() {
        return HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY));
>>>>>>> Rename sha3 too keccak256
    }
}
