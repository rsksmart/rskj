package co.rsk.datasources;

import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataSourceWrapper implements KeyValueDataSource {

    protected static final Logger logger = LoggerFactory.getLogger("datasourcewithauxkv");

    protected final ReadWriteLock dbLock = new ReentrantReadWriteLock();


    public boolean readOnly = false;
    boolean dump = false;

    long hits;
    long misses;
    long puts;
    long gets;
    String databaseName;

    public DataSourceWrapper(String databaseName, boolean readOnly) throws IOException {
        this.databaseName = databaseName;
        this.readOnly = readOnly;

    }



    public void readLock() {
        dbLock.readLock().lock();
    }

    public void readUnlock() {
        dbLock.readLock().unlock();
    }

    protected byte[] internalGet(ByteArrayWrapper wrappedKey) {
     return null;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        gets++;
        readLock(); try {
          value = internalGet(wrappedKey);
            if (value != null) {
                hits++;
            } else
                misses++;
        } finally {
            readUnlock();
        }



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

        if (dump) {
            System.out.println("Writing key " + wrappedKey.toString().substring(0, 8) +
                    " value " +
                    ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
        }

        dbLock.writeLock().lock();
        try {
            puts++;
            try {
                beginLog();
                try {
                    this.internalPut(wrappedKey, value);
                } finally {
                    endLog();
                    checkFlushAfterPut();
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        } finally {
            dbLock.writeLock().unlock();
        }
        return value;
    }

    protected void internalPut(ByteArrayWrapper key, byte[] value) {

    }

    public void checkFlushAfterPut() {

    }
    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    protected void internalRemove(ByteArrayWrapper wrappedKey) {

    }

    protected void delete(ByteArrayWrapper wrappedKey) {

        dbLock.writeLock().lock(); try {
            internalRemove(wrappedKey);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    protected Set<ByteArrayWrapper> internalKeySet() {
        return null;
    }

    @Override
    public Set<ByteArrayWrapper> keys() {


        // committedCache does not store (key,null) values.
        // (key,null) puts are interpreted as deletes.
        // Therefore, we don't need to filter the entrySet() for
        // null values: we can simple use keys().
        // Stream<ByteArrayWrapper> committedKeys = null;
        //committedKeys = committedCache.entrySet().stream()
        //        .filter(e -> e.getValue() != null)
        //        .map(Map.Entry::getKey);
        // note that toSet doesn't work with byte[], so we have to do this extra step
        //return committedKeys.collect(Collectors.toSet());
        readLock(); try {
          return internalKeySet();
        } finally {
            readUnlock();
        }

    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        dbLock.writeLock().lock(); try {
            beginLog();
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
            endLog();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            dbLock.writeLock().unlock();
        }

    }

    // This method is used to test power failures
    public void updateBatchInterrupted(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove,int interruptionPoint) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);
        int counter =0;
        dbLock.writeLock().lock(); try {
            beginLog();
            if (counter==interruptionPoint)
                return;
            counter++;
            for (Map.Entry<ByteArrayWrapper,byte[]> row : rows.entrySet()) {
                put(row.getKey(),row.getValue());

                if (counter==interruptionPoint)
                    return;
                counter++;
            }
            for (ByteArrayWrapper key: keysToRemove) {
                delete(key);

                if (counter==interruptionPoint)
                    return;
                counter++;
            }

            if (counter==interruptionPoint)
                return;
            counter++;
            endLog();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            dbLock.writeLock().unlock();
        }

    }
    public void beginLog() throws IOException {

    }
    public void endLog() throws IOException {

    }

    @Override
    public void flush() {
        flushWithFailure(null);
    }

    public void flushWithFailure(FailureTrack failureTrack) {

    }

    @Override
    public boolean exists() {
        return true;
    }


    @Override
    public String getName() {
        return "DataSourceWrapper-"+databaseName;
    }

    public void init() {

    }

    public boolean isAlive() {
        return true;
    }

    public void close() {

        flush();
    }

    protected long internalSize() {
        return 0;
    }

    public long countCommittedCachedElements() {

        readLock();
        try {
            return internalSize();
        } finally {
            readUnlock();
        }

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
        readLock(); try {
          list.add("size(): " + internalSize());
        } finally {
            readUnlock();
        }
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
