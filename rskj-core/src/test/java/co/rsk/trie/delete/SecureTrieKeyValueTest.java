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

package co.rsk.trie.delete;

import co.rsk.trie.Trie;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ajlopez on 03/04/2017.
 */
class SecureTrieKeyValueTest {

    @Test
    void zeroKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(oneKey);

        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), "So, first of all, let me assert my firm belief that".getBytes()));
        Assertions.assertNull(trie.get(oneKey));
    }

    @Test
    void oneKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assertions.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assertions.assertNull(trie.get(zeroKey));
    }

    @Test
    void zeroKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assertions.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assertions.assertNull(trie.get(zeroKey));
    }

    @Test
    void testRecursivelyDelete(){
        byte[] key0 = "0".getBytes();
        byte[] key1 = "1".getBytes();
        byte[] key2 = "112999".getBytes();
        byte[] key3 = "11200".getBytes();
        byte[] key4 = "1145".getBytes();

        byte[] msg0 = ByteUtil.toHexString(key0).getBytes();
        byte[] msg1 = ByteUtil.toHexString(key1).getBytes();
        byte[] msg2 = ByteUtil.toHexString(key2).getBytes();
        byte[] msg3 = ByteUtil.toHexString(key3).getBytes();
        byte[] msg4 = ByteUtil.toHexString(key4).getBytes();

        List<byte[]> keys = Arrays.asList(key0, key1, key2, key3, key4);
        List<byte[]> values = Arrays.asList(msg0, msg1, msg2, msg3, msg4);

        Trie trie = new Trie();
        trie = trie.put(keys.get(0), values.get(0));
        int trieSize = trie.trieSize();

        for (int i = 1; i < keys.size(); i++) {
            trie = trie.put(keys.get(i), values.get(i));
        }

        // Now check that all values are there
        for (int i = 0; i < keys.size(); i++) {
            Assertions.assertArrayEquals(trie.get(keys.get(i)), values.get(i));
        }

        trie = trie.deleteRecursive(key1);

        // Now only key0 must remain
        for (int i = 1; i < keys.size() ; i++) {
            Assertions.assertNull(trie.get(keys.get(i)));
        }

        // Now check the tree size and make sure it's the original
        Assertions.assertEquals(trieSize,trie.trieSize());
    }

    @Test
    void testRecursivelyDeleteCollapses(){
        byte[] key0 = "0".getBytes();
        byte[] key1 = "1".getBytes();
        byte[] key2 = "112999".getBytes();
        byte[] key3 = "11200".getBytes();
        byte[] key4 = "1145".getBytes();

        byte[] msg0 = ByteUtil.toHexString(key0).getBytes();
        byte[] msg1 = ByteUtil.toHexString(key1).getBytes();
        byte[] msg2 = ByteUtil.toHexString(key2).getBytes();
        byte[] msg3 = ByteUtil.toHexString(key3).getBytes();
        byte[] msg4 = ByteUtil.toHexString(key4).getBytes();

        List<byte[]> keys = Arrays.asList(key0, key1, key2, key3, key4);
        List<byte[]> values = Arrays.asList(msg0, msg1, msg2, msg3, msg4);

        Trie trie = new Trie();
        trie = trie.put(keys.get(0), values.get(0));
        int trieSize = trie.trieSize();

        for (int i = 1; i < keys.size(); i++) {
            trie = trie.put(keys.get(i), values.get(i));
        }

        // put this to test collapsing edge case
        trie = trie.put(new byte[0], values.get(1));

        // Now check that all values are there
        for (int i = 0; i < keys.size(); i++) {
            Assertions.assertArrayEquals(trie.get(keys.get(i)), values.get(i));
        }

        trie = trie.deleteRecursive(key1);
        trie = trie.delete(new byte[0]);

        // Now only key0 must remain
        for (int i = 1; i < keys.size() ; i++) {
            Assertions.assertNull(trie.get(keys.get(i)));
        }

        // Now check the tree size and make sure it's the original
        Assertions.assertEquals(trieSize, trie.trieSize());
    }

    @Test
    void oneKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie();
        byte[] msgZero ="So, first of all, let me assert my firm belief that".getBytes();
        trie = trie.put(zeroKey,msgZero );
        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));

        trie = trie.put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));

        trie = trie.delete(oneKey);


        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));
        Assertions.assertNull(trie.get(oneKey));
    }
}
