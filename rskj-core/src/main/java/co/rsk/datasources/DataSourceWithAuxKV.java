package co.rsk.datasources;

import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithAuxKV implements KeyValueDataSource {

    protected static final Logger logger = LoggerFactory.getLogger("datasourcewithauxkv");

    protected final ReadWriteLock dbLock = new ReentrantReadWriteLock();

    Path kvPath;
    KeyValueDataSource dsKV;
    protected Map<ByteArrayWrapper, byte[]> committedCache ;

    public boolean readOnly = false;
    boolean dump = false;

    long hits;
    long misses;
    long puts;
    long gets;
    String databaseName;

    public DataSourceWithAuxKV(String databaseName,boolean additionalKV) throws IOException {
        this.databaseName = databaseName;
        if (additionalKV) {
            kvPath = Paths.get(databaseName, "kv");

            // This normal LevelDV database is used to store non content-addressable KV pairs
            // using the kvPut() kvGet() methods
            dsKV = LevelDbDataSource.makeDataSource(kvPath);
        }
    }

    public byte[] kvGet(byte[] key) {
        Objects.requireNonNull(key);
        return dsKV.get(key);
    }

    public byte[] kvPut(byte[] key, byte[] value) {
        checkReadOnly();
        return dsKV.put(key, value);
    }


    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        gets++;

        value = committedCache.get(wrappedKey);
        if (value != null) {
            hits++;
        } else
            misses++;


        if (dump) {
            if (value != null)
                System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                        " value " +
                        ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
            else
                System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                        " failed");
        }
        return value;
    }

    public void checkReadOnly() {
        if (readOnly)
            throw new RuntimeException("read only DB");
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        checkReadOnly();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        return put(wrappedKey, value);
    }


    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        checkReadOnly();
        Objects.requireNonNull(value);
        puts++;
        if (dump) {
            System.out.println("Writing key " + wrappedKey.toString().substring(0, 8) +
                    " value " +
                    ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
        }


        this.putKeyValue(wrappedKey, value);

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        committedCache.put(key, value);

    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {

        // always mark for deletion if we don't know the state in the underlying store
        this.putKeyValue(wrappedKey, null);
        return;
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        Stream<ByteArrayWrapper> committedKeys = null;

        committedKeys = committedCache.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(Map.Entry::getKey);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return committedKeys
                .collect(Collectors.toSet());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        rows.forEach(this::put);
        keysToRemove.forEach(this::delete);
    }

    @Override
    public void flush() {
        if (dsKV!=null)
           dsKV.flush();
    }


    @Override
    public String getName() {
        return "DataSourceWithAuxKV-"+databaseName;
    }

    public void init() {

    }

    public boolean isAlive() {
        return true;
    }

    public void close() {

        flush();
    }


    public long countCommittedCachedElements() {
        if (committedCache!=null) {
            return committedCache.size();
        } else
            return 0;
    }

    public void resetHitCounters() {
        hits=0;
        misses=0;
        puts=0;
        gets=0;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        return list;
    }

    public List<String> getStats() {
        List<String> list = new ArrayList<>();
        list.add("puts: " + puts);
        list.add("gets: " + gets);
        long total = (hits + misses);
        list.add("committed cache hit [%]: " + hits * 100 /
                total);

        list.add("Hits: " + hits);
        list.add("Misses: " + misses);
        list.add("committedCache.size(): " + committedCache.size());
        list.add("DB Hashtable stats:");
        list.addAll(getHashtableStats());
        return list;
    }

    static public float getDefaultLoadFactor() {
        return 0.5f; // This is MaxSizeHashMap default.
    }

    public void clear() {
    }

}
