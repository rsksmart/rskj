package org.ethereum.datasource;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by SerAdmin on 10/27/2018.
 */
public class DataSourceWithCache implements KeyValueDataSource {
    KeyValueDataSource base;
    HashMapDB uncommittedCache;
    HashMapDB committedCache;

    // During the processing of a Raul's fully filled blockchain, the cache
    // is generating the following hits per block (average)
    // uncommitedCacheHits = 1340
    // commitedCacheHits = 248
    // Processing 134 blocks grows the commitedCache to 100K entries, or approximately
    // or 25 Mbytes. A cache of about 100 Mbytes seems rasonable. Anyway, for
    // precaution we'll set the limit to 100K entries.


    int uncommitedCacheHits;
    int commitedCacheHits;
    int totalReads;
    int totalWrites;
    int putSameValue;

    public DataSourceWithCache(KeyValueDataSource base,int cacheSize) {
     this.base = base;
     uncommittedCache = new HashMapDB();
     //
     committedCache =new HashMapDB(cacheSize,true);
    }

    public byte[] get(byte[] key) {
        byte[] r;
        totalReads++;

        r = committedCache.get(key);
        if (r!=null) {
            commitedCacheHits++;
            return r;
        }

        r = uncommittedCache.get(key);
        if (r!=null) {
            uncommitedCacheHits++;
            return r;
        }

        r = base.get(key);
        committedCache.put(key,r);
        return r;
    }

    public byte[] put(byte[] key, byte[] value) {
        if (key == null || value == null) throw new NullPointerException();
        totalWrites++;
        /**/
        // here I could check for equal datas or just move to the uncommited uncommittedCache.
        byte[] priorValue =committedCache.get(key);
        if (priorValue!=null) {
            if (Arrays.equals(priorValue,value)) {
                putSameValue++;
                return value;
            }
        }

        committedCache.delete(key);
        return uncommittedCache.put(key,value);
    }

    public void delete(byte[] key) {
        if (key == null) throw new NullPointerException();

        // fully delete this element
        committedCache.delete(key);

        // Here we MUST NOT use delete() because we have to mark that the
        // element should be deleted from the base.
        uncommittedCache.put(key,null);
    }

    private void addKeys(HashSet<ByteArrayWrapper> result, HashMapDB map) {
        for (ByteArrayWrapper k : map.keys()) {
            if (map.get(k.getData()).length!=0)
                result.add(k);
            else
                result.remove(k.getData());
        }
    }

    public Set<ByteArrayWrapper> keys() {
        HashSet<ByteArrayWrapper> result = new HashSet<>();
        result.addAll(base.keys());

        addKeys(result,committedCache);
        addKeys(result,uncommittedCache);
        return result;


    }

    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows) {
        // Remove from the commited set all elements in this batch
        committedCache.removeBatch(rows);
        uncommittedCache.updateBatch(rows);
    }

    public synchronized void flush() {
        // commited values need not be re-updated
        base.updateBatch(uncommittedCache.getStorageMap());

        // move all uncommited to commited. There should be no duplicated, by design.
        committedCache.addBatch(uncommittedCache.getStorageMap());
        uncommittedCache.clear();

        // Uncomment for debugging
        // dumpStats();
    }

    public void dumpStats() {
        System.out.println("uncommitedCacheHits: "+uncommitedCacheHits);
        System.out.println("commitedCacheHits: "+commitedCacheHits);
        System.out.println("totalReads: "+totalReads);
        System.out.println("putSameValue: "+putSameValue);
        System.out.println("totalWrites: +"+totalWrites);
    }

    public String getName() {
        return base.getName()+"-with-uncommittedCache";
    }

    public void init() {
        base.init();
        uncommittedCache.init();
        committedCache.init();

    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        flush();
        base.close();
        uncommittedCache.close();
        committedCache.close();
    }

}
