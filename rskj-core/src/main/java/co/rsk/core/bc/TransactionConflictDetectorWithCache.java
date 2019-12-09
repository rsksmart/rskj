package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.ObjIntConsumer;

/**
 * Like TransactionConflictDetector, this class is used to track accesses in repository (read/write or delete keys),
 * per partitionId, and detect if there are confilcts between partitions, that means 2 different partitionId
 * have accessed to the same key, whatever the access type (read/write or delete)
 * Unlike the parent class, all accesses are cached and not directly recorded in underlying maps, until
 * the method mergePartition() is called.
 * This is useful when mining a block and executing some transactions that may be invalid in the end.
 * In such a case we must purge all accesses done by the transaction from the records.
 * Using a cache mechanism allows use to simply ignore these entries, while they have not been merged into
 * underlying maps
 */
public class TransactionConflictDetectorWithCache extends TransactionConflictDetector {

    private Map<Integer, Collection<ByteArrayWrapper>> cachedReadKeysPerPartId = new HashMap<>();
    private Map<Integer, Collection<ByteArrayWrapper>> cachedWrittenKeysPerPartId = new HashMap<>();
    private Map<Integer, Collection<ByteArrayWrapper>> cachedAccessedAccounts = new HashMap<>();
    private Map<Integer, Collection<ByteArrayWrapper>> cachedDeletedAccounts = new HashMap<>();
    private Map<Integer, Collection<Conflict>> cachedConflictsPerPartId = new HashMap<>();

    public TransactionConflictDetectorWithCache(TransactionsPartitioner partitioner) {
        super(partitioner);
    }

    /**
     * This will merge cached accesses into global maps of the parent class
     *
     * @param partition
     */

    public synchronized void commitPartition(TransactionsPartition partition) {
        int partitionId = partition.getId();
        commitCachedMap(cachedReadKeysPerPartId, partitionId, (byteArrayWrapper, i) ->
            TransactionConflictDetectorWithCache.super.trackReadAccess(byteArrayWrapper, i)
        );
        commitCachedMap(cachedWrittenKeysPerPartId, partitionId, (byteArrayWrapper, i) ->
            TransactionConflictDetectorWithCache.super.trackWriteAccess(byteArrayWrapper, i)
        );
        commitCachedMap(cachedAccessedAccounts, partitionId, (byteArrayWrapper, i) ->
            TransactionConflictDetectorWithCache.super.trackAccessToAccount(byteArrayWrapper, i)
        );
        commitCachedMap(cachedDeletedAccounts, partitionId, (byteArrayWrapper, i) ->
            TransactionConflictDetectorWithCache.super.trackAccountDeleted(byteArrayWrapper, i)
        );
        commitCachedMap(cachedConflictsPerPartId, partitionId, (conflict, i) ->
            TransactionConflictDetectorWithCache.super.recordConflict(conflict)
        );
    }

    // All 'tracking' methods are overridden so that the key accesses are not recorded in global maps yet,
    // but in cached maps instead (note that these cached map are mapped per partitionId)

    @Override
    protected void trackReadAccess(ByteArrayWrapper key, int partitionId) {
        recordInMap(cachedReadKeysPerPartId, partitionId, key);
    }

    @Override
    protected void trackWriteAccess(ByteArrayWrapper key, int partitionId) {
        recordInMap(cachedWrittenKeysPerPartId, partitionId, key);
    }

    @Override
    protected void trackAccessToAccount(ByteArrayWrapper account, int partitionId) {
        recordInMap(cachedAccessedAccounts, partitionId, account);
    }

    @Override
    protected void trackAccountDeleted(ByteArrayWrapper account, int partitionId) {
        recordInMap(cachedDeletedAccounts, partitionId, account);
    }

    // same mechanism applies to recordConflict

    @Override
    protected void recordConflict(Conflict conflict) {
        recordInMap(cachedConflictsPerPartId, conflict.getConflictFrom().getId(), conflict);
        recordInMap(cachedConflictsPerPartId, conflict.getConflictWith().getId(), conflict);
    }

    private static <T> void recordInMap(Map<Integer, Collection<T>> map, Integer entryKey, T valueToAdd) {
        Collection<T> values = map.computeIfAbsent(entryKey, k -> new HashSet<>());
        // Add the value if not already in the collection
        values.add(valueToAdd);
    }

    private static <T> void commitCachedMap(Map<Integer, Collection<T>> cachedMap, int partitionId, ObjIntConsumer<T> trackFunction) {
        Collection<T> cachedValues = cachedMap.remove(partitionId);
        if (cachedValues != null) {
            for (T key : cachedValues) {
                trackFunction.accept(key, partitionId);
            }
        }
    }

}
