package co.rsk.datasources;

import co.rsk.bahashmaps.AbstractByteArrayHashMap;
import co.rsk.bahashmaps.Format;
import co.rsk.datasources.flatdb.DbLock;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

public class FlatDbDataSource extends DataSourceWithHeap {

    public static final int latestDBVersion = 1;
    EnumSet<CreationFlag> flags;
    DbLock lockFile ;

    public enum CreationFlag {
        storeKeys,
        supportNullValues,  // Allow values to be null, and stored as such in the map
        allowRemovals,      // allow remove() to really remove the values from the heap
        supportBigValues, // support values with lengths higher than 127 bytes to be efficiently handled
        supportAdditionalKV, // Support KVs with keys that are not hashes of data.
        atomicBatches,
        variableLengthKeys,
        useLogForBatchConsistency,
        useMaxOffsetForBatchConsistency,
        useMWChecksumForSlotConsistency,
        useDBForDescriptions,
        autoUpgrade;

        public static final EnumSet<CreationFlag> Default = EnumSet.of(
                storeKeys,
                supportBigValues,
                allowRemovals,
                atomicBatches,
                variableLengthKeys,
                useLogForBatchConsistency,
                CreationFlag.autoUpgrade);

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
                            EnumSet<CreationFlag> creationFlags,
                            int dbVersion, boolean readOnly) throws IOException {
        // single-thread test:
        //  With rwlocks or exclusive locks: 85k/sec.
        //  Without locks: 102K/sec

        super(maxNodeCount, beHeapCapacity,databaseName,LockType.RW,
                getFormat(creationFlags,dbVersion),
                (creationFlags.contains(CreationFlag.supportAdditionalKV)),
                (creationFlags.contains(CreationFlag.atomicBatches)) &&
                        (creationFlags.contains(CreationFlag.useLogForBatchConsistency))
                ,
                (creationFlags.contains(CreationFlag.autoUpgrade)),
                createDescDataSource(databaseName,(creationFlags.contains(CreationFlag.useDBForDescriptions)),readOnly),
                readOnly);
        this.flags = creationFlags;

    }

    public void init() {
        // The descDataSource already has a lock, so if two applications try
        // to open the same database, only one will be able to.
        try {
            Files.createDirectories(Paths.get(this.databaseName));
            Path p =Paths.get(this.databaseName,"lock.dat");
            lockFile = new DbLock(p.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        if (descDataSource!=null)
            descDataSource.init();

        super.init();

    }
    public void flushWithPowerFailure(FailureTrack failureTrack) {
        dbLock.writeLock().lock();
        try {
            //
            super.flushWithPowerFailure(failureTrack);
            // close the description file so it can be re-opened
            descDataSource.close();
            lockFile.release();
            FailureTrack.finish(failureTrack);
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
            lockFile.release();
        } finally {
            dbLock.writeLock().unlock();
        }
    }
    public void close() {
        dbLock.writeLock().lock();
        try {
            super.close();
            if (descDataSource!=null)
                 descDataSource.close();
            lockFile.release();
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
        if (creationFlags.contains(CreationFlag.useLogForBatchConsistency))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.useLogForBatchConsistency);
        if (creationFlags.contains(CreationFlag.useMaxOffsetForBatchConsistency))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.useMaxOffsetForBatchConsistency);
        if (creationFlags.contains(CreationFlag.useMWChecksumForSlotConsistency))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.useMWChecksumForSlotConsistency);
        if (creationFlags.contains(CreationFlag.storeKeys))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.storeKeys);
        if (creationFlags.contains(CreationFlag.variableLengthKeys))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.variableLengthKeys);

        return cfs;
    }
}
