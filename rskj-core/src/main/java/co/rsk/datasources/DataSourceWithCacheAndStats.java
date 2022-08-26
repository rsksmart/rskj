package co.rsk.datasources;

import co.rsk.util.FormatUtils;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCacheAndStats implements KeyValueDataSource {

    public static String debugKey;
    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");
    public boolean readOnly = false;
    private final int cacheSize;
    protected final KeyValueDataSource base;
    protected final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    protected final Map<ByteArrayWrapper, byte[]> committedCache ;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final CacheSnapshotHandler cacheSnapshotHandler;


    long committedCacheHits;
    long committedCacheMisses;
    long puts;
    long gets;
    long getsFromStore;

    public DataSourceWithCacheAndStats(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithCacheAndStats( KeyValueDataSource base, int cacheSize,
                                CacheSnapshotHandler cacheSnapshotHandler) {
        this.cacheSize = cacheSize;
        this.base = base;
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float)0.75, false);
        Map<ByteArrayWrapper, byte[]> iCache = makeCommittedCache(cacheSize, cacheSnapshotHandler);
        if (iCache!=null)
            this.committedCache = Collections.synchronizedMap(iCache);
        else
            this.committedCache =null;
        this.cacheSnapshotHandler = cacheSnapshotHandler;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();
        gets++;

        try {
            // An element cannot be in both the committed or uncommitted cache
            // if it is in the committed cache, it won't be on the other.
            // therefore the order of the containsKey() check is irrelevant.
            if ((debugKey!=null) && wrappedKey.toString().equals(debugKey)) {
                wrappedKey = wrappedKey;
            }
            if (committedCache!=null) {
                if (committedCache.containsKey(wrappedKey)) {
                    committedCacheHits++;
                    return committedCache.get(wrappedKey);
                }
            }
            committedCacheMisses++;

            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            if (base!=null) {
                value = base.get(key);
                if (dump) {
                    if (value != null)
                        System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                                " value " +
                                ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
                    else
                        System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                                " failed");
                }
                getsFromStore++;
                if (traceEnabled) {
                    numOfGetsFromStore.incrementAndGet();
                }

                //null value, as expected, is allowed here to be stored in committedCache
                // Why would a null value be needed here?
                // Only if an element is read from the database but it's missing
                // but how can it be missing if the key is a hash of the node ??
                // The ONLY case is that save() is testing if the node exists, to avoid
                // storing it. If it doesn't exists, it will immediately store it
                // therefore, there is absolutely no need to store null.
                //null value, as expected, is allowed here to be stored in committedCache
                //null value indicates the removal of the element.
                if (value != null)
                    if (committedCache != null)
                        committedCache.put(wrappedKey, value);
            } else
                value= null;
        }
        finally {
            if (traceEnabled) {
                numOfGets.incrementAndGet();
            }

            this.lock.readLock().unlock();
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

    boolean dump = false;
    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        checkReadOnly();
        Objects.requireNonNull(value);
        puts++;
        if (dump) {
            System.out.println("Writing key "+wrappedKey.toString().substring(0,8)+
                            " value "+
            ByteUtil.toHexString(value).substring(0,8)+".. length "+value.length);
        }

        this.lock.writeLock().lock();

        try {
            if ((debugKey!=null) && wrappedKey.toString().equals(debugKey)) {
                wrappedKey = wrappedKey;
            }
            if (committedCache!=null) {
                // here I could check for equal data or just move to the uncommittedCache.
                byte[] priorValue = committedCache.get(wrappedKey);

                if (priorValue != null && Arrays.equals(priorValue, value)) {
                    return value;
                }

                committedCache.remove(wrappedKey);
            }
            this.putKeyValue(wrappedKey, value);
        }
        finally {
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }

            this.lock.writeLock().unlock();
        }

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        uncommittedCache.put(key, value);

        if (uncommittedCache.size() > cacheSize) {
            this.flush();
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();

        try {
            if (committedCache!=null) {
                // always mark for deletion if we don't know the state in the underlying store
                if (!committedCache.containsKey(wrappedKey)) {
                    this.putKeyValue(wrappedKey, null);
                    return;
                }
            }

            byte[] valueToRemove = committedCache.get(wrappedKey);

            // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
            if (valueToRemove != null) {
                this.putKeyValue(wrappedKey, null);
                if (committedCache!=null) {
                    committedCache.remove(wrappedKey);
                }
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        Stream<ByteArrayWrapper> baseKeys;
        Stream<ByteArrayWrapper> committedKeys=null;
        Stream<ByteArrayWrapper> uncommittedKeys;
        Set<ByteArrayWrapper> uncommittedKeysToRemove;

        this.lock.readLock().lock();

        try {
            baseKeys = base.keys().stream();
            if (committedCache!=null) {
                committedKeys = committedCache.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .map(Map.Entry::getKey);
            } else
                committedKeys =  Stream.empty();

            uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        finally {
            this.lock.readLock().unlock();
        }

        Set<ByteArrayWrapper> knownKeys = Stream.concat(Stream.concat(baseKeys, committedKeys), uncommittedKeys)
                .collect(Collectors.toSet());
        knownKeys.removeAll(uncommittedKeysToRemove);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return knownKeys.stream()
                .collect(Collectors.toSet());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        this.lock.writeLock().lock();

        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
        if (dump)
            System.out.println("flushing...");

        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.lock.writeLock().lock();

        try {
            long saveTime = System.nanoTime();

            this.uncommittedCache.forEach((key, value) -> {
                if (value != null) {
                    if ((debugKey!=null) && (key.toString().equals(debugKey))) {
                        key = key;
                    }
                    uncommittedBatch.put(key, value);
                }
            });

            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            if ((uncommittedBatch.size()>0) || (uncommittedKeysToRemove.size()>0))
                checkReadOnly();
            if (base!=null)
                base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            if (committedCache!=null) {
                committedCache.putAll(uncommittedCache);
            }
            uncommittedCache.clear();

            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists() {
        return base.exists();
    }

    public String getModifiers() {
        return "";
    }

    public String getName() {
        if (base==null)
            return "DataSourceWithCacheInMemory";
        else
            return base.getName() + "-with-uncommittedCache";
    }

    public void init() {
        if (base==null)
            return;
        base.init();
    }

    public boolean isAlive() {
        if (base==null)
            return true;
        return base.isAlive();
    }

    public void close() {
        this.lock.writeLock().lock();

        try {
            flush();
            if (base!=null)
                base.close();
            if (cacheSnapshotHandler != null) {
                cacheSnapshotHandler.save(committedCache);
            }
            uncommittedCache.clear();
            if (committedCache!=null) {
                committedCache.clear();
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public void emitLogs() {
        if (!logger.isTraceEnabled()) {
            return;
        }

        this.lock.writeLock().lock();

        try {
            logger.trace("Activity: No. Gets: {}. No. Puts: {}. No. Gets from Store: {}",
                    numOfGets.getAndSet(0),
                    numOfPuts.getAndSet(0),
                    numOfGetsFromStore.getAndSet(0));
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }


    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                                     CacheSnapshotHandler cacheSnapshotHandler) {
        if (cacheSize==0) return null;

        Map<ByteArrayWrapper, byte[]> cache;

        if (cacheSize==Integer.MAX_VALUE) {
            cache = new HashMap<>();
        } else
            cache = new MaxSizeHashMap<>(cacheSize, true);


        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }

    public long countCommittedCachedElements() {
        if (committedCache!=null) {
            return committedCache.size();
        } else
            return 0;
    }

    public void resetHitCounters() {
        committedCacheHits=0;
        committedCacheMisses=0;
        puts=0;
        gets=0;
        getsFromStore=0;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        return list;
    }

    public List<String> getStats() {
        List<String> list = new ArrayList<>();
        list.add("puts: " +puts);
        list.add("gets: " +gets);
        list.add("getsFromStore: " +getsFromStore);
        if (committedCache!=null) {
            long total = (committedCacheHits+committedCacheMisses);
            if (total>0)
                list.add("committed cache hit [%]: " + committedCacheHits*100/
                    total);

            list.add("committedCacheHits: " + committedCacheHits);
            list.add("committedCacheMisses: " + committedCacheMisses);
            list.add("committedCache.size(): " + committedCache.size()+" (max "+cacheSize+")");
        }
        list.add("uncommittedCache.size(): "+uncommittedCache.size());
        return list;
    }

    static public float getDefaultLoadFactor() {
        return 0.75f; // This is MaxSizeHashMap default.
    }

    public void clear() {
        committedCache.clear();
        uncommittedCache.clear();
    }
}
