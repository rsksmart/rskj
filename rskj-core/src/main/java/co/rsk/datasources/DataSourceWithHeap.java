package co.rsk.datasources;

import co.rsk.freeheap.*;
import co.rsk.datasources.flatydb.LogManager;
import co.rsk.dbutils.ObjectIO;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.PrefixedKeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DataSourceWithHeap extends DataSourceWrapper {
    protected Format format;
    protected Path mapPath;
    protected Path dbPath;
    protected KeyValueDataSource descDataSource;
    LockType lockType;
    int maxNodeCount;
    long beHeapDesiredCapacity;
    long baHeapCapacity;
    boolean useLogManager;
    boolean autoUpgrade;
    boolean supportNullValues;
    boolean supportBigValues;
    boolean storeKeys;
    boolean variableLengthKeys;
    boolean allowRemovals;
    boolean flushAfterPut;
    int maxObjectSize;
    int keysize;
    long size;
    LogManager logManager;
    boolean inBatch;
    boolean logEvents = true;
    AbstractFreeHeap baHeap;

    co.rsk.freeheap.BAKeyValueRelation BAKeyValueRelation;

    public enum LockType {
        Exclusive,
        RW,
        None
    }



    // constructor
    public DataSourceWithHeap(int maxNodeCount, long beHeapCapacity,
                              int maxObjectSize,
                              String databaseName,LockType lockType,
                              Format format,boolean additionalKV,
                              boolean useLogManager,
                              boolean autoUpgrade,
                              KeyValueDataSource descDataSource,
                              boolean readOnly) throws IOException {
        super(databaseName,readOnly);
        //this.creationFlags = creationFlags;
        this.descDataSource = descDataSource;
        this.format = format;
        this.useLogManager = useLogManager;
        mapPath = Paths.get(databaseName, "hash.map");
        dbPath = Paths.get(databaseName, "store");
        this.maxObjectSize = maxObjectSize;
        this.lockType = lockType;
        this.maxNodeCount = maxNodeCount;
        this.beHeapDesiredCapacity = beHeapCapacity;
        this.autoUpgrade = format.creationFlags.contains(CreationFlag.autoUpgrade);
        this.flushAfterPut = format.creationFlags.contains(CreationFlag.flushAfterPut);

        this.BAKeyValueRelation = BAKeyValueRelation;

        if (useLogManager) {
            logManager = new LogManager(Paths.get(databaseName));
        }

    }

    boolean allowDisablingReadLock() {
        return  (!format.creationFlags.contains(CreationFlag.allowRemovals)) &&
                (format.creationFlags.contains(CreationFlag.useMWChecksumForSlotConsistency));

    }

    public void checkFlushAfterPut() {
        if (flushAfterPut)
            flush();
    }

    public void init() {

        try {
            makeHeap(maxNodeCount, beHeapDesiredCapacity);
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.init();
    }

    public String getModifiers() {
        return "";
    }

    public String getName() {
        return getClass().getSimpleName()+":"+databaseName;
    }

    public void flushWithPowerFailure(FailureTrack failureTrack) {
        super.flush();
    }


    @Override
    public void flush() {
        super.flush();
        deleteLog();

    }
    public void powerFailure() {
        dbLock.writeLock().lock();
        try {
            // do not flush
            baHeap.powerFailure(); // unmap files
            //committedCache.clear();

        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public void close() {
        dbLock.writeLock().lock();
        try {
            flush();
            //committedCache.clear();

        } finally {
            dbLock.writeLock().unlock();
        }
    }

    static protected final byte[] heapPrefix = "heap.".getBytes(StandardCharsets.UTF_8);
    static protected final byte[] mapPrefix = "map.".getBytes(StandardCharsets.UTF_8);

    AbstractFreeHeap createByteArrayHeap(
            float loadFactor, long maxNodeCount, long maxCapacity) throws IOException {
        FreeHeap baHeap = new FreeHeap();
        baHeap.setMaxMemory(maxCapacity); //730_000_000L); // 500 Mb / 1 GB
        Files.createDirectories(Paths.get(databaseName));
        if (descDataSource!=null) {
            baHeap.setDescriptionFileSource(new PrefixedKeyValueDataSource(heapPrefix,descDataSource));
            baHeap.setAutoUpgrade(autoUpgrade);
        }
        baHeap.setFileName(dbPath.toString());
        baHeap.setFileSystemPageSize(format.pageSize);
        baHeap.setMaxObjectSize(maxObjectSize);
        baHeap.setStoreHeadmap(true);
        baHeap.setFileMapping(false); // set it to true
        // Initialize will create the space files on disk if they don't exists, so we must
        // query first if they exists of not.
        boolean filesExists= baHeap.fileExists();
        baHeap.initialize();
        if (filesExists)
            baHeap.load(); // We throw away the root...
        else {
            // save the description and the files as soon as possible
            baHeap.save();
        }
        return baHeap;

    }

    public void deleteLog() {
        if (logManager==null) {
            return;
        }
        logManager.deleteLog();
    }

    public void beginLog() throws IOException {
        if (inBatch)
            throw new RuntimeException("Cannot create a batch if already in a batch");
        inBatch = true;
        // from now on this object should be locked and only one thread should
        // access it. The lock must be provided by the object calling beginLog()
        if (logManager==null) {
            return;
        }
        // Only one thread may begin and work on a log at the same time
        // beginLog() operations cannot be nested
        logManager.beginLog();
    }

    public void endLog() throws IOException {
        if (!inBatch)
            throw new RuntimeException("Not in a batch");
        inBatch = false;

        if (logManager==null) {
            return;
        }
        logManager.endLog();
    }

        protected void makeHeap(int maxNodeCount, long beHeapCapacity) throws IOException {
        if (maxNodeCount==0) return;


        if (!format.creationFlags.contains(CreationFlag.storeKeys))
            BAKeyValueRelation = new MyBAKeyValueRelation();

        float loadFActor =getDefaultLoadFactor();
        int initialSize = (int) (maxNodeCount/loadFActor);

        // Since we are not compressing handles, we must prepare for wost case
        // First we create a heap: this is where all values will be stored
        // in a continuous "stream" of data.
            baHeap =
                createByteArrayHeap(loadFActor,maxNodeCount,beHeapCapacity);

        baHeapCapacity = baHeap.getCapacity();

        // Now we create the map, which is like an index to locate the
        // information in the heap. ·"39" is the number of bits supported
        // in the datatype that references offsets in the heap.
        // 2^39 bytes is equivalent to 512 Gigabytes.
        //

        //this.bamap =  new ByteArrayVarHashMap(initialSize,loadFActor,myKR,
        //        (long) beHeapCapacity,
        //        baHeap,0,format,logManager);


            // Check if a log exists
        if (logManager!=null) {
            if (logManager.logExists()) {
                logManager.processLog(baHeap);
            }
        }

    }

    public long hash(Object key) {
        // It's important that this hash DOES not collude with the HashMap hash, because
        // we're using hashmaps inside each bucket. If both hashes are the same,
        // then all objects in the same bucket will be also in the same bucked of the
        // hashmap stored in the bucket.
        //
        if (key == null)
            return 0;
        long h = BAKeyValueRelation.getHashcode(((ByteArrayWrapper) key).getData());
        // I need h to be positive, so I will remove the highest bit
        return (h & 0x7fffffffffffffffL);
    }

    protected byte[] internalGet(ByteArrayWrapper wrappedKey) {
        return this.getNode(hash(wrappedKey), wrappedKey);
    }

    int nullMetadataBitMask = (1<<0);
    int keyStoredMetadataBitMask= (1<<1);

    public boolean isValueMarkedOffset(byte metadata) {
        //if (!supportNullValues)
        //    return true;
        return ((metadata & nullMetadataBitMask)==0);
    }

    public boolean isKeyStoredMarkedOffset(long markedOffset) {
        if (storeKeys)
            return true;
        if (!supportBigValues)
            return false;

        return ((markedOffset & keyStoredMetadataBitMask)!=0);
    }
    public byte[] getDataFromKPD(byte[] keyPlusData) {
        int keySpaceLength;
        if (variableLengthKeys) {
            keySpaceLength = ObjectIO.getInt(keyPlusData,0)+4;
        } else {
            keySpaceLength = keysize;
        }
        byte[] c = new byte[keyPlusData.length - keySpaceLength];
        System.arraycopy(keyPlusData, keySpaceLength, c, 0, c.length);
        return c;

    }
    public byte[] getDataFromKPD(byte[] keyPlusData, byte metadata) {

        if (!isValueMarkedOffset(metadata)) { //*
            return null;
        }

        if (!isKeyStoredMarkedOffset(metadata))
            return keyPlusData;

        return getDataFromKPD(keyPlusData);

    }

    public byte[] getKeyFromKPD(byte[] kpd,byte metadata) {
        if (!isValueMarkedOffset(metadata)) { //*
            return kpd;
        }

        if (isKeyStoredMarkedOffset(metadata))
            return getKeyFromKPD(kpd,metadata);

        return BAKeyValueRelation.computeKey(kpd);
    }

    public boolean fastCompareKPDWithKey(byte[] kpd,ByteArrayWrapper key,byte metadata) {
        byte[] keyBytes2 = key.getData();
        byte[] keyBytes1 = getKeyFromKPD(kpd, metadata);

        return ByteUtil.fastEquals(keyBytes1,keyBytes2);
    }

    final byte[] getNode(long hash, Object key) {

        long pureOffset = hash % baHeapCapacity;
        while(true) {
            byte metadata = baHeap.retrieveMetadataByOfs(pureOffset)[0];
            byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
            if (fastCompareKPDWithKey(kpd, (ByteArrayWrapper) key, metadata)) {
                //if (result != null) {
                //    result.data = getDataFromKPD(kpd, markedOffset);
                //}
                return getDataFromKPD(kpd, metadata);
            }
            long nextOffset = baHeap.retrieveNextDataOfsByOfs(pureOffset);
            if ((nextOffset==-1) || (baHeap.isOfsAvail(nextOffset)))  {
                return null;
            }
            pureOffset = nextOffset;
        }
    }

    protected void internalPut(ByteArrayWrapper key, byte[] value) {
        long hash = hash(key);
        long pureOffset = hash % baHeapCapacity;
        long nextOffset =pureOffset;
        while(true) {
            if (baHeap.isObjectStoredAtOfs(pureOffset)) {
                byte metadata = baHeap.retrieveMetadataByOfs(pureOffset)[0];
                byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                if (fastCompareKPDWithKey(kpd, (ByteArrayWrapper) key, metadata)) {
                    // already exists
                    return;
                }
                nextOffset = baHeap.retrieveNextDataOfsByOfs(pureOffset);
                if (nextOffset == -1) {
                    throw new RuntimeException("Full heap");
                }
            }
            if (baHeap.isOfsAvail(nextOffset)) {
                // free place: todo fill metadata
                baHeap.addObjectAtOfs(nextOffset,key.getData(), new byte[]{0});
                size++;
                break;
            }
            pureOffset = nextOffset;
        }
    }

    protected void internalRemove(ByteArrayWrapper wrappedKey) {
        throw new RuntimeException("not supported");
    }

    protected Set<ByteArrayWrapper> internalKeySet() {
        Set<ByteArrayWrapper> result = new HashSet<>();
        long pureOffset = 0;
        while(true) {
            byte metadata = baHeap.retrieveMetadataByOfs(pureOffset)[0];
            byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
            byte[] key = getKeyFromKPD(kpd, metadata);
            result.add(new ByteArrayWrapper(key));
            long nextOffset = baHeap.retrieveNextDataOfsByOfs(pureOffset);
            if ((nextOffset==-1) || (baHeap.isOfsAvail(nextOffset)))  {
                return result;
            }
            pureOffset = nextOffset;
        }
    }

    protected long internalSize() {
        return size;
    }


}
