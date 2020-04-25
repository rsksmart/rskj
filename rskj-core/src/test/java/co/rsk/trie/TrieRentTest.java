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
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
  * Created by mish on April 21.
  * for testing gradle build modified to permit standard system streams 
 */
public class TrieRentTest {
    
    // trie put, get node and r 
    @Test
    public void putKeyGetRent() {
        Trie trie = new Trie();        
        trie = trie.put("foo", "abc".getBytes());
        System.out.println(trie);
        System.out.println("Rent paid until block number "+ trie.getLastRentPaidTime());
        
        // replace with findNode?
        List<Trie> nodes = trie.getNodes("foo"); 
        Assert.assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), nodes.get(0).getValue());
        Assert.assertEquals(0,trie.getLastRentPaidTime()); // 0 (long cannot be null)
    }

    // trie save, retrieve, check rent status
    // trie to and from message

}
