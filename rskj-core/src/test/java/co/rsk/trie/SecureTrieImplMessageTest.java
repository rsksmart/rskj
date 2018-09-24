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

import org.ethereum.crypto.Keccak256Helper;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.ethereum.crypto.Keccak256Helper.keccak256;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class SecureTrieImplMessageTest {
    @Test
    public void emptyTrieToMessage() {
        TrieImpl trie = new TrieImpl(true);

        byte[] message = trie.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(6, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
    }

    @Ignore
    // Tries are not secure anymore. What is secure is the repository that contains
    // them. This test is therefore useless, but could be ported to work with
    // A Repository
    public void trieWithValueToMessage() {
        byte[] key = new byte[0];
        byte[] newKey = Keccak256Helper.keccak256(key);

        Trie trie = new TrieImpl(true).put(key, new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(42, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < newKey.length; k++)
            Assert.assertEquals(newKey[k], message[6 + k]);

        Assert.assertEquals(1, message[34 + 4]);
        Assert.assertEquals(2, message[34 + 5]);
        Assert.assertEquals(3, message[34 + 6]);
        Assert.assertEquals(4, message[34 + 7]);
    }

    @Ignore
    // Tries are not secure anymore. What is secure is the repository that contains
    // them. This test is therefore useless, but could be ported to work with
    // A Repository
    public void trieWithLongValueToMessage() {
        byte[] key = new byte[0];
        byte[] newKey = Keccak256Helper.keccak256(key);

        Trie trie = new TrieImpl(true).put(key, TrieImplValueTest.makeValue(33));

        byte[] message = trie.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(70, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(3, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < newKey.length; k++)
            Assert.assertEquals(newKey[k], message[6 + k]);

        byte[] valueHash = trie.getValueHash();

        for (int k = 0; k < valueHash.length; k++) {
            Assert.assertEquals(valueHash[k], message[k + 38]);
        }
    }

    @Ignore
    // Tries are not secure anymore. What is secure is the repository that contains
    // them. This test is therefore useless, but could be ported to work with
    // A Repository
    public void trieWithSubtrieAndNoValueToMessage() {
        byte[] key = new byte[] { 0x02 };
        byte[] newKey = Keccak256Helper.keccak256(key);

        Trie trie = new TrieImpl(true).put(key, new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(42, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < newKey.length; k++)
            Assert.assertEquals(newKey[k], message[6 + k]);

        Assert.assertEquals(1, message[34 + 4]);
        Assert.assertEquals(2, message[34 + 5]);
        Assert.assertEquals(3, message[34 + 6]);
        Assert.assertEquals(4, message[34 + 7]);
    }

    @Ignore
    // Tries are not secure anymore. What is secure is the repository that contains
    // them. This test is therefore useless, but could be ported to work with
    // A Repository
    public void trieWithSubtriesAndNoValueToMessage() {
        Trie trie = new TrieImpl(true).put(new byte[] { 0x2 }, new byte[] { 1, 2, 3, 4 })
                .put(new byte[] { 0x12 }, new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessage();

        Assert.assertNotNull(message);
        Assert.assertEquals(6 + 2 * Keccak256Helper.DEFAULT_SIZE_BYTES, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
    }
}
