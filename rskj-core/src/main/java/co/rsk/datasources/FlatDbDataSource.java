package co.rsk.datasources;

import co.rsk.bahashmaps.AbstractByteArrayHashMap;
import co.rsk.bahashmaps.Format;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

public class FlatDbDataSource extends DataSourceWithHeap {

    public static final int latestDBVersion = 1;
    EnumSet<CreationFlag> flags;

    public enum CreationFlag {
        supportNullValues,  // Allow values to be null, and stored as such in the map
        allowRemovals,      // allow remove() to really remove the values from the heap
        supportBigValues, // support values with lengths higher than 127 bytes to be efficiently handled
        supportAdditionalKV, // Support KVs with keys that are not hashes of data.
        atomicBatches,
        useDBForDescriptions;

        public static final EnumSet<CreationFlag> All = EnumSet.allOf(CreationFlag.class);
        public static final EnumSet<CreationFlag> None = EnumSet.noneOf(CreationFlag.class);
    }

    public static Format getFormat(EnumSet<CreationFlag> creationFlags,int dbVersion) {
        Format format = new Format();
        format.creationFlags = convertFlatDBFlags(creationFlags);
        format.dbVersion = dbVersion;
        format.pageSize = 16384; // later I should use an OS API to get the right value
        return format;
    }

    protected static KeyValueDataSource createDescDataSource(String aPath, boolean required, boolean readOnly) {
        if (!required) {
            return null;
        }
        Path datasourcePath = Paths.get(aPath,"metadata");
        String  name =datasourcePath.getFileName().toString(); //+".metadata";
        String  path =datasourcePath.getParent().toString();
        return new LevelDbDataSource(name,path,readOnly);
    }

    public FlatDbDataSource(int maxNodeCount, long beHeapCapacity, String databaseName,
                            EnumSet<CreationFlag> creationFlags, int dbVersion, boolean readOnly) throws IOException {
        // single-thread test:
        //  With rwlocks or exclusive locks: 85k/sec.
        //  Without locks: 102K/sec

        super(maxNodeCount, beHeapCapacity,databaseName,LockType.RW,
                getFormat(creationFlags,dbVersion),
                (creationFlags.contains(CreationFlag.supportAdditionalKV)),
                (creationFlags.contains(CreationFlag.atomicBatches)),
                createDescDataSource(databaseName,(creationFlags.contains(CreationFlag.useDBForDescriptions)),readOnly),
                readOnly);
        this.flags = creationFlags;
    }

    public void init() {
        if (descDataSource!=null)
            descDataSource.init();
        super.init();

    }
    public void flushWithPowerFailure() {
        dbLock.writeLock().lock();
        try {
            //
            super.flushWithPowerFailure();
            // close the description file so it can be re-opened
            descDataSource.close();
        } finally {
            dbLock.writeLock().unlock();
        }
    }
    public void powerFailure() {
        dbLock.writeLock().lock();
        try {
            //
            super.powerFailure();
            // close the description file so it can be re-opened
            descDataSource.close();
        } finally {
            dbLock.writeLock().unlock();
        }
    }
    public void close() {
        dbLock.writeLock().lock();
        try {
            super.close();
            descDataSource.close();
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public static EnumSet<AbstractByteArrayHashMap.CreationFlag> convertFlatDBFlags(EnumSet<CreationFlag> creationFlags) {
        EnumSet<AbstractByteArrayHashMap.CreationFlag> cfs = EnumSet.noneOf(AbstractByteArrayHashMap.CreationFlag.class);
        if (creationFlags.contains(CreationFlag.supportNullValues))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.supportNullValues);
        if (creationFlags.contains(CreationFlag.allowRemovals))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.allowRemovals);
        if (creationFlags.contains(CreationFlag.supportBigValues))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.supportBigValues);
        return cfs;
    }
}
