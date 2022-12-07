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

import co.rsk.core.types.ints.Uint24;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieValueTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.bouncycastle.util.encoders.Hex.decode;

public class TrieWasabiMessageTest {

    // a long value exceeds 32 bytes
    public static final int LONG_VALUE = 32 + 1;

    @Test
    public void emptyTrieToMessageWasabiToMessageWasabi() {
        Trie trie = new Trie();

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);

        // flags (1 byte)
        Assertions.assertEquals(1, message.length);

        // check flags
        Assertions.assertEquals(0b01000000, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithLeftEmbeddedChildToMessageWasabi() {
        Trie trie = new Trie()
                .put(decode("0a"), new byte[]{1})
                .put(decode("0a00"), new byte[]{7});

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);
        Assertions.assertEquals(10, message.length);

        // check flags (version + lshared + left + leftEmbedded) => 0b01000000 | 0b00010000 | 0b00001000 | 0b00000010
        Assertions.assertEquals(0b01011010, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithLeftChildToMessageWasabi() {
        Trie trie = new Trie()
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a01"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a0110"), TrieValueTest.makeValue(LONG_VALUE - 1));

        Assertions.assertTrue(trie.getLeft().getNode().isPresent());
        Assertions.assertTrue(!trie.getLeft().isEmbeddable());
        Assertions.assertTrue(!trie.getRight().getNode().isPresent());

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);
        Assertions.assertEquals(68, message.length);

        // check flags (version + lshared + left) => 0b01000000 | 0b00010000 | 0b00001000
        Assertions.assertEquals(0b01011000, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithEmbeddedLeftRightChildsToMessageWasabi() {
        Trie trie = new Trie()
                .put(decode("1a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("1a10"), TrieValueTest.makeValue(LONG_VALUE - 1));

        Assertions.assertTrue(trie.getLeft().isEmbeddable());
        Assertions.assertTrue(trie.getLeft().getNode().get().isTerminal());
        Assertions.assertTrue(!trie.getRight().isEmbeddable());
        Assertions.assertTrue(!trie.getRight().getNode().get().isTerminal());

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);
        Assertions.assertEquals(72, message.length);

        // check flags (version + lshared + left + leftEmbedded + right) => 0b01000000 | 0b00010000 | 0b00001000 | 0b00000010 | 0b00000100
        Assertions.assertEquals(0b01011110, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithLeftEmbeddedRightChildsToMessageWasabi() {
        Trie trie = new Trie()
                .put(decode("1a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a10"), TrieValueTest.makeValue(LONG_VALUE - 1));

        Assertions.assertTrue(!trie.getLeft().isEmbeddable());
        Assertions.assertTrue(!trie.getLeft().getNode().get().isTerminal());
        Assertions.assertTrue(trie.getRight().isEmbeddable());
        Assertions.assertTrue(trie.getRight().getNode().get().isTerminal());

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);
        Assertions.assertEquals(72, message.length);

        // check flags (version + lshared + left + right + rightEmbedded) => 0b01000000 | 0b00010000 | 0b00001000 | 0b00000100 | 0b00000001
        Assertions.assertEquals(0b01011101, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithBothEmbeddedChildsToMessageWasabi() {
        Trie trie = new Trie()
                .put(decode("0a"), new byte[]{1})
                .put(decode("10"), new byte[]{9});

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);
        Assertions.assertEquals(14, message.length);

        // check flags (version + lshared + left + leftEmbedded + right + rightEmbedded) => 0b01000000 | 0b00010000 | 0b00001000 | 0b00000010
        Assertions.assertEquals(0b01011111, message[0]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithValueToMessageWasabi() {
        Trie trie = new Trie().put(new byte[0], new byte[]{1, 2, 3, 4});

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);

        // flags (1 byte) + value (4 bytes)
        Assertions.assertEquals(5, message.length);

        // check flags
        Assertions.assertEquals(0b01000000, message[0]);

        // check value
        Assertions.assertEquals(1, message[1]);
        Assertions.assertEquals(2, message[2]);
        Assertions.assertEquals(3, message[3]);
        Assertions.assertEquals(4, message[4]);

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test
    public void trieWithLongValueToMessageWasabi() {
        Trie trie = new Trie().put(new byte[0], TrieValueTest.makeValue(LONG_VALUE));

        byte[] message = trie.toMessage();

        Assertions.assertNotNull(message);

        // flags (1 byte) + valueHash (32 bytes) + valueLength (3 bytes)
        Assertions.assertEquals(36, message.length);

        // check flags => 0b01100000 | 0b00100000
        Assertions.assertEquals(0b01100000, message[0]);

        // check encoded valueHash
        byte[] valueHash = trie.getValueHash().getBytes();
        for (int k = 0; k < valueHash.length; k++) {
            Assertions.assertEquals(valueHash[k], message[k + 1]); // the first byte corresponds to flags
        }

        // check value length
        Assertions.assertEquals(new Uint24(LONG_VALUE), Uint24.decode(new byte[]{message[33], message[34], message[35]}, 0));

        Assertions.assertEquals(trie, Trie.fromMessage(message, null));
    }
}
