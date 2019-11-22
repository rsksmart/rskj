package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;

public class TransactionConflictDetectorWithCache extends TransactionConflictDetector {

    Map<String, Collection<ByteArrayWrapper>> cachedReadKeysPerThreadGroup = new HashMap<>();
    Map<String, Collection<ByteArrayWrapper>> cachedWrittenKeysPerThreadGroup = new HashMap<>();
    Map<String, Collection<ByteArrayWrapper>> cachedAccessedAccounts = new HashMap<>();
    Map<String, Collection<ByteArrayWrapper>> cachedDeletedAccounts = new HashMap<>();
    Map<String, Collection<Conflict>> cachedConflictsPerThreadGroup = new HashMap<>();

    public TransactionConflictDetectorWithCache(TransactionsPartitioner partitioner) {
        super(partitioner);
    }

    /**
     * This will merge cached accesses into global maps of the parent class
     * @param partition
     */

    public synchronized void commitPartition(TransactionsPartition partition) {
        String threadGroupName = partition.getThreadGroup().getName();
        commitCachedMap(cachedReadKeysPerThreadGroup, threadGroupName, new BiConsumer<ByteArrayWrapper, String>() {
            @Override
            public void accept(ByteArrayWrapper byteArrayWrapper, String s) {
                TransactionConflictDetectorWithCache.super.trackReadAccess(byteArrayWrapper, s);
            }
        });
        commitCachedMap(cachedWrittenKeysPerThreadGroup, threadGroupName, new BiConsumer<ByteArrayWrapper, String>() {
            @Override
            public void accept(ByteArrayWrapper byteArrayWrapper, String s) {
                TransactionConflictDetectorWithCache.super.trackWriteAccess(byteArrayWrapper, s);
            }
        });
        commitCachedMap(cachedAccessedAccounts, threadGroupName, new BiConsumer<ByteArrayWrapper, String>() {
            @Override
            public void accept(ByteArrayWrapper byteArrayWrapper, String s) {
                TransactionConflictDetectorWithCache.super.trackAccessToAccount(byteArrayWrapper, s);
            }
        });
        commitCachedMap(cachedDeletedAccounts, threadGroupName, new BiConsumer<ByteArrayWrapper, String>() {
            @Override
            public void accept(ByteArrayWrapper byteArrayWrapper, String s) {
                TransactionConflictDetectorWithCache.super.trackAccountDeleted(byteArrayWrapper, s);
            }
        });
        commitCachedMap(cachedConflictsPerThreadGroup, threadGroupName, new BiConsumer<Conflict, String>() {
            @Override
            public void accept(Conflict conflict, String s) {
                TransactionConflictDetectorWithCache.super.recordConflict(conflict);
            }
        });
    }

    // All 'tracking' methods are overridden so that the key accesses are not recorded in global maps yet,
    // but in cached maps instead (note that these cached map are mapped per threadGroupName)

    @Override
    protected void trackReadAccess(ByteArrayWrapper key, String threadGroupName) {
        recordInMap(cachedReadKeysPerThreadGroup, threadGroupName, key);
    }

    @Override
    protected void trackWriteAccess(ByteArrayWrapper key, String threadGroupName) {
        recordInMap(cachedWrittenKeysPerThreadGroup, threadGroupName, key);
    }

    @Override
    protected void trackAccessToAccount(ByteArrayWrapper account, String threadGroupName) {
        recordInMap(cachedAccessedAccounts, threadGroupName, account);
    }

    @Override
    protected void trackAccountDeleted(ByteArrayWrapper account, String threadGroupName) {
        recordInMap(cachedDeletedAccounts, threadGroupName, account);
    }

    // same mechanism applies to recordConflict

    @Override
    protected void recordConflict(Conflict conflict) {
        recordInMap(cachedConflictsPerThreadGroup, conflict.getConflictFrom().getThreadGroup().getName(), conflict);
        recordInMap(cachedConflictsPerThreadGroup, conflict.getConflictWith().getThreadGroup().getName(), conflict);
    }

    private static <T> void recordInMap(Map<String, Collection<T>> map, String entryKey, T valueToAdd) {
        Collection<T> values = map.computeIfAbsent(entryKey, k -> new HashSet<>());
        // Add the value if not already in the collection
        values.add(valueToAdd);
    }

    private static <T> void commitCachedMap(Map<String, Collection<T>> cachedMap, String threadGroupName, BiConsumer<T, String> trackFunction) {
        Collection<T> cachedValues = cachedMap.remove(threadGroupName);
        if (cachedValues != null) {
            for (T key: cachedValues) {
                trackFunction.accept(key, threadGroupName);
            }
        }
    }

}
