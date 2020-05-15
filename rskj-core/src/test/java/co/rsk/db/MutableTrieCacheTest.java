/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by SerAdmin on 9/26/2018. 
 */
public class MutableTrieCacheTest {
    
    // some helper methods first

    // convert string to bytes
    private byte[] toBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    // convert string to Dataword
    private DataWord toStorageKey(String x) {
        return DataWord.valueOf(toBytes(x));
    }

    // convert set of byteArrayWrapper objects (e.g. node keys) to a single, `;' separated, sorted, string
    private String setToString(Set<ByteArrayWrapper> set) {
        String r ="";
        ArrayList<String> list = new ArrayList<>();

        for (ByteArrayWrapper item : set) {
            list.add(new String(item.getData(), StandardCharsets.UTF_8));

        }
        Collections.sort(list);
        for (String s : list ) {
            r = r+s+";"; //separted by ';'
        }

        return r;
    }

    private String getKeysFrom(MutableTrie mt) {
        return setToString(mt.collectKeys(Integer.MAX_VALUE));
    }

    @Test
    public void testPuts() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));
        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;",result);
        // one using the rent version
        baseMutableTrie.putLastRentPaidTime("BOB",toBytes("bob"), 1L);
        // initalize a cache
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // Now add two more to the cache, not the parent
        mtCache.put("CAROL",toBytes("carol"));  
        // with rent data
        mtCache.putLastRentPaidTime("ROBERT",toBytes("robert"), 3L);
        // parent trie is unaware of these changes..         
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;",result);

        result = getKeysFrom(mtCache);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
        assertArrayEquals(toBytes("robert"), mtCache.get(toBytes("ROBERT")));
        assertEquals(3L, mtCache.getLastRentPaidTime(toBytes("ROBERT")));
        assertEquals(0, mtCache.getLastRentPaidTime(toBytes("missingROBERT"))); // key not in trie
        
        // commit so the changes are reflected in base trie as well
        mtCache.commit();
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
        assertArrayEquals(toBytes("robert"), baseMutableTrie.get(toBytes("ROBERT")));
    }

    @Test
    public void testAccountBehavior(){
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        // expand base key to the desired size by appending 0's
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");        
        // put couple of nodes under the account
        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"), 2L);
        
        //delete using the account key
        mtCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        
        assertNull(mtCache.get(toBytes(accountLikeKey.toString())));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));

        // if a key is inserted after a recursive delete is visible
        mtCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"), 4L);
        assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertEquals(4L, mtCache.getLastRentPaidTime(toBytes(accountLikeKey.toString() + "125")));
        
        byte[] val = mtCache.get(toBytes(accountLikeKey.toString() + "125"));
        mtCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "127"), toBytes("HALO"), 3L);
        assertArrayEquals(toBytes("HALO"), mtCache.get(toBytes(accountLikeKey.toString() + "127")));

        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString())));

        mtCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "125"), null, 4L);
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertEquals(0, mtCache.getLastRentPaidTime(toBytes(accountLikeKey.toString() + "125"))); //cos long cannot be null

    }

    @Test
    public void testNestedCaches() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");
        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"), 2L);
        mtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));

        // puts on superior levels are not reflected on lower levels before commit
        MutableTrieCache otherCache = new MutableTrieCache(mtCache);
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "126")));
        otherCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "124"), toBytes("LAH"),3000);
        // node's value in higher and lower level caches
        assertArrayEquals(toBytes("LAH"), otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertArrayEquals(toBytes("HAL"), mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        // a node's rent paid time in higher and lower level caches
        assertEquals(3000, otherCache.getLastRentPaidTime(toBytes(accountLikeKey.toString() + "124")));
        assertEquals(2, mtCache.getLastRentPaidTime(toBytes(accountLikeKey.toString() + "124")));

        //this is a delete in the higher level cache
        otherCache.putLastRentPaidTime(toBytes(accountLikeKey.toString() + "123"), null, 0);
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        // value unchanged in the lower level cache
        assertArrayEquals(toBytes("HAL"), mtCache.get(toBytes(accountLikeKey.toString() + "123")));

        // after commit puts on superior levels are reflected on lower levels
        otherCache.commit();
        assertArrayEquals(toBytes("LAH"), otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertArrayEquals(toBytes("LAH"), mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertArrayEquals(toBytes("HAL"), mtCache.get(toBytes(accountLikeKey.toString() + "125")));

        mtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        mtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        otherCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        otherCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(otherCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString())));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "126")));

        // before commit lower level cache is not affected
        assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString())));

        otherCache.commit();
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(mtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(mtCache.get(toBytes(accountLikeKey.toString())));
    }

    @Test
    public void testStorageKeysMixOneLevel() {
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);
        MutableTrieCache otherCache = new MutableTrieCache(mtCache);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);
        MutableRepository cacheRepository = new MutableRepository(mtCache);
        MutableRepository otherCacheRepository = new MutableRepository(otherCache);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");
        // storage => contract account, not a regular one.. so two steps.. 
        baseRepository.createAccount(addr);
        baseRepository.setupContract(addr);

        //5 storage node keys. toSrageKey defined at the top
        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");
        DataWord sk122 = toStorageKey("122");
        DataWord sk123 = toStorageKey("123");
        DataWord sk124 = toStorageKey("124");
        // base repo.. these nodes are added to baseMutableTrie
        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk121, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk122, toBytes("HAL"));
        // cache based on lower level mtCache
        // overwrite the above keys in first cache . the trie here is the same parent trie baseMutableTrie
        cacheRepository.addStorageBytes(addr, sk120, null); // deleted
        cacheRepository.addStorageBytes(addr, sk121, toBytes("LAH"));
        cacheRepository.addStorageBytes(addr, sk123, toBytes("LAH"));
        // higher level Repo cache.. based on higher level mtCache
        otherCacheRepository.addStorageBytes(addr, sk124, toBytes("HAL"));

        // assertions
        // these are based on the first cached trie
        Iterator<DataWord> storageKeys = mtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120)); //was deleted
        assertTrue(keys.contains(sk121));
        assertTrue(keys.contains(sk122));
        assertTrue(keys.contains(sk123)); // will find this from the underlying baseMuttrie
        assertFalse(keys.contains(sk124)); // won't see this, not yet committed from higgher level cache
        assertEquals(3, keys.size());

        // repeat for higher level cahce    
        storageKeys = otherCache.getStorageKeys(addr);
        keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120)); // was nulled/deleted
        assertTrue(keys.contains(sk121));
        assertTrue(keys.contains(sk122));
        assertTrue(keys.contains(sk123));
        assertTrue(keys.contains(sk124));
        assertEquals(4, keys.size());
    }

    @Test
    public void testStorageKeysNoCache() {  // no cached repository, just a base.. there is howwver a mutTrieCache shares the same parent trie
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk121, toBytes("HAL"));

        Iterator<DataWord> storageKeys = mtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertTrue(keys.contains(sk120));
        assertTrue(keys.contains(sk121));
        assertEquals(2, keys.size());
    }

    @Test
    public void testStorageKeysNoTrie() {
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository cacheRepository = new MutableRepository(mtCache);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord skzero = DataWord.ZERO;
        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        cacheRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        cacheRepository.addStorageBytes(addr, sk121, toBytes("HAL"));
        cacheRepository.addStorageBytes(addr, skzero, toBytes("HAL"));

        Iterator<DataWord> storageKeys = mtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertTrue(keys.contains(sk120));
        assertTrue(keys.contains(sk121));
        assertTrue(keys.contains(skzero));
        assertEquals(3, keys.size());
    }

    @Test
    public void testStorageKeysDeletedAccount() {
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository cacheRepository = new MutableRepository(mtCache);
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);
        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        cacheRepository.delete(addr);

        Iterator<DataWord> storageKeys = mtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120));
        assertFalse(keys.contains(sk121));
        assertEquals(0, keys.size());

        cacheRepository.addStorageBytes(addr, sk121, toBytes("HAL"));

        storageKeys = mtCache.getStorageKeys(addr);
        keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120));
        assertTrue(keys.contains(sk121));
        assertEquals(1, keys.size());
    }

    @Test
    public void testStoreValueOnTrieAndGetSize() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base

        byte[] value = toBytes("alice");
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        baseMutableTrie.put(key, value);
        Uint24 valueLength = baseMutableTrie.getValueLength(key);
        assertEquals(new Uint24(value.length), valueLength);

        // Test the same in cache

        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        mtCache.put(keyForCache, value);
        Uint24 cacheValueLength = mtCache.getValueLength(keyForCache);
        assertEquals(new Uint24(value.length), cacheValueLength);
    }

    @Test
    public void testStoreEmptyValueOnTrieAndGetSize() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        byte[] emptyValue = new byte[0];
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        baseMutableTrie.put(key, emptyValue);
        Uint24 valueLength = baseMutableTrie.getValueLength(key);
        assertEquals(Uint24.ZERO, valueLength);

        // Test the same in cache

        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        mtCache.put(keyForCache, emptyValue);
        Uint24 cacheValueLength = mtCache.getValueLength(keyForCache);
        assertEquals(Uint24.ZERO, cacheValueLength);
    }

    @Test
    public void testGetValueNotStoredAndGetSize() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base

        byte[] wrongKey = toBytes("BOB");
        
        Uint24 valueLength = baseMutableTrie.getValueLength(wrongKey);
        assertEquals(Uint24.ZERO, valueLength);

        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        Uint24 cacheValueLength = mtCache.getValueLength(wrongKey);
        assertEquals(Uint24.ZERO, cacheValueLength);
    }

    @Test
    public void testStoreValueOnTrieAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        byte[] value = toBytes("11111111112222222222333333333344");
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        Keccak256 expectedHash = new Keccak256(Keccak256Helper.keccak256(value));

        baseMutableTrie.put(key, value);
        mtCache.put(keyForCache, value);

        getValueHashAndAssert(baseMutableTrie, key, expectedHash);
        getValueHashAndAssert(mtCache, keyForCache, expectedHash);
    }

    @Test
    public void testStoreEmptyValueOnTrieAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        byte[] emptyValue = new byte[0];
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        Keccak256 emptyHash = new Keccak256(Keccak256Helper.keccak256(emptyValue));

        baseMutableTrie.put(key, emptyValue);
        mtCache.put(keyForCache, emptyValue);

        getValueHashAndAssert(baseMutableTrie, key, Keccak256.ZERO_HASH);
        getValueHashAndAssert(mtCache, keyForCache, emptyHash);
    }

    @Test
    public void testGetValueNotStoredAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        byte[] wrongKey = toBytes("BOB");
        Keccak256 zeroHash = Keccak256.ZERO_HASH;

        getValueHashAndAssert(baseMutableTrie, wrongKey, zeroHash);
        getValueHashAndAssert(mtCache, wrongKey, zeroHash);
    }

    

    private void getValueHashAndAssert(MutableTrie trie, byte[] key, Keccak256 expectedHash) {
        Keccak256 hash = trie.getValueHash(key);
        assertEquals(expectedHash, hash);
    }
}