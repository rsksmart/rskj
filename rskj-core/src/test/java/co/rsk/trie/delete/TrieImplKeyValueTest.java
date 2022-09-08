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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Created by ajlopez on 03/04/2017.
 */
public class TrieImplKeyValueTest {

    @Test
    public void zeroKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(oneKey);

        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), "So, first of all, let me assert my firm belief that".getBytes()));
        Assertions.assertNull(trie.get(oneKey));
    }

    @Test
    public void oneKeyWhenTwoKeysHasNoSharedPath() {
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "1".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assertions.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assertions.assertNull(trie.get(zeroKey));
    }

    @Test
    public void zeroKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(zeroKey);

        Assertions.assertTrue(Arrays.equals(trie.get(oneKey), "the only thing we have to fear is... fear itself ".getBytes()));
        Assertions.assertNull(trie.get(zeroKey));
    }

    @Test
    public void oneKeyWhenTwoKeysHasSharedPathAndOneIsPrefixOfTheOther(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie().put(zeroKey, "So, first of all, let me assert my firm belief that".getBytes())
                .put(oneKey, "the only thing we have to fear is... fear itself ".getBytes());

        trie = trie.delete(oneKey);

        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), "So, first of all, let me assert my firm belief that".getBytes()));
        Assertions.assertNull(trie.get(oneKey));
    }

    @Test
    public void forEqualRootStatesAfterDelete(){
        byte[] zeroKey = "0".getBytes();
        byte[] oneKey = "012345678910".getBytes();

        Trie trie = new Trie();

        byte[] stateHash0 =trie.getHash().getBytes();

        trie = trie.put(zeroKey, "zero".getBytes());

        byte[] stateHash1 =trie.getHash().getBytes();

        trie = trie.put(oneKey, "one".getBytes());

        byte[] stateHash2 =trie.getHash().getBytes();

        trie = trie.delete(oneKey);

        byte[] stateHash3 =trie.getHash().getBytes();

        byte[] zeroValue = trie.get(zeroKey);
        Assertions.assertTrue(Arrays.equals(trie.get(zeroKey), "zero".getBytes()));
        Assertions.assertNull(trie.get(oneKey));
        Assertions.assertFalse(Arrays.equals(stateHash0, stateHash1));
        Assertions.assertFalse(Arrays.equals(stateHash1, stateHash2));
        Assertions.assertTrue(Arrays.equals(stateHash1, stateHash3));

        // Not check that setting null is equivalent to deleting
        // We re'add the one key

        trie = trie.delete(oneKey);

        byte[] stateHash4 =trie.getHash().getBytes();
        Assertions.assertTrue(Arrays.equals(stateHash3, stateHash4));

        // Re remove the key again

        trie = trie.put(oneKey,null);

        byte[] stateHash5 =trie.getHash().getBytes();
        Assertions.assertTrue(Arrays.equals(stateHash1, stateHash5));
    }
}
