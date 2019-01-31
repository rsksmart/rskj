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
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class SecureTrieKeyValueTest {

    @Test
    public void zeroKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(oneKey);

        Assert.assertTrue(Arrays.equals(trie.get(zeroKey), "So, first of all, let me assert my firm belief that".getBytes()));
        Assert.assertNull(trie.get(oneKey));
    }

    @Test
    public void oneKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assert.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assert.assertNull(trie.get(zeroKey));
    }

    @Test
    public void zeroKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assert.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assert.assertNull(trie.get(zeroKey));
    }

    @Test
    public void testRecursivelyDelete(){
        byte[] key0 = "0".getBytes();
        byte[] key1 = "1".getBytes();
        byte[] key2 = "112999".getBytes();
        byte[] key3 = "11200".getBytes();
        byte[] key4 = "1145".getBytes();

        byte[] msg0 = Hex.toHexString(key0).getBytes();
        byte[] msg1 = Hex.toHexString(key1).getBytes();
        byte[] msg2 = Hex.toHexString(key2).getBytes();
        byte[] msg3 = Hex.toHexString(key3).getBytes();
        byte[] msg4 = Hex.toHexString(key4).getBytes();

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
            Assert.assertArrayEquals(trie.get(keys.get(i)), values.get(i));
        }

        trie = trie.deleteRecursive(key1);

        // Now only key0 must remain
        for (int i = 1; i < keys.size() ; i++) {
            Assert.assertNull(trie.get(keys.get(i)));
        }

        // Now check the tree size and make sure it's the original
        Assert.assertEquals(trieSize,trie.trieSize());
    }

    @Test
    public void oneKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie();
        byte[] msgZero ="So, first of all, let me assert my firm belief that".getBytes();
        trie = trie.put(zeroKey,msgZero );
        Assert.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));

        trie = trie.put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        Assert.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));

        trie = trie.delete(oneKey);


        Assert.assertTrue(Arrays.equals(trie.get(zeroKey), msgZero));
        Assert.assertNull(trie.get(oneKey));
    }
}
