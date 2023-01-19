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

package co.rsk.trie.message;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieValueTest;
import org.ethereum.crypto.Keccak256Helper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static co.rsk.trie.message.TrieWasabiMessageTest.LONG_VALUE;

/**
 * Created by ajlopez on 11/01/2017.
 */
public class TrieOrchidMessageTest {

    @Test
    public void emptyTrieToMessageOrchid() {
        Trie trie = new Trie();

        byte[] message = trie.toMessageOrchid(false);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(6, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(0, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(0, message[5]);
    }

    @Test
    public void trieWithValueToMessageOrchid() {
        Trie trie = new Trie().put(new byte[0], new byte[]{1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(10, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(0, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(0, message[5]);
        Assertions.assertEquals(1, message[6]);
        Assertions.assertEquals(2, message[7]);
        Assertions.assertEquals(3, message[8]);
        Assertions.assertEquals(4, message[9]);
    }

    @Test
    public void trieWithLongValueToMessageOrchid() {
        Trie trie = new Trie().put(new byte[0], TrieValueTest.makeValue(33));

        byte[] message = trie.toMessageOrchid(false);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(38, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(2, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(0, message[5]);

        byte[] valueHash = trie.getValueHash().getBytes();

        for (int k = 0; k < valueHash.length; k++) {
            Assertions.assertEquals(valueHash[k], message[k + 6]);
        }
    }

    @Test
    public void trieWithSubtrieAndNoValueToMessageOrchid() {
        Trie trie = new Trie().put(new byte[]{0x2}, new byte[]{1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(11, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(0, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);

        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(8, message[5]);

        Assertions.assertEquals(2, message[6]);

        Assertions.assertEquals(1, message[7]);
        Assertions.assertEquals(2, message[8]);
        Assertions.assertEquals(3, message[9]);
        Assertions.assertEquals(4, message[10]);
    }

    @Test
    public void trieWithSubtriesAndNoValueToMessageOrchid() {
        Trie trie = new Trie().put(new byte[]{0x2}, new byte[]{1, 2, 3, 4})
                .put(new byte[]{0x12}, new byte[]{1, 2, 3, 4});

        byte[] message = trie.toMessageOrchid(false);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(6 + 1 + 2 * Keccak256Helper.DEFAULT_SIZE_BYTES, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(0, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(3, message[3]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(3, message[5]);
        Assertions.assertEquals(0, message[6]);
    }

    @Test
    public void emptyTrieToMessageOrchidSecure() {
        Trie trie = new Trie();

        byte[] message = trie.toMessageOrchid(true);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(6, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(1, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(0, message[5]);
    }

    @Test
    public void trieWithValueToMessageOrchidSecure() {
        byte[] oldKey = new byte[0];
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessageOrchid(true);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(42, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(1, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(1, message[4]);
        Assertions.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++)
            Assertions.assertEquals(key[k], message[6 + k]);

        Assertions.assertEquals(1, message[34 + 4]);
        Assertions.assertEquals(2, message[34 + 5]);
        Assertions.assertEquals(3, message[34 + 6]);
        Assertions.assertEquals(4, message[34 + 7]);
    }

    @Test
    public void trieWithLongValueToMessageOrchidSecure() {
        byte[] oldKey = new byte[0];
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, TrieValueTest.makeValue(LONG_VALUE));

        byte[] message = trie.toMessageOrchid(true);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(70, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(3, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(1, message[4]);
        Assertions.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++)
            Assertions.assertEquals(key[k], message[6 + k]);

        byte[] valueHash = trie.getValueHash().getBytes();

        for (int k = 0; k < valueHash.length; k++) {
            Assertions.assertEquals(valueHash[k], message[k + 38]);
        }
    }

    @Test
    public void trieWithSubtrieAndNoValueToMessageOrchidSecure() {
        byte[] oldKey = new byte[]{0x02};
        byte[] key = Keccak256Helper.keccak256(oldKey);

        Trie trie = new Trie().put(key, new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessageOrchid(true);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(42, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(1, message[1]);
        Assertions.assertEquals(0, message[2]);
        Assertions.assertEquals(0, message[3]);
        Assertions.assertEquals(1, message[4]);
        Assertions.assertEquals(0, message[5]);

        for (int k = 0; k < key.length; k++)
            Assertions.assertEquals(key[k], message[6 + k]);

        Assertions.assertEquals(1, message[34 + 4]);
        Assertions.assertEquals(2, message[34 + 5]);
        Assertions.assertEquals(3, message[34 + 6]);
        Assertions.assertEquals(4, message[34 + 7]);
    }

    @Test
    public void trieWithSubtriesAndNoValueToMessageOrchidSecure() {
        Trie trie = new Trie()
                .put(Keccak256Helper.keccak256(new byte[] { 0x2 }), new byte[] { 1, 2, 3, 4 })
                .put(Keccak256Helper.keccak256(new byte[] { 0x12 }), new byte[] { 1, 2, 3, 4 });

        byte[] message = trie.toMessageOrchid(true);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(6 + 2 * Keccak256Helper.DEFAULT_SIZE_BYTES, message.length);
        Assertions.assertEquals(2, message[0]);
        Assertions.assertEquals(1, message[1]);
        Assertions.assertEquals(0, message[4]);
        Assertions.assertEquals(0, message[5]);
    }
}
