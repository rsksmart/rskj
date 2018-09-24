package co.rsk.db;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.helpers.PerformanceTestHelper;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;

public class RepositoryPerformanceTest {
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

    @Ignore
    @Test
    public void testAccountCreation(  ) {
        Repository repository = createRepository(true);
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

    @Ignore
    @Test
    public void testStorageRowsCreation() {
        Repository repository = createRepository(true);
        subtestStorageRowsCreation(repository ,false);
    }

    @Ignore
    @Test
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

    @Ignore
    @Test
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

    @Ignore
    @Test
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

    @Ignore
    @Test

    public void testIncludeDeletionsInBatch() {
        LevelDbDataSource  ds = new LevelDbDataSource("test-storage-rows",config.databaseDir());
        ds.init();

        Set<byte[]> keysOrigin = ds.keys();
        ds.put("test".getBytes(),"value".getBytes());
        Set<byte[]> keys1 = ds.keys();
        Map<byte[], byte[]> rows = new HashMap<>();

        // Testing empty for deletion.
        rows.put("test".getBytes() ,empty);

        // this throwed an exception. Now it doesn't because it implements manual deletion
        // for each item in the batch that has null value.

        ds.updateBatch(rows);
        Set<byte[]> keys2 = ds.keys();

    }

    public static Repository createRepository(boolean isSecure) {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl(isSecure))));
    }

    public static Repository createRepository(TrieStore store) {
        return new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));
    }
}
