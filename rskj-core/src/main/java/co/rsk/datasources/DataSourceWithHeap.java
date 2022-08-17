package co.rsk.datasources;

import co.rsk.bahashmaps.ByteArray40HashMap;
import co.rsk.bahashmaps.Format;
import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.baheaps.ByteArrayHeap;
import co.rsk.bahashmaps.AbstractByteArrayHashMap;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.PrefixedKeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DataSourceWithHeap extends DataSourceWithAuxKV {
    protected AbstractByteArrayHashMap bamap;
    protected AbstractByteArrayHeap sharedBaHeap;
    protected EnumSet<AbstractByteArrayHashMap.CreationFlag> creationFlags;
    protected Format format;
    protected Path mapPath;
    protected Path dbPath;
    protected KeyValueDataSource descDataSource;
    LockType lockType;
    int maxNodeCount;
    long beHeapCapacity;

    public enum LockType {
        Exclusive,
        RW,
        None
    }

    public DataSourceWithHeap(int maxNodeCount, long beHeapCapacity,
                              String databaseName,LockType lockType,
                              Format format,boolean additionalKV,
                              KeyValueDataSource descDataSource,
                              boolean readOnly) throws IOException {
        super(databaseName,additionalKV,readOnly);
        this.descDataSource = descDataSource;
        this.format = format;
        mapPath = Paths.get(databaseName, "hash.map");
        dbPath = Paths.get(databaseName, "store");
        this.lockType = lockType;
        this.maxNodeCount = maxNodeCount;
        this.beHeapCapacity = beHeapCapacity;

    }

    public void init() {

        Map<ByteArrayWrapper, byte[]> iCache = null;
        try {
            iCache = makeCommittedCache(maxNodeCount,beHeapCapacity);
        } catch (IOException e) {
            // TO DO:  What ?
            e.printStackTrace();
        }
        if (lockType==LockType.RW)
            this.committedCache =RWLockedCollections.rwSynchronizedMap(iCache);
        else
        if (lockType==LockType.Exclusive)
            this.committedCache = Collections.synchronizedMap(iCache);
        else
            this.committedCache = iCache;
      super.init();
    }

    public String getModifiers() {
        return "";
    }

    public String getName() {
        return "DataSourceWithHeap-"+databaseName;
    }

    @Override
    public void flush() {
        super.flush();
        try {
            if ((!readOnly) && (bamap.modified())) {
                sharedBaHeap.save();
                bamap.save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        dbLock.writeLock().lock();
        try {
            flush();
            committedCache.clear();
            dsKV.close();
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    static protected final byte[] heapPrefix = "heap.".getBytes(StandardCharsets.UTF_8);
    static protected final byte[] mapPrefix = "map.".getBytes(StandardCharsets.UTF_8);

    AbstractByteArrayHeap createByteArrayHeap(float loadFactor, long maxNodeCount, long maxCapacity) throws IOException {
        ByteArrayHeap baHeap = new ByteArrayHeap();
        baHeap.setMaxMemory(maxCapacity); //730_000_000L); // 500 Mb / 1 GB
        Files.createDirectories(Paths.get(databaseName));
        if (descDataSource!=null) {
            baHeap.setDescriptionFileSource(new PrefixedKeyValueDataSource(heapPrefix,descDataSource));
        }
        baHeap.setFileName(dbPath.toString());
        baHeap.setFileMapping(true);
        // Initialize will create the space files on disk if they don't exists, so we must
        // query first if they exists of not.
        boolean filesExists= baHeap.fileExists();
        baHeap.initialize();
        if (filesExists)
            baHeap.load(); // We throw away the root...
        return baHeap;

    }

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int maxNodeCount, long beHeapCapacity) throws IOException {
        if (maxNodeCount==0) return null;

        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();

        float loadFActor =getDefaultLoadFactor();
        int initialSize = (int) (maxNodeCount/loadFActor);

        // Since we are not compressing handles, we must prepare for wost case
        // First we create a heap: this is where all values will be stored
        // in a continuous "stream" of data.
        sharedBaHeap =
                createByteArrayHeap(loadFActor,maxNodeCount,beHeapCapacity);

        // Now we create the map, which is like an index to locate the
        // information in the heap. ·"39" is the number of bits supported
        // in the datatype that references offsets in the heap.
        // 2^39 bytes is equivalent to 512 Gigabytes.
        //
        this.bamap =  new ByteArray40HashMap(initialSize,loadFActor,myKR,
                (long) beHeapCapacity,
                sharedBaHeap,0,format);
        if (descDataSource!=null) {
            this.bamap.setDataSource(
                    new PrefixedKeyValueDataSource(mapPrefix,descDataSource));
        }
        this.bamap.setPath(mapPath);
        if (bamap.dataFileExists()) {
            bamap.load();
        }
        return bamap;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        list.add("slotChecks: " +bamap.tableSlotChecks);
        list.add("lookups: " +bamap.tableLookups);
        list.add("slotchecks per lookup: " +1.0*bamap.tableSlotChecks/bamap.tableLookups);
        return list;
    }

}
