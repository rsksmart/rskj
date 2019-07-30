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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.helpers.PerformanceTestHelper;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RepositoryPerformanceMetrics {
    private final TestSystemProperties config = new TestSystemProperties();
    final int createCount = 1000*1000;

    public void buildAccountCreationTest(Repository track ) {
        for(int t=0;t<createCount;t++) {
            RskAddress addr = TestUtils.randomAddress();
            track.createAccount(addr);
        }
    }

    public void subtestAccountCreation(Repository repository,boolean doSave) {
        PerformanceTestHelper pth = new PerformanceTestHelper();
        Repository track = repository.startTracking();
        pth.setup();
        pth.startMeasure();
        buildAccountCreationTest(track);
        pth.endMeasure("Accounts added"); // partial result

        System.out.println("Time per storage account added [uS]: "+
                PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));

        track.commit();
        pth.endMeasure("Accounts committed"); // final result

        System.out.println("Time per storage account added [uS]: "+
                PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));

        if (doSave) {
            repository.save();
            pth.endMeasure("Accounts saved"); // final result

            System.out.println("Time per storage account added [uS]: "+
                    PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));

        }
    }

    public void testAccountCreation(  ) {
        Repository repository = createRepositoryWithCache();
        subtestAccountCreation(repository,false);
    }

    public void buildStorageRowsCreationTest(Repository track ) {


        RskAddress addr = TestUtils.randomAddress();

        track.createAccount(addr);
        track.setupContract(addr);

        for(int t=0;t<createCount;t++) {
            track.addStorageRow(addr,TestUtils.randomDataWord(),TestUtils.randomDataWord());
        }

    }

    public void subtestStorageRowsCreation(Repository repository,boolean doSave) {
        PerformanceTestHelper pth = new PerformanceTestHelper();
        Repository track = repository.startTracking();
        pth.setup();
        pth.startMeasure();
        buildStorageRowsCreationTest(track);
        pth.endMeasure("Storage rows added"); // partial result

        System.out.println("Time per storage row added [uS]: "+
                PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));


        track.commit();
        pth.endMeasure("Storage rows committed"); // final result

        System.out.println("Time per storage row added [uS]: "+
                PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));

        if (doSave) {
            repository.save();
            pth.endMeasure("Accounts saved"); // final result

            System.out.println("Time per storage row added [uS]: "+
                    PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));

            repository.flush();
            pth.endMeasure("Accounts flushed"); // final result

            System.out.println("Time per storage row added [uS]: "+
                    PerformanceTestHelper.padLeft(pth.getDeltaRealTimeMillis()*1000/createCount));


        }
    }

    public void testStorageRowsCreation() {
        Repository repository = createRepositoryWithCache();
        subtestStorageRowsCreation(repository ,false);
    }

    public void testStorageRowsCreationAndSave() {
        TrieStoreImpl astore = new TrieStoreImpl(new HashMapDB());
        Repository repository = createRepository(astore);
        subtestStorageRowsCreation(repository ,true);
    }

    // Keys with null values ARE stored in hashmaps, but not in concurrenthashmaps !
    void testHashmap() {
        HashMap<ByteArrayWrapper,Integer> h = new HashMap();
        h.put(new ByteArrayWrapper("test".getBytes()),null);
        Integer r = h.get(new ByteArrayWrapper("test".getBytes()));
        int s = h.size();
        Set<ByteArrayWrapper> keySet = h.keySet();
    }
    static byte[] empty = new byte[]{};

    public void testEmptyPuts() {
        LevelDbDataSource  ds = new LevelDbDataSource("test-storage-rows",config.databaseDir());
        ds.init();

        Set<byte[]> keysOrigin = ds.keys();
        ds.put("test".getBytes(),"value".getBytes());

        // null puts are not allowed
        // empty does a delete() .
        ds.put("test".getBytes(),empty);

        Set<byte[]> keys = ds.keys();

    }

    public void testZeroArrayPuts() {
        LevelDbDataSource  ds = new LevelDbDataSource("test-storage-rows",config.databaseDir());
        ds.init();

        Set<byte[]> keysOrigin = ds.keys();
        ds.put("test".getBytes(),"value".getBytes());

        Set<byte[]> keys1 = ds.keys();

        // this does not remove the item!
        ds.put("test".getBytes(),new byte[]{});

        Set<byte[]> keys2 = ds.keys();
        ds.delete("test".getBytes());

        Set<byte[]> keys3 = ds.keys();

    }

    public void testIncludeDeletionsInBatch() {
        LevelDbDataSource  ds = new LevelDbDataSource("test-storage-rows",config.databaseDir());
        ds.init();

        Set<byte[]> keysOrigin = ds.keys();
        ds.put("test".getBytes(),"value".getBytes());
        Set<byte[]> keys1 = ds.keys();
        Map<ByteArrayWrapper, byte[]> rows = new HashMap<>();

        // Testing empty for deletion.
        rows.put(new ByteArrayWrapper("test".getBytes()), empty);

        // this throwed an exception. Now it doesn't because it implements manual deletion
        // for each item in the batch that has null value.

        ds.updateBatch(rows, Collections.emptySet());
        Set<byte[]> keys2 = ds.keys();

    }

    public static Repository createRepositoryWithCache() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static Repository createRepository(TrieStore store) {
        return new MutableRepository(new MutableTrieImpl(store, new Trie(store)));
    }
}
