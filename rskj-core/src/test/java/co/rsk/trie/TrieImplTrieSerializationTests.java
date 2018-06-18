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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

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

    public static Keccak256 makeEmptyHash() {
        return new Keccak256(HashUtil.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
