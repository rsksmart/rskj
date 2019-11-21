package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.ICacheTracking;
import com.google.common.annotations.VisibleForTesting;
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

    // We record all the threadGroup reading to a given key.
    // If another group attempts to write the same key, there will be a conflict recorded with every readers,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    Map<ByteArrayWrapper, Collection<String>> threadGroupReadersPerKey = new HashMap<>();
    // We record only one threadGroup writing to a given key.
    // If another group attempts to write the same key, there will be a conflict recorded with the first writer,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    Map<ByteArrayWrapper, String> threadGroupWriterPerKey = new HashMap<>();
    // We record all the threadGroup accessing to the keys of a given account.
    // If another group attempts to delete this account, there will be a conflict recorded with every accessors,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    Map<ByteArrayWrapper, Collection<String>> accessedAccounts = new HashMap<>();
    // We record only one threadGroup deleting a given account.
    // If another group attempts to delete the same account, there will be a conflict recorded with the first deletor,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    Map<ByteArrayWrapper, String> deletedAccounts = new HashMap<>();

    @Override
    public synchronized void onReadKey(ICacheTracking cacheTracking, ByteArrayWrapper key, String threadGroupName) {
        // There is a conflict if :
        // - the key has already been written by another threadGroup
        // or
        // - the account from that key has been deleted by another threadGroup

        if (!checkKeyAlreadyWritten(key, threadGroupName)) {
            // No conflict detected -> track the read access to the key
            trackReadAccess(key, threadGroupName);
        }
        checkAccountDeleted(cacheTracking.getAccountFromKey(key), threadGroupName);
        // Whatever if there is a conflict or not, track access to the account of this key
        trackAccessToAccount(cacheTracking.getAccountFromKey(key), threadGroupName);
    }

    @Override
    public synchronized void onWriteKey(ICacheTracking cacheTracking, ByteArrayWrapper key, String threadGroupName) {
        // There is a conflict if :
        // - the key has already been written by another threadGroup
        // or
        // - the key has already been read by one or more other threadGroups
        // or
        // - the account from that key has been deleted by another threadGroup

        if (!checkKeyAlreadyWritten(key, threadGroupName)) {
            // No conflict detected -> track the write access to the key
            trackWriteAccess(key, threadGroupName);
            // Only check if already read by one or more other threadGroups is there is no conflict with another writer,
            // because in such a case, the readers are already in conflict with it.
            checkKeyAlreadyRead(key, threadGroupName);
        }
        checkAccountDeleted(cacheTracking.getAccountFromKey(key), threadGroupName);
        // Whatever if there is a conflict or not, track access to the account of this key
        trackAccessToAccount(cacheTracking.getAccountFromKey(key), threadGroupName);
    }

    @Override
    public synchronized void onDeleteAccount(ICacheTracking cacheTracking, ByteArrayWrapper account, String threadGroupName) {
        // There is a conflict if :
        // - some keys of this account have been accessed (read or written) by another threadGroup
        // or
        // - the account have already been deleted by another threadGroup

        checkAccountAccessed(account, threadGroupName);
        if (!checkAccountDeleted(account, threadGroupName)) {
            trackAccountDeleted(account, threadGroupName);
        }
    }

    private void recordConflict(ByteArrayWrapper key, String conflictThreadGroup) {
        TransactionsPartition conflictPartition = partitioner.fromThreadGroup(conflictThreadGroup);
        Conflict conflict = new Conflict(key, conflictPartition);
        Collection<Conflict> conflicts = conflictsPerPartition.computeIfAbsent(conflict.conflictWith, k -> new ArrayList<>());
        conflicts.add(conflict);
    }

    private boolean checkKeyAlreadyWritten(ByteArrayWrapper key, String threadGroupName) {
        String otherThreadGroup = threadGroupWriterPerKey.get(key);
        if ((otherThreadGroup == null) || otherThreadGroup.equals(threadGroupName)) {
            return false;
        } else {
            recordConflict(key, otherThreadGroup);
        }
        return true;
    }

    private boolean checkKeyAlreadyRead(ByteArrayWrapper key, String threadGroupName) {
        Collection<String> otherThreadGroups = threadGroupReadersPerKey.get(key);
        if (otherThreadGroups == null) {
            return false;
        }
        // there can not be a conflict with itself
        otherThreadGroups.remove(threadGroupName);
        if (otherThreadGroups.isEmpty()) {
            return false;
        }
        // Record a conflict for each reading group
        otherThreadGroups.forEach(conflictThreadGroup -> recordConflict(key, conflictThreadGroup));
        return true;
    }

    private boolean checkAccountAccessed(ByteArrayWrapper account, String threadGroupName) {
        Collection<String> otherThreadGroups = accessedAccounts.get(account);
        if (otherThreadGroups == null) {
            return false;
        }
        // there can not be a conflict with itself
        otherThreadGroups.remove(threadGroupName);
        if (otherThreadGroups.isEmpty()) {
            return false;
        }
        // Record a conflict for each reading group
        otherThreadGroups.forEach(conflictThreadGroup -> recordConflict(account, conflictThreadGroup));
        return true;
    }

    private boolean checkAccountDeleted(ByteArrayWrapper account, String threadGroupName) {
        String otherThreadGroup = deletedAccounts.get(account);
        if ((otherThreadGroup == null) || otherThreadGroup.equals(threadGroupName)) {
            return false;
        } else {
            recordConflict(account, otherThreadGroup);
        }
        return true;
    }

    private void trackReadAccess(ByteArrayWrapper key, String threadGroupName) {
        // Imprtant ! use a HashSet and not an ArrayList because we don't want to add several times the same reader
        Collection<String> readers = threadGroupReadersPerKey.computeIfAbsent(key, k -> new HashSet<>());
        // Add the reader if not already in the collection
        readers.add(threadGroupName);
    }

    private void trackWriteAccess(ByteArrayWrapper key, String threadGroupName) {
        threadGroupWriterPerKey.put(key, threadGroupName);
    }

    private void trackAccessToAccount(ByteArrayWrapper account, String threadGroupName) {
        // Imprtant ! use a HashSet and not an ArrayList because we don't want to add several times the same accessor
        Collection<String> accessors = accessedAccounts.computeIfAbsent(account, k -> new HashSet<>());
        // Add the accessor if not already in the collection
        accessors.add(threadGroupName);
    }

    private void trackAccountDeleted(ByteArrayWrapper account, String threadGroupName) {
        deletedAccounts.put(account, threadGroupName);
    }

    public synchronized boolean hasConflict() {
        for(Collection<Conflict> conflicts: conflictsPerPartition.values()) {
            if (!conflicts.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    public synchronized Collection<Conflict> getConflicts() {
        Collection<Conflict> allConflicts = new ArrayList<>();
        for(Collection<Conflict> conflicts: conflictsPerPartition.values()) {
            allConflicts.addAll(conflicts);
        }
        return allConflicts;
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
