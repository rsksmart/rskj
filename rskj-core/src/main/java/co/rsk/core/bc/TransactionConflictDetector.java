package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.ICacheTracking;
import org.ethereum.db.ByteArrayWrapper;

import java.util.*;

/**
 * This class tracks accesses in repository (read/write or delete keys), per threadGroup
 * and detect if there are confilcts between threadGroups, that means 2 different threadGroups
 * have accessed to the same key, whatever the access type (read/write or delete)
 */
class TransactionConflictDetector implements ICacheTracking.Listener {

    public Set<TransactionsPartition> getConflictingPartitions() {
        return conflictsPerPartition.keySet();
    }

    public static class Conflict {
        private ByteArrayWrapper key;
        private eConflictType conflictType;
        private TransactionsPartition conflictWith;
        public Conflict(ByteArrayWrapper key, TransactionsPartition conflictWith) {
            this.key = key;
            this.conflictWith = conflictWith;
        }
    }

    private Map<TransactionsPartition, List<Conflict>> conflictsPerPartition = new HashMap<>();
    private TransactionsPartitioner partitioner;

    public TransactionConflictDetector(TransactionsPartitioner partitioner) {
        this.partitioner = partitioner;
    }

    Map<ByteArrayWrapper, String> lastThreadReadOrWrite = new HashMap<>();

    @Override
    public void onReadKey(ByteArrayWrapper key, String threadGroupName) {
        onAccessKey(key, threadGroupName);
    }

    @Override
    public void onWriteKey(ByteArrayWrapper key, String threadGroupName) {
        onAccessKey(key, threadGroupName);
    }

    @Override
    public void onDeleteKey(ByteArrayWrapper key, String threadGroupName) {
        onAccessKey(key, threadGroupName);
    }

    private synchronized void onAccessKey(ByteArrayWrapper key, String threadGroupName)  {
        // Check if any other thread already read or write this key
        String otherThreadGroup = lastThreadReadOrWrite.get(key);
        if (otherThreadGroup == null) {
            lastThreadReadOrWrite.put(key, threadGroupName);
        } else if (!otherThreadGroup.equals(threadGroupName)) {
            TransactionsPartition conflictPartition = partitioner.fromThreadGroup(otherThreadGroup);
            Conflict conflict = new Conflict(key, conflictPartition);
            Collection<Conflict> conflicts = conflictsPerPartition.computeIfAbsent(conflictPartition, k -> new ArrayList<>());
            conflicts.add(conflict);
        }
    }

    public synchronized boolean hasConflict() {
        for(Collection<Conflict> conflicts: conflictsPerPartition.values()) {
            if (!conflicts.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public enum eConflictType {
        READ_READ,
        READ_WRITE,
        WRITE_READ,
        WRITE_WRITE,
        READ_DELETE,
        WRITE_DELETE,
        DELETE_READ,
        DELETE_WRITE,
        DELETE_DELETE
    }
    
    public static class TransactionConflictException extends Exception {

        Conflict conflict;

        public TransactionConflictException(Conflict conflict) {
            this.conflict = conflict;
        }

        public TransactionsPartition getConflictWithPartition() {
            return conflict.conflictWith;
        }

        public eConflictType getConflictType() {
            return conflict.conflictType;
        }
    }
    
    public synchronized void check() throws TransactionConflictException {
        for(List<Conflict> conflicts: conflictsPerPartition.values()) {
            if (!conflicts.isEmpty()) {
                throw new TransactionConflictException(conflicts.get(0));
            }
        }
    }
}
