package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by SerAdmin on 10/27/2018.
 */
public class DataSourceWithCache implements KeyValueDataSource {
    KeyValueDataSource base;
    HashMapDB cache;

    public DataSourceWithCache(KeyValueDataSource base) {
     this.base = base;
     cache = new HashMapDB();
    }

    public byte[] get(byte[] key) {
        byte[] r = cache.get(key);
        if (r!=null)
            return r;
        return base.get(key);

    }

    public byte[] put(byte[] key, byte[] value) {
        if (key == null || value == null) throw new NullPointerException();
        return cache.put(key,value);
    }

    public void delete(byte[] key) {
        if (key == null) throw new NullPointerException();
        cache.put(key,null); // null means delete
    }

    public Set<ByteArrayWrapper> keys() {
        HashSet<ByteArrayWrapper> result = new HashSet<>();
        result.addAll(base.keys());
        for (ByteArrayWrapper k : cache.keys()) {
            if (cache.get(k.getData()).length!=0)
                result.add(k);
            else
                result.remove(k.getData());
        }

        return result;


    }

    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows) {
        cache.updateBatch(rows);
    }

    public synchronized void flush() {
        base.updateBatch(cache.getStorageMap());
        cache.clear();
    }

    public String getName() {
        return base.getName()+"-with-cache";
    }

    public void init() {
        base.init();
        cache.init();

    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        flush();
        base.close();
        cache.close();
    }

}
