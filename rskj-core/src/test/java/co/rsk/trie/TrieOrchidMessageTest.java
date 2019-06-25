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
import org.junit.Test;

/** Created by ajlopez on 11/01/2017. */
public class TrieOrchidMessageTest {
    @Test
    public void emptyTrieToMessage() {
        Trie trie = new Trie();

        byte[] message = trie.toMessageOrchid(false);

        Assert.assertNotNull(message);
        Assert.assertEquals(6, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(0, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
    }

    @Test
    public void trieWithValueToMessage() {
        Trie trie = new Trie().put(new byte[0], new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assert.assertNotNull(message);
        Assert.assertEquals(10, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(0, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
        Assert.assertEquals(1, message[6]);
        Assert.assertEquals(2, message[7]);
        Assert.assertEquals(3, message[8]);
        Assert.assertEquals(4, message[9]);
    }

    @Test
    public void trieWithLongValueToMessage() {
        Trie trie = new Trie().put(new byte[0], TrieValueTest.makeValue(33));

        byte[] message = trie.toMessageOrchid(false);

        Assert.assertNotNull(message);
        Assert.assertEquals(38, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(2, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);

        byte[] valueHash = trie.getValueHash().getBytes();

        for (int k = 0; k < valueHash.length; k++) {
            Assert.assertEquals(valueHash[k], message[k + 6]);
        }
    }

    @Test
    public void trieWithSubtrieAndNoValueToMessage() {
        Trie trie = new Trie().put(new byte[] {0x2}, new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assert.assertNotNull(message);
        Assert.assertEquals(11, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(0, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);

        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(8, message[5]);

        Assert.assertEquals(2, message[6]);

        Assert.assertEquals(1, message[7]);
        Assert.assertEquals(2, message[8]);
        Assert.assertEquals(3, message[9]);
        Assert.assertEquals(4, message[10]);
    }

    @Test
    public void trieWithSubtriesAndNoValueToMessage() {
        Trie trie =
                new Trie()
                        .put(new byte[] {0x2}, new byte[] {1, 2, 3, 4})
                        .put(new byte[] {0x12}, new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assert.assertNotNull(message);
        Assert.assertEquals(6 + 1 + 2 * Keccak256Helper.DEFAULT_SIZE_BYTES, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(0, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(3, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(3, message[5]);
        Assert.assertEquals(0, message[6]);
    }

    @Test
    public void emptyTrieToMessageSecure() {
        Trie trie = new Trie();

        byte[] message = trie.toMessageOrchid(true);

        Assert.assertNotNull(message);
        Assert.assertEquals(6, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
    }

    @Test
    public void trieWithValueToMessageSecure() {
        byte[] oldKey = new byte[0];
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(true);

        Assert.assertNotNull(message);
        Assert.assertEquals(42, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++) Assert.assertEquals(key[k], message[6 + k]);

        Assert.assertEquals(1, message[34 + 4]);
        Assert.assertEquals(2, message[34 + 5]);
        Assert.assertEquals(3, message[34 + 6]);
        Assert.assertEquals(4, message[34 + 7]);
    }

    @Test
    public void trieWithLongValueToMessageSecure() {
        byte[] oldKey = new byte[0];
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, TrieValueTest.makeValue(33));

        byte[] message = trie.toMessageOrchid(true);

        Assert.assertNotNull(message);
        Assert.assertEquals(70, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(3, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++) Assert.assertEquals(key[k], message[6 + k]);

        byte[] valueHash = trie.getValueHash().getBytes();

        for (int k = 0; k < valueHash.length; k++) {
            Assert.assertEquals(valueHash[k], message[k + 38]);
        }
    }

    @Test
    public void trieWithSubtrieAndNoValueToMessageSecure() {
        byte[] oldKey = new byte[] {0x02};
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(true);

        Assert.assertNotNull(message);
        Assert.assertEquals(42, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[2]);
        Assert.assertEquals(0, message[3]);
        Assert.assertEquals(1, message[4]);
        Assert.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++) Assert.assertEquals(key[k], message[6 + k]);

        Assert.assertEquals(1, message[34 + 4]);
        Assert.assertEquals(2, message[34 + 5]);
        Assert.assertEquals(3, message[34 + 6]);
        Assert.assertEquals(4, message[34 + 7]);
    }

    @Test
    public void trieWithSubtriesAndNoValueToMessageSecure() {
        Trie trie =
                new Trie()
                        .put(Keccak256Helper.keccak256(new byte[] {0x2}), new byte[] {1, 2, 3, 4})
                        .put(Keccak256Helper.keccak256(new byte[] {0x12}), new byte[] {1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(true);

        Assert.assertNotNull(message);
        Assert.assertEquals(6 + 2 * Keccak256Helper.DEFAULT_SIZE_BYTES, message.length);
        Assert.assertEquals(2, message[0]);
        Assert.assertEquals(1, message[1]);
        Assert.assertEquals(0, message[4]);
        Assert.assertEquals(0, message[5]);
    }
}
