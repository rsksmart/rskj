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
import org.ethereum.crypto.Keccak256Helper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Created by ajlopez on 23/08/2019.
 */
class TrieGetNodesTest {
    @Test
    void getNullForUnknownKey() {
        Trie trie = new Trie();

        Assertions.assertNull(trie.getNodes(new byte[] { 0x01, 0x02, 0x03 }));
        Assertions.assertNull(trie.getNodes("foo"));
    }

    @Test
    void putOneKeyAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());

        List<Trie> nodes = trie.getNodes("foo");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals("bar".getBytes(), nodes.get(0).getValue());
    }

    @Test
    void putTwoKeysAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());
        trie = trie.put("bar", "foo".getBytes());

        List<Trie> nodes = trie.getNodes("foo");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(2, nodes.size());

        Assertions.assertArrayEquals("bar".getBytes(StandardCharsets.UTF_8), nodes.get(0).getValue());
        Assertions.assertNull(nodes.get(1).getValue());
    }

    @Test
    void putAndGetKeyValueTwiceWithDifferenteValuesAndGetNodes() {
        Trie trie = new Trie();
        Trie trie1 = trie.put("foo", "bar1".getBytes());
        Trie trie2 = trie1.put("foo", "bar2".getBytes());

        List<Trie> nodes = trie2.getNodes("foo");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals("bar2".getBytes(), nodes.get(0).getValue());
    }

    @Test
    void putLongValueAndGetNodes() {
        Trie trie = new Trie();
        byte[] value = TrieValueTest.makeValue(100);

        trie = trie.put("foo", value);

        List<Trie> nodes = trie.getNodes("foo");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals(value, nodes.get(0).getValue());
    }

    @Test
    void putAndDeleteKeyAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes()).delete("foo");

        Assertions.assertNull(trie.getNodes("foo"));
    }

    @Test
    void putKeyLongValueAndDeleteKeyAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("foo", TrieValueTest.makeValue(100)).delete("foo");

        Assertions.assertNull(trie.getNodes("foo"));
    }

    @Test
    void putAndGetEmptyKeyValueAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("", "bar".getBytes());

        List<Trie> nodes = trie.getNodes("");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals("bar".getBytes(), nodes.get(0).getValue());
    }

    @Test
    void putAndGetEmptyKeyLongValueAndGetNodes() {
        Trie trie = new Trie();
        byte[] value = TrieValueTest.makeValue(100);

        trie = trie.put("", value);

        List<Trie> nodes = trie.getNodes("");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals(value, nodes.get(0).getValue());
    }

    @Test
    void putAndGetTwoKeyLongValues() {
        Trie trie = new Trie();
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        trie = trie.put("foo", value1);
        trie = trie.put("bar", value2);

        List<Trie> nodes = trie.getNodes("foo");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(2, nodes.size());
        Assertions.assertArrayEquals(value1, nodes.get(0).getValue());
        Assertions.assertNull(nodes.get(1).getValue());
    }

    @Test
    void putAndGetKeyAndSubKeyValuesAndGetNodes() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());
        trie = trie.put("f", "42".getBytes());

        List<Trie> nodes = trie.getNodes("f");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals("42".getBytes(), nodes.get(0).getValue());
    }

    @Test
    void putAndGetKeyAndSubKeyLongValuesAndGetNodes() {
        Trie trie = new Trie();
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        trie = trie.put("foo", value1);
        trie = trie.put("f", value2);

        List<Trie> nodes = trie.getNodes("f");

        Assertions.assertNotNull(nodes);
        Assertions.assertFalse(nodes.isEmpty());
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertArrayEquals(value2, nodes.get(0).getValue());
    }

    @Test
    void putAndGetOneHundredKeyValuesAndGetNodes() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", (k + "").getBytes());

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            List<Trie> nodes = trie.getNodes(key);
            Assertions.assertNotNull(nodes);
            Assertions.assertTrue(nodes.size() > 1);
            Assertions.assertArrayEquals((k + "").getBytes(), nodes.get(0).getValue());

            Assertions.assertTrue(find(nodes.get(0).toMessage(), (k + "").getBytes()));

            for (int j = 1; j < nodes.size(); j++) {
                Trie childNode = nodes.get(j - 1);
                Trie node = nodes.get(j);

                Assertions.assertTrue(find(node.toMessage(), childNode.getHash().getBytes()) || find(node.toMessage(), childNode.toMessage()));
            }
        }
    }

    @Test
    void putAndGetOneHundredKeyLongValues() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", TrieValueTest.makeValue(k + 100));

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            byte[] expected = TrieValueTest.makeValue(k + 100);
            List<Trie> nodes = trie.getNodes(key);
            Assertions.assertNotNull(nodes);
            Assertions.assertTrue(nodes.size() > 1);
            Assertions.assertArrayEquals(expected, nodes.get(0).getValue());

            Assertions.assertTrue(find(nodes.get(0).toMessage(), Keccak256Helper.keccak256(expected)));

            for (int j = 1; j < nodes.size(); j++) {
                Trie childNode = nodes.get(j - 1);
                Trie node = nodes.get(j);

                Assertions.assertTrue(find(node.toMessage(), childNode.getHash().getBytes()) || find(node.toMessage(), childNode.toMessage()));
            }
        }
    }

    // https://stackoverflow.com/questions/35248973/how-to-search-sequence-of-bytes-in-an-byte-array-of-a-bin-file
    private static boolean find(byte[] buffer, byte[] key) {
        for (int i = 0; i <= buffer.length - key.length; i++) {
            int j = 0;

            while (j < key.length && buffer[i + j] == key[j]) {
                j++;
            }

            if (j == key.length) {
                return true;
            }
        }

        return false;
    }
}
