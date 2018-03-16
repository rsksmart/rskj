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

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 08/02/2017.
 */
public class PartialMerkleTreeTest {
    @Test
    public void getTreeFromTrieKey() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        Assert.assertNotNull(tree);
    }

    @Test
    public void getTreeHashFromTrieWithOneKey() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        Assert.assertArrayEquals(trie.getHash().getBytes(), tree.getHash("bar".getBytes()));
    }

    @Test
    public void getTreeHashFromTrieWithTwoKeys() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", "foo".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        Assert.assertArrayEquals(trie.getHash().getBytes(), tree.getHash("bar".getBytes()));
    }

    @Test
    public void getTreeHashFromTrieWithThreeKeys() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes())
                .put("bar", "foo".getBytes())
                .put("baz", "bar".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        Assert.assertArrayEquals(trie.getHash().getBytes(), tree.getHash("bar".getBytes()));
    }

    @Test
    public void getTreeFromOneHundredKeyValues() {
        Trie trie = new TrieImpl(false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", (k + "").getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("50".getBytes());

        Assert.assertArrayEquals(trie.getHash().getBytes(), tree.getHash("50".getBytes()));
    }

    @Test
    public void toMessage() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        byte[] message = tree.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(1, message[0]);
        Assert.assertEquals(0, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(trie.toMessage().length - 3, message[4]);
        Assert.assertEquals(message[4] + 5, message.length);
    }

    @Test
    public void fromMessage() {
        Trie trie = new TrieImpl().put("foo", "bar".getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("foo".getBytes());

        byte[] message = tree.toMessage();

        PartialMerkleTree tree2 = PartialMerkleTree.fromMessage(message);

        Assert.assertNotNull(tree2);
        Assert.assertArrayEquals(tree.getHash("bar".getBytes()), tree2.getHash("bar".getBytes()));
        Assert.assertArrayEquals(trie.getHash().getBytes(), tree2.getHash("bar".getBytes()));

        Assert.assertArrayEquals(tree.toMessage(), tree2.toMessage());
    }

    @Test
    public void toMessagefromMessagegetHashUsingOneHundredKeyValue() {
        Trie trie = new TrieImpl(false);

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", (k + "").getBytes());

        PartialMerkleTree tree = trie.getPartialMerkleTree("50".getBytes());

        byte[] message = tree.toMessage();

        PartialMerkleTree tree2 = PartialMerkleTree.fromMessage(message);

        Assert.assertNotNull(tree2);
        Assert.assertArrayEquals(tree.getHash("50".getBytes()), tree2.getHash("50".getBytes()));
        Assert.assertArrayEquals(trie.getHash().getBytes(), tree2.getHash("50".getBytes()));

        Assert.assertArrayEquals(tree.toMessage(), tree2.toMessage());
    }
}
