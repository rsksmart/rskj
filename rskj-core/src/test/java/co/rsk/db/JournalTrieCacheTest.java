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
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import sun.security.ec.point.ProjectivePoint;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class JournalTrieCacheTest {

    private byte[] toBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    private DataWord toStorageKey(String x) {
        return DataWord.valueOf(toBytes(x));
    }

    private String setToString(Set<ByteArrayWrapper> set) {
        String r ="";
        ArrayList<String> list = new ArrayList<>();

        for (ByteArrayWrapper item : set) {
            list.add(new String(item.getData(), StandardCharsets.UTF_8));

        }
        Collections.sort(list);
        for (String s : list ) {
            r = r+s+";";
        }

        return r;
    }

    private String getKeysFrom(MutableTrie mt) {
        return setToString(mt.collectKeys(Integer.MAX_VALUE));
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testPuts() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));

        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;",result);


        baseMutableTrie.put("BOB",toBytes("bob"));

        JournalTrieCache jtCache;
        jtCache = new JournalTrieCache(baseMutableTrie);

        // Now add two more
        jtCache.put("CAROL",toBytes("carol"));
        jtCache.put("ROBERT",toBytes("robert"));

        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;",result);

        result = getKeysFrom(jtCache);

        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        jtCache.commit();

        // Now the base trie must have all
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
    }

    @Test
    public void testAccountBehavior(){
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");
        jtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        jtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        jtCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString())));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "124")));

        // if a key is inserted after a recursive delete is visible
        jtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        assertNotNull(jtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString())));
    }

    @Test
    public void testNestedCaches() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        // when account is deleted any key in that account is deleted
        StringBuilder accountLikeKey = new StringBuilder("HAL");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKey.length() < keySize;) accountLikeKey.append("0");
        jtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        jtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        jtCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));

        // puts on superior levels are not reflected on lower levels before commit
        JournalTrieCache otherCache = new JournalTrieCache(jtCache);
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "126")));
        otherCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("LAH"));
        assertArrayEquals(toBytes("LAH"), otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertArrayEquals(toBytes("HAL"), jtCache.get(toBytes(accountLikeKey.toString() + "124")));
        otherCache.put(toBytes(accountLikeKey.toString() + "123"), null);
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertArrayEquals(toBytes("HAL"), jtCache.get(toBytes(accountLikeKey.toString() + "123")));

        // after commit puts on superior levels are reflected on lower levels
        otherCache.commit();
        assertArrayEquals(toBytes("LAH"), otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertArrayEquals(toBytes("LAH"), jtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertArrayEquals(toBytes("HAL"), jtCache.get(toBytes(accountLikeKey.toString() + "125")));

        jtCache.put(toBytes(accountLikeKey.toString() + "123"), toBytes("HAL"));
        jtCache.put(toBytes(accountLikeKey.toString() + "124"), toBytes("HAL"));
        otherCache.deleteRecursive(toBytes(accountLikeKey.toString()));
        otherCache.put(toBytes(accountLikeKey.toString() + "125"), toBytes("HAL"));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(otherCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString())));
        assertNull(otherCache.get(toBytes(accountLikeKey.toString() + "126")));

        // before commit lower level cache is not affected
        assertNotNull(jtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNotNull(jtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(jtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString())));

        otherCache.commit();
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "123")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString() + "124")));
        assertNotNull(jtCache.get(toBytes(accountLikeKey.toString() + "125")));
        assertNull(jtCache.get(toBytes(accountLikeKey.toString())));
    }

    @Test
    public void testStorageKeysMixOneLevel() {
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);
        JournalTrieCache otherCache = new JournalTrieCache(jtCache);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);
        MutableRepository cacheRepository = new MutableRepository(jtCache);
        MutableRepository otherCacheRepository = new MutableRepository(otherCache);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        baseRepository.createAccount(addr);
        baseRepository.setupContract(addr);

        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");
        DataWord sk122 = toStorageKey("122");
        DataWord sk123 = toStorageKey("123");
        DataWord sk124 = toStorageKey("124");

        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk121, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk122, toBytes("HAL"));
        cacheRepository.addStorageBytes(addr, sk120, null);
        cacheRepository.addStorageBytes(addr, sk121, toBytes("LAH"));
        cacheRepository.addStorageBytes(addr, sk123, toBytes("LAH"));
        otherCacheRepository.addStorageBytes(addr, sk124, toBytes("HAL"));

        // assertions

        Iterator<DataWord> storageKeys = jtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120));
        assertTrue(keys.contains(sk121));
        assertTrue(keys.contains(sk122));
        assertTrue(keys.contains(sk123));
        assertFalse(keys.contains(sk124));
        assertEquals(3, keys.size());

        storageKeys = otherCache.getStorageKeys(addr);
        keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120));
        assertTrue(keys.contains(sk121));
        assertTrue(keys.contains(sk122));
        assertTrue(keys.contains(sk123));
        assertTrue(keys.contains(sk124));
        assertEquals(4, keys.size());
    }

    @Test
    public void testStorageKeysNoCache() {
        // SUTs
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        baseRepository.addStorageBytes(addr, sk121, toBytes("HAL"));

        Iterator<DataWord> storageKeys = jtCache.getStorageKeys(addr);
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
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository cacheRepository = new MutableRepository(jtCache);

        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord skzero = DataWord.ZERO;
        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        cacheRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        cacheRepository.addStorageBytes(addr, sk121, toBytes("HAL"));
        cacheRepository.addStorageBytes(addr, skzero, toBytes("HAL"));

        Iterator<DataWord> storageKeys = jtCache.getStorageKeys(addr);
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
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        // setup

        // helpers for interacting with the SUTs
        MutableRepository cacheRepository = new MutableRepository(jtCache);
        MutableRepository baseRepository = new MutableRepository(baseMutableTrie);
        RskAddress addr = new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7");

        DataWord sk120 = toStorageKey("120");
        DataWord sk121 = toStorageKey("121");

        baseRepository.addStorageBytes(addr, sk120, toBytes("HAL"));
        cacheRepository.delete(addr);

        Iterator<DataWord> storageKeys = jtCache.getStorageKeys(addr);
        Set<DataWord> keys = new HashSet<>();
        storageKeys.forEachRemaining(keys::add);
        assertFalse(keys.contains(sk120));
        assertFalse(keys.contains(sk121));
        assertEquals(0, keys.size());

        cacheRepository.addStorageBytes(addr, sk121, toBytes("HAL"));

        storageKeys = jtCache.getStorageKeys(addr);
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

        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        jtCache.put(keyForCache, value);
        Uint24 cacheValueLength = jtCache.getValueLength(keyForCache);
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

        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        jtCache.put(keyForCache, emptyValue);
        Uint24 cacheValueLength = jtCache.getValueLength(keyForCache);
        assertEquals(Uint24.ZERO, cacheValueLength);
    }

    @Test
    public void testGetValueNotStoredAndGetSize() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base

        byte[] wrongKey = toBytes("BOB");

        Uint24 valueLength = baseMutableTrie.getValueLength(wrongKey);
        assertEquals(Uint24.ZERO, valueLength);

        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        Uint24 cacheValueLength = jtCache.getValueLength(wrongKey);
        assertEquals(Uint24.ZERO, cacheValueLength);
    }

    @Test
    public void testStoreValueOnTrieAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        byte[] value = toBytes("11111111112222222222333333333344");
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        Keccak256 expectedHash = new Keccak256(Keccak256Helper.keccak256(value));

        baseMutableTrie.put(key, value);
        jtCache.put(keyForCache, value);

        getValueHashAndAssert(baseMutableTrie, key, expectedHash);
        getValueHashAndAssert(jtCache, keyForCache, expectedHash);
    }

    @Test
    public void testStoreEmptyValueOnTrieAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        byte[] emptyValue = new byte[0];
        byte[] key = toBytes("ALICE");
        byte[] keyForCache = toBytes("ALICE2");

        Keccak256 emptyHash = new Keccak256(Keccak256Helper.keccak256(emptyValue));

        baseMutableTrie.put(key, emptyValue);
        jtCache.put(keyForCache, emptyValue);

        getValueHashAndAssert(baseMutableTrie, key, Keccak256.ZERO_HASH);
        getValueHashAndAssert(jtCache, keyForCache, emptyHash);
    }

    @Test
    public void testGetValueNotStoredAndGetHash() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        JournalTrieCache jtCache = new JournalTrieCache(baseMutableTrie);

        byte[] wrongKey = toBytes("BOB");
        Keccak256 zeroHash = Keccak256.ZERO_HASH;

        getValueHashAndAssert(baseMutableTrie, wrongKey, zeroHash);
        getValueHashAndAssert(jtCache, wrongKey, zeroHash);
    }

    /**
     * Check getHash() and save() method. Verify that all commits are applied before saving Trie in TrieStore
     */
    @Test
    public void testSaveOneCacheLevel() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));
        baseMutableTrie.put("BOB",toBytes("bob"));
        JournalTrieCache jtCache;
        jtCache = new JournalTrieCache(baseMutableTrie);

        // Now add two more
        jtCache.put("ALICE",toBytes("ecila"));
        jtCache.put("CAROL",toBytes("carol"));
        jtCache.put("ROBERT",toBytes("robert"));
        jtCache.deleteRecursive(toBytes("BOB"));

        byte[] hashFromCache = null;
        try {
            // before save, we expect an exception is thrown when getting the hash
            hashFromCache = jtCache.getHash().getBytes();
            fail("An Exception was expected because of calling getHash() on a not empty cache");
        } catch (IllegalStateException e) {
            assertNull(hashFromCache);
        }
        jtCache.save();
        hashFromCache = jtCache.getHash().getBytes();
        assertNotNull(hashFromCache);
        byte[] hashToRetrieve = baseMutableTrie.getTrie().getHash().getBytes();
        assertArrayEquals(hashFromCache, hashToRetrieve);

        Trie retrievedTrie = trieStore.retrieve(hashToRetrieve).get();
        baseMutableTrie = new MutableTrieImpl(trieStore, retrievedTrie);
        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;CAROL;ROBERT;",result);
        assertArrayEquals(toBytes("ecila"), baseMutableTrie.get(toBytes("ALICE")));
        assertNull(baseMutableTrie.get(toBytes("BOB")));
        assertArrayEquals(toBytes("carol"), baseMutableTrie.get(toBytes("CAROL")));
        assertArrayEquals(toBytes("robert"), baseMutableTrie.get(toBytes("ROBERT")));
    }

    /**
     * Check the save() method. Verify that all commits are applied before saving Trie in TrieStore
     */
    @Test
    public void testSaveSeveralLevels() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));
        baseMutableTrie.put("BOB",toBytes("bob"));
        JournalTrieCache jtCacheLvl1 = new JournalTrieCache(baseMutableTrie);
        JournalTrieCache jtCacheLvl2 = new JournalTrieCache(jtCacheLvl1);
        JournalTrieCache jtCacheLvl3 = new JournalTrieCache(jtCacheLvl2);

        // Now add two more
        jtCacheLvl1.put("ALICE",toBytes("ecila"));
        jtCacheLvl2.put("CAROL",toBytes("carol"));
        jtCacheLvl2.deleteRecursive(toBytes("BOB"));
        jtCacheLvl3.put("ROBERT",toBytes("robert"));

        jtCacheLvl3.save();
        byte[] hashToRetrieve = baseMutableTrie.getTrie().getHash().getBytes();
        Trie retrievedTrie = trieStore.retrieve(hashToRetrieve).get();
        baseMutableTrie = new MutableTrieImpl(trieStore, retrievedTrie);
        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;CAROL;ROBERT;",result);
        assertArrayEquals(toBytes("ecila"), baseMutableTrie.get(toBytes("ALICE")));
        assertNull(baseMutableTrie.get(toBytes("BOB")));
        assertArrayEquals(toBytes("carol"), baseMutableTrie.get(toBytes("CAROL")));
        assertArrayEquals(toBytes("robert"), baseMutableTrie.get(toBytes("ROBERT")));
    }

    /**
     * Check collectKeys()/deleteRecursive()/commit()/rollback() with a 4 levels caching scenario
     */
    @Test
    public void testMultiPutsIncludingAccountDeletion() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());

        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));

        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;",result);

        baseMutableTrie.put("BOB",toBytes("bob"));

        JournalTrieCache jtCacheLvl1;
        jtCacheLvl1 = new JournalTrieCache(baseMutableTrie);
        JournalTrieCache jtCacheLvl2;
        jtCacheLvl2 = new JournalTrieCache(jtCacheLvl1);
        JournalTrieCache jtCacheLvl3;
        jtCacheLvl3 = new JournalTrieCache(jtCacheLvl2);
        JournalTrieCache jtCacheLvl4;
        jtCacheLvl4 = new JournalTrieCache(jtCacheLvl3);

        // Now add two more at lvl1
        jtCacheLvl1.put("CAROL",toBytes("carol"));
        jtCacheLvl1.put("ROBERT",toBytes("robert"));
        result = getKeysFrom(jtCacheLvl1);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        // Now delete one at lvl2
        jtCacheLvl2.deleteRecursive(toBytes("CAROL"));
        result = getKeysFrom(jtCacheLvl2);
        assertEquals("ALICE;BOB;ROBERT;",result);

        // Now delete another one at lvl3
        jtCacheLvl3.deleteRecursive(toBytes("BOB"));
        result = getKeysFrom(jtCacheLvl3);
        assertEquals("ALICE;ROBERT;",result);

        // Now add two more at lvl4
        jtCacheLvl4.put("CAROL",toBytes("carol"));
        jtCacheLvl4.put("DENISE",toBytes("denise"));
        result = getKeysFrom(jtCacheLvl4);
        assertEquals("ALICE;CAROL;DENISE;ROBERT;",result);
        // recheck lvl3 before committing
        result = getKeysFrom(jtCacheLvl3);
        assertEquals("ALICE;ROBERT;",result);
        // commit lvl4
        jtCacheLvl4.commit();
        // recheck lvl3 after committing
        result = getKeysFrom(jtCacheLvl3);
        assertEquals("ALICE;CAROL;DENISE;ROBERT;",result);

        // recheck lvl2 before committing
        result = getKeysFrom(jtCacheLvl2);
        assertEquals("ALICE;BOB;ROBERT;",result);
        // commit lvl3
        jtCacheLvl3.commit();
        // recheck lvl2 after committing
        result = getKeysFrom(jtCacheLvl2);
        assertEquals("ALICE;CAROL;DENISE;ROBERT;",result);

        // recheck base and lvl1 before rollback
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;",result);
        result = getKeysFrom(jtCacheLvl1);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
        // rollback lvl2
        jtCacheLvl2.rollback();
        // recheck base and lvl1 after rollback
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;",result);
        result = getKeysFrom(jtCacheLvl1);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);

        jtCacheLvl1.commit();
        // Now the base trie must have all
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;BOB;CAROL;ROBERT;",result);
    }

    /**
     * Check a JournalTrieCache with 2 caching levels over a MutableTrieCache with 1 level
     */
    @Test
    public void testJournalCacheOverMutableTrieCache() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        // First put some strings in the base
        baseMutableTrie.put("ALICE",toBytes("alice"));
        baseMutableTrie.put("BOB",toBytes("bob"));
        JournalTrieCache mtCacheLvl1 = new JournalTrieCache(baseMutableTrie);
        JournalTrieCache jtCacheLvl2 = new JournalTrieCache(mtCacheLvl1);
        JournalTrieCache jtCacheLvl3 = new JournalTrieCache(jtCacheLvl2);

        // Now add two more
        mtCacheLvl1.put("ALICE",toBytes("ecila"));
        jtCacheLvl2.put("CAROL",toBytes("carol"));
        jtCacheLvl2.deleteRecursive(toBytes("BOB"));
        jtCacheLvl3.put("ROBERT",toBytes("robert"));

        jtCacheLvl3.save();
        byte[] hashToRetrieve = baseMutableTrie.getTrie().getHash().getBytes();
        Trie retrievedTrie = trieStore.retrieve(hashToRetrieve).get();
        baseMutableTrie = new MutableTrieImpl(trieStore, retrievedTrie);
        String result;
        result = getKeysFrom(baseMutableTrie);
        assertEquals("ALICE;CAROL;ROBERT;",result);
        assertArrayEquals(toBytes("ecila"), baseMutableTrie.get(toBytes("ALICE")));
        assertNull(baseMutableTrie.get(toBytes("BOB")));
        assertArrayEquals(toBytes("carol"), baseMutableTrie.get(toBytes("CAROL")));
        assertArrayEquals(toBytes("robert"), baseMutableTrie.get(toBytes("ROBERT")));
    }

    private void getValueHashAndAssert(MutableTrie trie, byte[] key, Keccak256 expectedHash) {
        Keccak256 hash = trie.getValueHash(key);
        assertEquals(expectedHash, hash);
    }

}