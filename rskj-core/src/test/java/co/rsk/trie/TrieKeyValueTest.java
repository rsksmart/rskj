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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 11/01/2017.
 */
public class TrieKeyValueTest {
    @Test
    public void getNullForUnknownKey() {
        Trie trie = new Trie();

        Assertions.assertNull(trie.get(new byte[] { 0x01, 0x02, 0x03 }));
        Assertions.assertNull(trie.get("foo"));
    }

    @Test
    public void putAndGetKeyValue() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());
        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie.get("foo"));
    }

    @Test
    public void putAndGetKeyValueTwice() {
        Trie trie = new Trie();
        Trie trie1 = trie.put("foo", "bar".getBytes());
        Trie trie2 = trie1.put("foo", "bar".getBytes());
        Assertions.assertNotNull(trie1.get("foo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie1.get("foo"));
        Assertions.assertNotNull(trie2.get("foo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie2.get("foo"));
        Assertions.assertSame(trie1, trie2);
    }
    @Test
    public void putAndGetKeyValueTwiceWithDifferenteValues() {
        Trie trie = new Trie();
        Trie trie1 = trie.put("foo", "bar1".getBytes());
        Trie trie2 = trie1.put("foo", "bar2".getBytes());
        Assertions.assertNotNull(trie1.get("foo"));
        Assertions.assertArrayEquals("bar1".getBytes(), trie1.get("foo"));
        Assertions.assertNotNull(trie2.get("foo"));
        Assertions.assertArrayEquals("bar2".getBytes(), trie2.get("foo"));
    }


    @Test
    public void putAndGetKeyLongValue() {
        Trie trie = new Trie();
        byte[] value = TrieValueTest.makeValue(100);

        trie = trie.put("foo", value);
        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals(value, trie.get("foo"));
    }

    @Test
    public void putKeyValueAndDeleteKey() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes()).delete("foo");
        Assertions.assertNull(trie.get("foo"));
    }

    @Test
    public void putKeyLongValueAndDeleteKey() {
        Trie trie = new Trie();

        trie = trie.put("foo", TrieValueTest.makeValue(100)).delete("foo");
        Assertions.assertNull(trie.get("foo"));
    }

    @Test
    public void putAndGetEmptyKeyValue() {
        Trie trie = new Trie();

        trie = trie.put("", "bar".getBytes());
        Assertions.assertNotNull(trie.get(""));
        Assertions.assertArrayEquals("bar".getBytes(), trie.get(""));
    }

    @Test
    public void putAndGetEmptyKeyLongValue() {
        Trie trie = new Trie();
        byte[] value = TrieValueTest.makeValue(100);

        trie = trie.put("", value);
        Assertions.assertNotNull(trie.get(""));
        Assertions.assertArrayEquals(value, trie.get(""));
    }

    @Test
    public void putAndGetTwoKeyValues() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());
        trie = trie.put("bar", "foo".getBytes());

        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie.get("foo"));

        Assertions.assertNotNull(trie.get("bar"));
        Assertions.assertArrayEquals("foo".getBytes(), trie.get("bar"));
    }

    @Test
    public void putAndGetTwoKeyLongValues() {
        Trie trie = new Trie();
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        trie = trie.put("foo", value1);
        trie = trie.put("bar", value2);

        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals(value1, trie.get("foo"));

        Assertions.assertNotNull(trie.get("bar"));
        Assertions.assertArrayEquals(value2, trie.get("bar"));
    }

    @Test
    public void putAndGetKeyAndSubKeyValues() {
        Trie trie = new Trie();

        trie = trie.put("foo", "bar".getBytes());
        trie = trie.put("f", "42".getBytes());

        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie.get("foo"));

        Assertions.assertNotNull(trie.get("f"));
        Assertions.assertArrayEquals("42".getBytes(), trie.get("f"));
    }

    @Test
    public void putAndGetKeyAndSubKeyLongValues() {
        Trie trie = new Trie();
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        trie = trie.put("foo", value1);
        trie = trie.put("f", value2);

        Assertions.assertNotNull(trie.get("foo"));
        Assertions.assertArrayEquals(value1, trie.get("foo"));

        Assertions.assertNotNull(trie.get("f"));
        Assertions.assertArrayEquals(value2, trie.get("f"));
    }

    @Test
    public void putAndGetKeyAndSubKeyValuesInverse() {
        Trie trie = new Trie();

        trie = trie.put("f", "42".getBytes())
                .put("fo", "bar".getBytes());

        Assertions.assertNotNull(trie.get("fo"));
        Assertions.assertArrayEquals("bar".getBytes(), trie.get("fo"));

        Assertions.assertNotNull(trie.get("f"));
        Assertions.assertArrayEquals("42".getBytes(), trie.get("f"));
    }

    @Test
    public void putAndGetKeyAndSubKeyLongValuesInverse() {
        Trie trie = new Trie();
        byte[] value1 = TrieValueTest.makeValue(100);
        byte[] value2 = TrieValueTest.makeValue(200);

        trie = trie.put("f", value1)
                .put("fo", value2);

        Assertions.assertNotNull(trie.get("fo"));
        Assertions.assertArrayEquals(value2, trie.get("fo"));

        Assertions.assertNotNull(trie.get("f"));
        Assertions.assertArrayEquals(value1, trie.get("f"));
    }

    @Test
    public void putAndGetOneHundredKeyValues() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", (k + "").getBytes());

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            byte[] expected = key.getBytes();
            byte[] value = trie.get(key);
            Assertions.assertArrayEquals(value, expected, key);
        }
    }

    @Test
    public void putAndGetOneHundredKeyLongValues() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", TrieValueTest.makeValue(k + 100));

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            byte[] expected = TrieValueTest.makeValue(k + 100);
            byte[] value = trie.get(key);
            Assertions.assertArrayEquals(expected, value);
        }
    }

    @Test
    public void putAndGetOneHundredKeyValuesUsingBinaryTree() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", (k + "").getBytes());

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            byte[] expected = key.getBytes();
            byte[] value = trie.get(key);
            Assertions.assertArrayEquals(value, expected, key);
        }
    }

    @Test
    public void putAndGetOneHundredKeyLongValuesUsingBinaryTree() {
        Trie trie = new Trie();

        for (int k = 0; k < 100; k++)
            trie = trie.put(k + "", TrieValueTest.makeValue(k + 100));

        for (int k = 0; k < 100; k++) {
            String key = k + "";
            byte[] expected = TrieValueTest.makeValue(k + 100);
            byte[] value = trie.get(key);
            Assertions.assertArrayEquals(expected, value);
        }
    }
}
