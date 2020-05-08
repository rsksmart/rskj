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
  * Created by #mish on April 21, 2020.
  * for testing gradle build modified to permit standard system streams 
 */
public class TrieRentTest {
    
    // trie put, get node and r 
    @Test
    public void putKeyGetRent() {
        Trie trie = new Trie();        
        trie = trie.put("foo", "abc".getBytes());
        trie = trie.put("foot", "abc".getBytes());
        //System.out.println(trie);
        System.out.println("Rent fully paid until time  "+ trie.getLastRentPaidTime());
        
        // replace with findNode?
        List<Trie> nodes = trie.getNodes("foo");

        Assert.assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), nodes.get(0).getValue());
        Assert.assertEquals(0,trie.getLastRentPaidTime()); // 0 (long cannot be null)
    }

    // rent time delta
    @Test
    public void putKeyGetRentTimeDelta() {
        Trie trie = new Trie();        
        trie = trie.put("foo", "abc".getBytes());
        long timeDelta = trie.getRentPaidTimeDelta();
        System.out.println("Time delta between now and rent last paid (fully) "+ timeDelta + " sec, or about "+ timeDelta/(365.25*24*3600) + " years");
        
        // replace with findNode?
        List<Trie> nodes = trie.getNodes("foo"); 
        Assert.assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), nodes.get(0).getValue());
    }

    // base: a test modified from TrieKeyValueTest -> save, retrieve, check rent status
    @Test
    public void putAndGetKeyValueTwice() {
        Trie trie = new Trie();
        Trie trie1 = trie.put("foo".getBytes(), "bar".getBytes());
        System.out.println("Rent fully paid until time Trie 1: "+ trie1.getLastRentPaidTime());
        
        Assert.assertNotNull(trie1.get("foo"));
        Assert.assertArrayEquals("bar".getBytes(), trie1.get("foo".getBytes()));

        Trie trie2 = trie1.putLastRentPaidTime("foo".getBytes(), "zip".getBytes(), 1000_000); 
        Assert.assertNotNull(trie2.get("foo"));
        Assert.assertArrayEquals("zip".getBytes(), trie2.get("foo".getBytes()));
        System.out.println("Rent fully paid until time Trie 2: "+ trie2.getLastRentPaidTime());
        //Assert.assertSame(trie1, trie2);
    }
    
    @Test
    public void putAndGetKeyValueSameTrie() {
        Trie trie = new Trie();
        trie = trie.put("foo".getBytes(), "bar".getBytes());
        System.out.println("Rent fully paid until time Trie : " + trie.getLastRentPaidTime() +
                        " value:  " + new String(trie.get("foo".getBytes())) );
        //System.out.println(new String("bar".getBytes()));
        //add same key with rent update
        trie = trie.putLastRentPaidTime("foo".getBytes(), "zip".getBytes(), 1000_000); 
        System.out.println("Rent fully paid until time (same key with rentupdate): " + trie.getLastRentPaidTime() +
                        " value:  " + new String( trie.get("foo".getBytes())));
          
        //back to initial value, same key. RentpadDatewill be retained
        trie = trie.put("foo".getBytes(), "bar".getBytes());
        System.out.println("Rent fully paid until time (still the same key, no rent info): "+ trie.getLastRentPaidTime() +
                        " value:  " + new String( trie.get("foo".getBytes())));
        Assert.assertEquals(1000_000, trie.getLastRentPaidTime());        

    }

    // #mish: this is a variant of a test of the same name from TrieGetNodesTest.
    @Test
    public void putKeyAndSubkeyAndGetNodes() {
        Trie trie = new Trie();
        //order of trie puts() does not matter for getNodes()
        trie = trie.put("foo".getBytes(), "abc".getBytes()); // "foo": main key of interest

        trie = trie.putLastRentPaidTime("fo".getBytes(), "longSubKeyVal".getBytes(), 4000); //a longer subkey
        trie = trie.putLastRentPaidTime("fo".getBytes(), "longSubKeyVal".getBytes(), 3000); // value unchanged, rent changed
        //trie = trie.putLastRentPaidTime("fo".getBytes(), "newlongSubKeyVal".getBytes(), 3000); //value and changed, rent unchanged 
        
        trie = trie.putLastRentPaidTime("f".getBytes(), "shortSubKeyVal".getBytes(), 100); //short subkey "f", even empty "" works
        trie = trie.putLastRentPaidTime("f".getBytes(), "newshortSubKeyVal".getBytes(), 200); //value and rent both changed
        //regular put, this will not alter last rent paid time
        trie = trie.put("f".getBytes(), "newInfo".getBytes()); //value changed, but this should not change last rent paid time
        
        List<Trie> nodes = trie.getNodes("foo"); //foo, then fo, then f

        for (int n = 0; n < nodes.size(); n++){
            //System.out.println("Rent last paid through block: " + nodes.get(n).getLastRentPaidTime());
            System.out.println("Rent last paid time: "+ nodes.get(n).getLastRentPaidTime() + " value: " + new String(nodes.get(n).getValue()));
        }
        Assert.assertEquals(0, nodes.get(0).getLastRentPaidTime()); // foo renttime not added
        Assert.assertEquals(3000, nodes.get(1).getLastRentPaidTime()); // fo 
        Assert.assertEquals("newInfo",new String(nodes.get(2).getValue())); // f updated value
        Assert.assertEquals(200, nodes.get(2).getLastRentPaidTime()); // even though value updated, last rent paid status unchanged    
    }
}

