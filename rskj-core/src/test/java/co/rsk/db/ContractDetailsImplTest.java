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

package co.rsk.db;

import co.rsk.config.TestSystemProperties;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.*;

import static org.ethereum.TestUtils.randomBytes;
import static org.ethereum.TestUtils.randomDataWord;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Created by ajlopez on 05/04/2017.
 */
public class ContractDetailsImplTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final int IN_MEMORY_STORAGE_LIMIT = config.detailsInMemoryStorageLimit();

    @Test
    public void getNullFromUnusedAddress() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertEquals(null, details.get(DataWord.ONE));
    }

    @Test
    public void newContractDetailsIsClean() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertFalse(details.isDirty());
    }

    @Test
    public void hasExternalStorage() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertTrue(details.hasExternalStorage());
    }

    @Test
    public void hasExternalStorageIfHasEnoughtKeys() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        int nkeys = IN_MEMORY_STORAGE_LIMIT;

        for (int k = 1; k <= nkeys + 1; k++)
            details.put(new DataWord(k), new DataWord(k * 2));

        Assert.assertTrue(details.hasExternalStorage());
    }

    @Test
    public void hasExternalStorageIfHasEnoughtBytesKeys() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        int nkeys = IN_MEMORY_STORAGE_LIMIT;

        for (int k = 1; k <= nkeys + 1; k++)
            details.putBytes(new DataWord(k), randomData());

        Assert.assertTrue(details.hasExternalStorage());
    }

    @Test
    public void setDirty() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setDirty(true);
        Assert.assertTrue(details.isDirty());
    }

    @Test
    public void newContractDetailsIsNotDeleted() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertFalse(details.isDeleted());
    }

    @Test
    public void setDeleted() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setDeleted(true);
        Assert.assertTrue(details.isDeleted());
    }

    @Test
    public void putAndGetDataWord() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ONE, new DataWord(42));

        Assert.assertEquals(new DataWord(42), details.get(DataWord.ONE));
        Assert.assertTrue(details.isDirty());
        Assert.assertEquals(1, details.getStorageSize());
    }

    @Test
    public void putDataWordWithoutLeadingZeroes() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ONE, new DataWord(42));

        Trie trie = details.getTrie();

        byte[] value = trie.get(DataWord.ONE.getData());

        Assert.assertNotNull(value);
        Assert.assertEquals(1, value.length);
        Assert.assertEquals(42, value[0]);
        Assert.assertEquals(1, details.getStorageSize());
    }

    @Test
    public void putDataWordZeroAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ONE, new DataWord(42));
        details.put(DataWord.ONE, DataWord.ZERO);

        Trie trie = details.getTrie();

        byte[] value = trie.get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void getNullBytesFromUnusedAddress() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertNull(details.getBytes(DataWord.ONE));
    }

    @Test
    public void putAndGetBytes() {
        byte[] value = new byte[] { 0x01, 0x02, 0x03 };

        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.putBytes(DataWord.ONE, value);

        Assert.assertArrayEquals(value, details.getBytes(DataWord.ONE));
        Assert.assertTrue(details.isDirty());
        Assert.assertEquals(1, details.getStorageSize());
    }

    @Test
    public void putNullValueAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.putBytes(DataWord.ONE, new byte[] { 0x01, 0x02, 0x03 });
        details.putBytes(DataWord.ONE, null);

        Trie trie = details.getTrie();

        byte[] value = trie.get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void getStorageRoot() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ONE, new DataWord(42));
        details.put(DataWord.ZERO, new DataWord(1));

        Trie trie = details.getTrie();

        Assert.assertNotNull(trie.getHash().getBytes());

        Assert.assertArrayEquals(trie.getHash().getBytes(), details.getStorageHash());
    }

    @Test
    public void getNullCode() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertNull(details.getCode());
    }

    @Test
    public void setAndGetCode() {
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };

        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setCode(code);

        Assert.assertArrayEquals(code, details.getCode());
    }

    @Test
    public void getStorageSizeInEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void getStorageSizeInNonEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ZERO, DataWord.ONE);
        details.put(DataWord.ONE, new DataWord(42));

        Assert.assertEquals(2, details.getStorageSize());
    }

    @Test
    public void getStorageKeysInNonEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ZERO, DataWord.ONE);
        details.put(DataWord.ONE, new DataWord(42));

        Set<DataWord> keys = details.getStorageKeys();

        Assert.assertNotNull(keys);
        Assert.assertEquals(2, keys.size());
        Assert.assertTrue(keys.contains(DataWord.ZERO));
        Assert.assertTrue(keys.contains(DataWord.ONE));
    }

    @Test
    public void getStorageKeysAfterDelete() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ZERO, DataWord.ONE);
        details.put(DataWord.ONE, new DataWord(42));
        details.put(DataWord.ONE, DataWord.ZERO);

        Set<DataWord> keys = details.getStorageKeys();

        Assert.assertNotNull(keys);
        Assert.assertEquals(1, keys.size());
        Assert.assertTrue(keys.contains(DataWord.ZERO));
    }

    @Test
    public void getStorageFromEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Map<DataWord, DataWord> map = details.getStorage();

        Assert.assertNotNull(map);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void getStorageUsingNullFromEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Map<DataWord, DataWord> map = details.getStorage(null);

        Assert.assertNotNull(map);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void getStorageFromNonEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ZERO, DataWord.ONE);
        details.put(DataWord.ONE, new DataWord(42));

        Map<DataWord, DataWord> map = details.getStorage();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());
        Assert.assertEquals(2, map.size());
        Assert.assertTrue(map.containsKey(DataWord.ZERO));
        Assert.assertTrue(map.containsKey(DataWord.ONE));

        Assert.assertEquals(DataWord.ONE, map.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(42), map.get(DataWord.ONE));
    }

    @Test
    public void getStorageFromNonEmptyDetailsUsingKeys() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ZERO, DataWord.ONE);
        details.put(DataWord.ONE, new DataWord(42));
        details.put(new DataWord(3), new DataWord(144));

        Collection<DataWord> keys = new HashSet<>();

        keys.add(DataWord.ZERO);
        keys.add(new DataWord(3));

        Map<DataWord, DataWord> map = details.getStorage(keys);

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());

        Assert.assertEquals(2, map.size());

        Assert.assertTrue(map.containsKey(DataWord.ZERO));
        Assert.assertTrue(map.containsKey(new DataWord(3)));

        Assert.assertEquals(DataWord.ONE, map.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(144), map.get(new DataWord(3)));
    }

    @Test
    public void getNullAddress() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertNull(details.getAddress());
    }

    @Test
    public void setAndGetAddress() {
        byte[] address = new byte[] { 0x01, 0x02, 0x03 };

        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setAddress(address);

        byte[] result = details.getAddress();

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(address, result);
    }

    @Test
    public void newContractDetailsIsNullObject() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Assert.assertTrue(details.isNullObject());
    }

    @Test
    public void newContractDetailsWithEmptyCodeIsNullObject() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setCode(new byte[0]);

        Assert.assertTrue(details.isNullObject());
    }

    @Test
    public void contractDetailsWithNonEmptyCodeIsNotNullObject() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.setCode(new byte[] { 0x01, 0x02, 0x03 });

        Assert.assertFalse(details.isNullObject());
    }

    @Test
    public void contractDetailsWithStorageDataIsNotNullObject() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.put(DataWord.ONE, new DataWord(42));

        Assert.assertFalse(details.isNullObject());
    }

    @Test
    public void setStorageUsingMap() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        Map<DataWord, DataWord> map = new HashMap<>();

        map.put(DataWord.ZERO, new DataWord(42));
        map.put(DataWord.ONE, new DataWord(144));

        details.setStorage(map);

        Assert.assertEquals(new DataWord(42), details.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(144), details.get(DataWord.ONE));
    }

    @Test
    public void getSnapshot() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        byte[] initialRoot = details.getStorageHash();

        Map<DataWord, DataWord> firstStorage = new HashMap<>();
        firstStorage.put(DataWord.ZERO, new DataWord(42));
        firstStorage.put(DataWord.ONE, new DataWord(144));
        details.setStorage(firstStorage);

        byte[] root = details.getStorageHash();

        Map<DataWord, DataWord> secondStorage = new HashMap<>();

        secondStorage.put(new DataWord(2), new DataWord(1));
        secondStorage.put(new DataWord(3), new DataWord(2));

        details.setStorage(secondStorage);

        ContractDetails result = details.getSnapshotTo(root);

        Assert.assertEquals(new DataWord(42), result.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(144), result.get(DataWord.ONE));
        Assert.assertEquals(null, result.get(new DataWord(2)));
        Assert.assertEquals(null, result.get(new DataWord(3)));

        ContractDetails result2 = details.getSnapshotTo(initialRoot);

        Assert.assertEquals(null, result2.get(DataWord.ZERO));
        Assert.assertEquals(null, result2.get(DataWord.ONE));
        Assert.assertEquals(null, result2.get(new DataWord(2)));
        Assert.assertEquals(null, result2.get(new DataWord(3)));
    }

    @Test
    public void getEncodedAndCreateClone() {
        HashMapDB store = new HashMapDB();
        ContractDetailsImpl details = buildContractDetails(store);

        Map<DataWord, DataWord> storage = new HashMap<>();
        storage.put(DataWord.ZERO, new DataWord(42));
        storage.put(DataWord.ONE, new DataWord(144));

        details.setStorage(storage);
        details.syncStorage();

        byte[] encoded = details.getEncoded();

        Assert.assertNotNull(encoded);

        ContractDetailsImpl result = new ContractDetailsImpl(encoded, new TrieStorePoolOnMemory(() -> store), config.detailsInMemoryStorageLimit());

        Assert.assertEquals(new DataWord(42), result.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(144), result.get(DataWord.ONE));
        Assert.assertEquals(null, result.get(new DataWord(2)));
        Assert.assertEquals(null, result.get(new DataWord(3)));
    }

    @Test
    public void getOldEncodedAndCreateClone() {
        HashMapDB store = new HashMapDB();
        ContractDetailsImpl details = buildContractDetails(store);

        Map<DataWord, DataWord> storage = new HashMap<>();
        storage.put(DataWord.ZERO, new DataWord(42));
        storage.put(DataWord.ONE, new DataWord(144));

        details.setStorage(storage);

        byte[] encoded = details.getEncodedOldFormat();

        Assert.assertNotNull(encoded);

        ContractDetailsImpl result = new ContractDetailsImpl(encoded, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        Assert.assertEquals(new DataWord(42), result.get(DataWord.ZERO));
        Assert.assertEquals(new DataWord(144), result.get(DataWord.ONE));
        Assert.assertEquals(null, result.get(new DataWord(2)));
        Assert.assertEquals(null, result.get(new DataWord(3)));

        Assert.assertArrayEquals(details.getStorageHash(), result.getStorageHash());
    }

    @Test
    public void getOldEncodedWithEmptyStorageAndCreateClone() {
        HashMapDB store = new HashMapDB();
        ContractDetailsImpl details = buildContractDetails(store);

        byte[] encoded = details.getEncodedOldFormat();

        Assert.assertNotNull(encoded);

        ContractDetailsImpl result = new ContractDetailsImpl(encoded, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        Assert.assertArrayEquals(details.getStorageHash(), result.getStorageHash());
    }

    @Test
    public void getOldEncodedAndCreateCloneSettingStorage() {
        HashMapDB store = new HashMapDB();
        ContractDetailsImpl details = buildContractDetails(store);

        DataWord value0 = new DataWord(42);
        DataWord value1 = new DataWord(144);

        details.put(DataWord.ZERO, value0);
        details.put(DataWord.ONE, value1);

        byte[] hash1 = details.getStorageHash();

        details.put(new DataWord(10), value0);
        details.put(new DataWord(11), value1);

        byte[] hash2 = details.getStorageHash();

        byte[] encoded = details.getEncodedOldFormat();

        Assert.assertNotNull(encoded);

        ContractDetailsImpl result = new ContractDetailsImpl(encoded, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        ContractDetails result1 = result.getSnapshotTo(hash1);

        Assert.assertNotNull(result1);
        Assert.assertArrayEquals(hash1, result1.getStorageHash());

        ContractDetails result2 = result.getSnapshotTo(hash2);

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(hash2, result2.getStorageHash());
    }

    @Test
    public void syncStorageInEmptyDetails() {
        ContractDetailsImpl details = buildContractDetails(new HashMapDB());

        details.syncStorage();
    }

    @Test
    public void syncStorageInDetailsWithTrieInMemory() {
        HashMapDB store = new HashMapDB();
        Trie trie = new TrieImpl(new TrieStoreImpl(store), false);
        byte[] accountAddress = randomAddress();
        ContractDetailsImpl details = new ContractDetailsImpl(accountAddress, trie, null, new TrieStorePoolOnMemory(() -> store), config.detailsInMemoryStorageLimit());

        details.put(new DataWord(42), DataWord.ONE);

        details.syncStorage();

        Assert.assertNotNull(details.get(new DataWord(42)));
    }

    @Test
    public void usingSameExternalStorage() {
        HashMapDB store = new HashMapDB();
        Trie trie = new TrieImpl(new TrieStoreImpl(store), false);
        byte[] accountAddress = randomAddress();
        ContractDetailsImpl details = new ContractDetailsImpl(accountAddress, trie, null, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        int nkeys = IN_MEMORY_STORAGE_LIMIT;

        for (int k = 1; k <= nkeys + 1; k++)
            details.put(new DataWord(k), new DataWord(k * 2));

        Assert.assertTrue(details.hasExternalStorage());

        details.syncStorage();

        TrieStore.Pool pool = new TrieStorePoolOnMemory(() -> store);

        ContractDetailsImpl details1 = new ContractDetailsImpl(details.getEncoded(), pool, config.detailsInMemoryStorageLimit());
        ContractDetailsImpl details2 = new ContractDetailsImpl(details.getEncoded(), pool, config.detailsInMemoryStorageLimit());

        Assert.assertTrue(details1.hasExternalStorage());
        Assert.assertTrue(details2.hasExternalStorage());

        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(details1.get(new DataWord(k)));
        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(details2.get(new DataWord(k)));

        details1.syncStorage();
        details2.syncStorage();
    }

    @Test
    public void syncStorageWithExternalStorage() {
        TrieStorePoolOnMemory trieStorePool = new TrieStorePoolOnMemory();
        byte[] accountAddress = randomAddress();
        String storeName = "details-storage/" + toHexString(accountAddress);
        Trie trie = new TrieImpl(trieStorePool.getInstanceFor(storeName), false);
        ContractDetailsImpl details = new ContractDetailsImpl(accountAddress, trie, null, trieStorePool, config.detailsInMemoryStorageLimit());

        int nkeys = IN_MEMORY_STORAGE_LIMIT;

        for (int k = 1; k <= nkeys + 1; k++)
            details.put(new DataWord(k), new DataWord(k * 2));

        Assert.assertTrue(details.hasExternalStorage());

        details.syncStorage();

        int ssize = details.getStorageSize();

        details = new ContractDetailsImpl(details.getEncoded(), trieStorePool, config.detailsInMemoryStorageLimit());

        Assert.assertEquals(ssize, details.getStorageSize());

        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(details.get(new DataWord(k)));

        ContractDetailsImpl clone = new ContractDetailsImpl(details.getEncoded(), trieStorePool, config.detailsInMemoryStorageLimit());

        Assert.assertNotNull(clone);
        Assert.assertTrue(clone.hasExternalStorage());
        Assert.assertEquals(details.getStorageSize(), clone.getStorageSize());

        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(clone.get(new DataWord(k)));

        for (int k = 1; k <= nkeys + 1; k++)
            clone.put(new DataWord(k), new DataWord(k * 3));

        Assert.assertTrue(clone.hasExternalStorage());
        Assert.assertEquals(details.getStorageSize(), clone.getStorageSize());

        ContractDetailsImpl snapshot = (ContractDetailsImpl)clone.getSnapshotTo(clone.getStorageHash());
        Assert.assertTrue(snapshot.hasExternalStorage());
        Assert.assertEquals(clone.getStorageSize(), snapshot.getStorageSize());
    }

    @Test
    public void syncStorageAndGetKeyValues() {
        HashMapDB store = new HashMapDB();
        Trie trie = new TrieImpl(new TrieStoreImpl(store), false);
        byte[] accountAddress = randomAddress();
        ContractDetailsImpl details = new ContractDetailsImpl(accountAddress, trie, null, new TrieStorePoolOnMemory(() -> store), config.detailsInMemoryStorageLimit());

        int nkeys = IN_MEMORY_STORAGE_LIMIT;

        for (int k = 1; k <= nkeys + 1; k++)
            details.put(new DataWord(k), new DataWord(k * 2));

        Assert.assertTrue(details.hasExternalStorage());

        details.syncStorage();

        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(details.get(new DataWord(k)));

        ContractDetailsImpl clone = new ContractDetailsImpl(details.getEncoded(), new TrieStorePoolOnMemory(() -> store), config.detailsInMemoryStorageLimit());

        Assert.assertNotNull(clone);
        Assert.assertTrue(clone.hasExternalStorage());
        Assert.assertEquals(details.getStorageSize(), clone.getStorageSize());

        for (int k = 1; k <= nkeys + 1; k++)
            Assert.assertNotNull(clone.get(new DataWord(k)));

        for (int k = 1; k <= nkeys + 1; k++)
            clone.put(new DataWord(k), new DataWord(k * 3));

        Assert.assertTrue(clone.hasExternalStorage());
        Assert.assertEquals(details.getStorageSize(), clone.getStorageSize());

        ContractDetailsImpl snapshot = (ContractDetailsImpl)clone.getSnapshotTo(clone.getStorageHash());
        Assert.assertTrue(snapshot.hasExternalStorage());
        Assert.assertEquals(clone.getStorageSize(), snapshot.getStorageSize());
    }

    @Test @Ignore("From now on we always save to external storage")
    public void testExternalStorageSerialization() {
        byte[] address = randomAddress();
        byte[] code = randomBytes(512);
        Map<DataWord, DataWord> elements = new HashMap<>();

        HashMapDB externalStorage = new HashMapDB();

        ContractDetailsImpl original = new ContractDetailsImpl(address, new TrieImpl(new TrieStoreImpl(externalStorage), true), code, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit(
        ));

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT + 10; i++) {
            DataWord key = randomDataWord();
            DataWord value = randomDataWord();

            elements.put(key, value);
            original.put(key, value);
        }

        original.syncStorage();

        byte[] rlp = original.getEncoded();

        ContractDetailsImpl deserialized = new ContractDetailsImpl(rlp, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        Assert.assertEquals(toHexString(address), toHexString(deserialized.getAddress()));
        Assert.assertEquals(toHexString(code), toHexString(deserialized.getCode()));

        Map<DataWord, DataWord> storage = deserialized.getStorage();
        Assert.assertEquals(elements.size(), storage.size());
        for (DataWord key : elements.keySet()) {
            Assert.assertEquals(elements.get(key), storage.get(key));
        }

        DataWord deletedKey = elements.keySet().iterator().next();

        deserialized.put(deletedKey, DataWord.ZERO);
        deserialized.put(randomDataWord(), DataWord.ZERO);
    }

    @Test
    public void externalStorageTransition() {
        byte[] address = randomAddress();
        byte[] code = randomBytes(512);
        Map<DataWord, DataWord> elements = new HashMap<>();

        HashMapDB externalStorage = new HashMapDB();

        ContractDetailsImpl original = new ContractDetailsImpl(address, new TrieImpl(new TrieStoreImpl(externalStorage), true), code, new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT - 1; i++) {
            DataWord key = randomDataWord();
            DataWord value = randomDataWord();

            elements.put(key, value);
            original.put(key, value);
        }

        original.syncStorage();

        ContractDetails deserialized = new ContractDetailsImpl(original.getEncoded(), new TrieStorePoolOnMemory(() -> externalStorage), config.detailsInMemoryStorageLimit());

        // adds keys for in-memory storage limit overflow
        for (int i = 0; i < 10; i++) {
            DataWord key = randomDataWord();
            DataWord value = randomDataWord();

            elements.put(key, value);
            deserialized.put(key, value);
        }

        deserialized.syncStorage();

        deserialized = new ContractDetailsImpl(deserialized.getEncoded(), new TrieStorePoolOnMemory(() -> externalStorage), config.detailsInMemoryStorageLimit());

        Map<DataWord, DataWord> storage = deserialized.getStorage();
        Assert.assertEquals(elements.size(), storage.size());
        for (DataWord key : elements.keySet()) {
            Assert.assertEquals(elements.get(key), storage.get(key));
        }
    }

    @Test
    public void test_1(){

        byte[] code = Hex.decode("60016002");

        byte[] key_1 = Hex.decode("111111");
        byte[] val_1 = Hex.decode("aaaaaa");

        byte[] key_2 = Hex.decode("222222");
        byte[] val_2 = Hex.decode("bbbbbb");

        HashMapDB store = new HashMapDB();
        TrieStore.Pool pool = new TrieStorePoolOnMemory(() -> store);

        ContractDetailsImpl contractDetails = buildContractDetails(pool);
        contractDetails.setCode(code);
        contractDetails.put(new DataWord(key_1), new DataWord(val_1));
        contractDetails.put(new DataWord(key_2), new DataWord(val_2));
        contractDetails.syncStorage();

        byte[] data = contractDetails.getEncoded();

        ContractDetailsImpl contractDetails_ = new ContractDetailsImpl(data, pool, config.detailsInMemoryStorageLimit());

        Assert.assertEquals(Hex.toHexString(code),
                Hex.toHexString(contractDetails_.getCode()));

        Assert.assertEquals(Hex.toHexString(val_1),
                Hex.toHexString(contractDetails_.get(new DataWord(key_1)).getNoLeadZeroesData()));

        Assert.assertEquals(Hex.toHexString(val_2),
                Hex.toHexString(contractDetails_.get(new DataWord(key_2)).getNoLeadZeroesData()));
    }

    @Test
    public void test_2(){

        byte[] code = Hex.decode("7c0100000000000000000000000000000000000000000000000000000000600035046333d546748114610065578063430fe5f01461007c5780634d432c1d1461008d578063501385b2146100b857806357eb3b30146100e9578063dbc7df61146100fb57005b6100766004356024356044356102f0565b60006000f35b61008760043561039e565b60006000f35b610098600435610178565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100c96004356024356044356101a0565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100f1610171565b8060005260206000f35b610106600435610133565b8360005282602052816040528073ffffffffffffffffffffffffffffffffffffffff1660605260806000f35b5b60006020819052908152604090208054600182015460028301546003909301549192909173ffffffffffffffffffffffffffffffffffffffff1684565b5b60015481565b5b60026020526000908152604090205473ffffffffffffffffffffffffffffffffffffffff1681565b73ffffffffffffffffffffffffffffffffffffffff831660009081526020819052604081206002015481908302341080156101fe575073ffffffffffffffffffffffffffffffffffffffff8516600090815260208190526040812054145b8015610232575073ffffffffffffffffffffffffffffffffffffffff85166000908152602081905260409020600101548390105b61023b57610243565b3391506102e8565b6101966103ca60003973ffffffffffffffffffffffffffffffffffffffff3381166101965285166101b68190526000908152602081905260408120600201546101d6526101f68490526102169080f073ffffffffffffffffffffffffffffffffffffffff8616600090815260208190526040902060030180547fffffffffffffffffffffffff0000000000000000000000000000000000000000168217905591508190505b509392505050565b73ffffffffffffffffffffffffffffffffffffffff33166000908152602081905260408120548190821461032357610364565b60018054808201909155600090815260026020526040902080547fffffffffffffffffffffffff000000000000000000000000000000000000000016331790555b50503373ffffffffffffffffffffffffffffffffffffffff1660009081526020819052604090209081556001810192909255600290910155565b3373ffffffffffffffffffffffffffffffffffffffff166000908152602081905260409020600201555600608061019660043960048051602451604451606451600080547fffffffffffffffffffffffff0000000000000000000000000000000000000000908116909517815560018054909516909317909355600355915561013390819061006390396000f3007c0100000000000000000000000000000000000000000000000000000000600035046347810fe381146100445780637e4a1aa81461005557806383d2421b1461006957005b61004f6004356100ab565b60006000f35b6100636004356024356100fc565b60006000f35b61007460043561007a565b60006000f35b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146100a2576100a8565b60078190555b50565b73ffffffffffffffffffffffffffffffffffffffff8116600090815260026020526040902080547fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0016600117905550565b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146101245761012f565b600582905560068190555b505056");
        byte[] address = randomBytes(32);

        byte[] key_0 = Hex.decode("39a2338cbc13ff8523a9b1c9bc421b7518d63b70aa690ad37cb50908746c9a55");
        byte[] val_0 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000064");

        byte[] key_1 = Hex.decode("39a2338cbc13ff8523a9b1c9bc421b7518d63b70aa690ad37cb50908746c9a56");
        byte[] val_1 = Hex.decode("000000000000000000000000000000000000000000000000000000000000000c");

        byte[] key_2 = Hex.decode("4effac3ed62305246f40d058e1a9a8925a448d1967513482947d1d3f6104316f");
        byte[] val_2 = Hex.decode("7a65703300000000000000000000000000000000000000000000000000000000");

        byte[] key_3 = Hex.decode("4effac3ed62305246f40d058e1a9a8925a448d1967513482947d1d3f61043171");
        byte[] val_3 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000014");

        byte[] key_4 = Hex.decode("39a2338cbc13ff8523a9b1c9bc421b7518d63b70aa690ad37cb50908746c9a54");
        byte[] val_4 = Hex.decode("7a65703200000000000000000000000000000000000000000000000000000000");

        byte[] key_5 = Hex.decode("4effac3ed62305246f40d058e1a9a8925a448d1967513482947d1d3f61043170");
        byte[] val_5 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000078");

        byte[] key_6 = Hex.decode("e90b7bceb6e7df5418fb78d8ee546e97c83a08bbccc01a0644d599ccd2a7c2e0");
        byte[] val_6 = Hex.decode("00000000000000000000000010b426278fbec874791c4e3f9f48a59a44686efe");

        byte[] key_7 = Hex.decode("0df3cc3597c5ede0b1448e94daf1f1445aa541c6c03f602a426f04ae47508bb8");
        byte[] val_7 = Hex.decode("7a65703100000000000000000000000000000000000000000000000000000000");

        byte[] key_8 = Hex.decode("0df3cc3597c5ede0b1448e94daf1f1445aa541c6c03f602a426f04ae47508bb9");
        byte[] val_8 = Hex.decode("00000000000000000000000000000000000000000000000000000000000000c8");

        byte[] key_9 = Hex.decode("0df3cc3597c5ede0b1448e94daf1f1445aa541c6c03f602a426f04ae47508bba");
        byte[] val_9 = Hex.decode("000000000000000000000000000000000000000000000000000000000000000a");

        byte[] key_10 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001");
        byte[] val_10 = Hex.decode("0000000000000000000000000000000000000000000000000000000000000003");

        byte[] key_11 = Hex.decode("0df3cc3597c5ede0b1448e94daf1f1445aa541c6c03f602a426f04ae47508bbb");
        byte[] val_11 = Hex.decode("0000000000000000000000007cd917d6194bcfc3670d8a1613e5b0c790036a35");

        byte[] key_12 = Hex.decode("679795a0195a1b76cdebb7c51d74e058aee92919b8c3389af86ef24535e8a28c");
        byte[] val_12 = Hex.decode("000000000000000000000000b0b0a72fcfe293a85bef5915e1a7acb37bf0c685");

        byte[] key_13 = Hex.decode("ac33ff75c19e70fe83507db0d683fd3465c996598dc972688b7ace676c89077b");
        byte[] val_13 = Hex.decode("0000000000000000000000000c6686f3d6ee27e285f2de7b68e8db25cf1b1063");


        HashMapDB store = new HashMapDB();
        ContractDetailsImpl contractDetails = buildContractDetails(store);
        contractDetails.setCode(code);
        contractDetails.setAddress(address);
        contractDetails.put(new DataWord(key_0), new DataWord(val_0));
        contractDetails.put(new DataWord(key_1), new DataWord(val_1));
        contractDetails.put(new DataWord(key_2), new DataWord(val_2));
        contractDetails.put(new DataWord(key_3), new DataWord(val_3));
        contractDetails.put(new DataWord(key_4), new DataWord(val_4));
        contractDetails.put(new DataWord(key_5), new DataWord(val_5));
        contractDetails.put(new DataWord(key_6), new DataWord(val_6));
        contractDetails.put(new DataWord(key_7), new DataWord(val_7));
        contractDetails.put(new DataWord(key_8), new DataWord(val_8));
        contractDetails.put(new DataWord(key_9), new DataWord(val_9));
        contractDetails.put(new DataWord(key_10), new DataWord(val_10));
        contractDetails.put(new DataWord(key_11), new DataWord(val_11));
        contractDetails.put(new DataWord(key_12), new DataWord(val_12));
        contractDetails.put(new DataWord(key_13), new DataWord(val_13));
        contractDetails.syncStorage();

        byte[] data = contractDetails.getEncoded();

        ContractDetailsImpl contractDetails_ = new ContractDetailsImpl(data, new TrieStorePoolOnMemory(() -> store), config.detailsInMemoryStorageLimit());

        Assert.assertEquals(Hex.toHexString(code),
                Hex.toHexString(contractDetails_.getCode()));

        Assert.assertEquals(Hex.toHexString(address),
                Hex.toHexString(contractDetails_.getAddress()));

        Assert.assertEquals(Hex.toHexString(val_1),
                Hex.toHexString(contractDetails_.get(new DataWord(key_1)).getData()));

        Assert.assertEquals(Hex.toHexString(val_2),
                Hex.toHexString(contractDetails_.get(new DataWord(key_2)).getData()));

        Assert.assertEquals(Hex.toHexString(val_3),
                Hex.toHexString(contractDetails_.get(new DataWord(key_3)).getData()));

        Assert.assertEquals(Hex.toHexString(val_4),
                Hex.toHexString(contractDetails_.get(new DataWord(key_4)).getData()));

        Assert.assertEquals(Hex.toHexString(val_5),
                Hex.toHexString(contractDetails_.get(new DataWord(key_5)).getData()));

        Assert.assertEquals(Hex.toHexString(val_6),
                Hex.toHexString(contractDetails_.get(new DataWord(key_6)).getData()));

        Assert.assertEquals(Hex.toHexString(val_7),
                Hex.toHexString(contractDetails_.get(new DataWord(key_7)).getData()));

        Assert.assertEquals(Hex.toHexString(val_8),
                Hex.toHexString(contractDetails_.get(new DataWord(key_8)).getData()));

        Assert.assertEquals(Hex.toHexString(val_9),
                Hex.toHexString(contractDetails_.get(new DataWord(key_9)).getData()));

        Assert.assertEquals(Hex.toHexString(val_10),
                Hex.toHexString(contractDetails_.get(new DataWord(key_10)).getData()));

        Assert.assertEquals(Hex.toHexString(val_11),
                Hex.toHexString(contractDetails_.get(new DataWord(key_11)).getData()));

        Assert.assertEquals(Hex.toHexString(val_12),
                Hex.toHexString(contractDetails_.get(new DataWord(key_12)).getData()));

        Assert.assertEquals(Hex.toHexString(val_13),
                Hex.toHexString(contractDetails_.get(new DataWord(key_13)).getData()));
    }

    private static byte[] randomData() {
        byte[] bytes = new byte[32];

        new Random().nextBytes(bytes);

        return bytes;
    }

    private static byte[] randomAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return bytes;
    }

    private ContractDetailsImpl buildContractDetails(HashMapDB store) {
        return new ContractDetailsImpl(
                null,
                new TrieImpl(new TrieStoreImpl(store), true),
                null,
                new TrieStorePoolOnMemory(),
                config.detailsInMemoryStorageLimit()
        );
    }

    private ContractDetailsImpl buildContractDetails(TrieStore.Pool pool) {
        return new ContractDetailsImpl(
                null,
                new TrieImpl(pool.getInstanceFor(null), true),
                null,
                pool,
                config.detailsInMemoryStorageLimit()
        );
    }
}
