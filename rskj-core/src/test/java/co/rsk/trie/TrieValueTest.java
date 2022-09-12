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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 04/12/2017.
 */
class TrieValueTest {
    @Test
    void noLongValueInEmptyTrie() {
        Trie trie = new Trie();

        Assertions.assertFalse(trie.hasLongValue());
        Assertions.assertNull(trie.getValueHash());
        Assertions.assertNull(trie.getValue());
    }

    @Test
    void noLongValueInTrieWithShortValue() {
        byte[] value = new byte[] { 0x01, 0x02, 0x03 };
        byte[] key = new byte[] { 0x04, 0x05 };
        Trie trie = new Trie().put(key, value);
        byte[] valueHash = Keccak256Helper.keccak256(value);

        Assertions.assertFalse(trie.hasLongValue());
        Assertions.assertArrayEquals(trie.getValueHash().getBytes(), valueHash);
        Assertions.assertEquals(value.length, trie.getValueLength().intValue());
        Assertions.assertArrayEquals(value, trie.getValue());
    }

    @Test
    void noValueInTrieWith32BytesValue() {
        byte[] value = makeValue(32);

        for (int k = 0; k < value.length; k++)
            value[k] = (byte)(k + 1);

        byte[] key = new byte[] { 0x04, 0x05 };
        Trie trie = new Trie().put(key, value);

        Assertions.assertFalse(trie.hasLongValue());
        Assertions.assertNotNull(trie.getValueHash());
        Assertions.assertEquals(value.length, trie.getValueLength().intValue());
        Assertions.assertArrayEquals(value, trie.getValue());
    }

    @Test
    void longValueInTrieWith33BytesValue() {
        byte[] value = makeValue(33);

        for (int k = 0; k < value.length; k++)
            value[k] = (byte)(k + 1);

        byte[] key = new byte[] { 0x04, 0x05 };
        Trie trie = new Trie().put(key, value);

        Assertions.assertTrue(trie.hasLongValue());
        Assertions.assertNotNull(trie.getValueHash());
        Assertions.assertEquals(32, trie.getValueHash().getBytes().length);
        Assertions.assertArrayEquals(value, trie.getValue());
    }

    public static byte[] makeValue(int length) {
        byte[] value = new byte[length];

        for (int k = 0; k < length; k++) {
            value[k] = (byte)((k + 1) % 256);
        }

        return value;
    }
}

