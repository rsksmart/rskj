package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.ICacheTracking;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class tracks accesses in repository (read/write or delete keys), per threadGroup
 * and detect if there are confilcts between threadGroups, that means 2 different threadGroups
 * have accessed to the same key, whatever the access type (read/write or delete)
 */
class TransactionConflictDetector implements ICacheTracking.Listener {

    private static final Logger logger = LoggerFactory.getLogger("txconflictdetector");

    public static class Conflict {
        private ByteArrayWrapper key;
        private eConflictType conflictType;
        private Set<TransactionsPartition> conflictingPartitions = new HashSet<>();
        public Conflict(ByteArrayWrapper key, TransactionsPartition conflictFrom, TransactionsPartition conflictWith, eConflictType conflictType) {
            this.key = key;
            this.conflictType = conflictType;
            this.conflictingPartitions.add(conflictFrom);
            this.conflictingPartitions.add(conflictWith);
        }
    }

    private Map<TransactionsPartition, Set<Conflict>> conflictsPerPartition = new HashMap<>();
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
        logger.info("key [{}] is read by group [{}]",
                key.toString(), threadGroupName);

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
        logger.info("key [{}] is written by group [{}]",
                key.toString(), threadGroupName);

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

    private void recordConflict(ByteArrayWrapper key, String conflictThreadGroup, String originThreadGroup, eConflictType conflictType) {
        logger.info("Record conflict between groups '[{}]' and '[{}}]'", conflictThreadGroup, originThreadGroup);
        TransactionsPartition conflictPartition = partitioner.fromThreadGroup(conflictThreadGroup);
        TransactionsPartition originPartition = partitioner.fromThreadGroup(originThreadGroup);
        if (conflictPartition == null) {
            logger.error("Unable to get the partition for threadGroup " + conflictThreadGroup);
        }
        if (originPartition == null) {
            logger.error("Unable to get the partition for threadGroup " + conflictThreadGroup);
        }
        Conflict conflict = new Conflict(key, conflictPartition, originPartition, conflictType);
        Collection<Conflict> conflicts = conflictsPerPartition.computeIfAbsent(conflictPartition, k -> new HashSet<>());
        conflicts.add(conflict);
        conflicts = conflictsPerPartition.computeIfAbsent(originPartition, k -> new HashSet<>());
        conflicts.add(conflict);
    }

    private boolean checkKeyAlreadyWritten(ByteArrayWrapper key, String threadGroupName) {
        String otherThreadGroup = threadGroupWriterPerKey.get(key);
        if ((otherThreadGroup == null) || otherThreadGroup.equals(threadGroupName)) {
            return false;
        } else {
            logger.info("key [{}] has already been written by group [{}] --> conflict from group [{}]",
                    key.toString(), otherThreadGroup, threadGroupName);
            recordConflict(key, otherThreadGroup, threadGroupName, eConflictType.WRITTEN_BEFORE_ACCESSED);
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
        otherThreadGroups.forEach(conflictThreadGroup -> {
            recordConflict(key, conflictThreadGroup, threadGroupName, eConflictType.ACCESSED_BEFORE_WRITTEN);
            logger.info("key [{}] has already been read by group [{}] --> conflict from group [{}]",
                    key.toString(), conflictThreadGroup, threadGroupName);
        });
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
        otherThreadGroups.forEach(conflictThreadGroup -> recordConflict(account, conflictThreadGroup, threadGroupName, eConflictType.ACCESSED_BEFORE_DELETE));
        return true;
    }

    private boolean checkAccountDeleted(ByteArrayWrapper account, String threadGroupName) {
        String otherThreadGroup = deletedAccounts.get(account);
        if ((otherThreadGroup == null) || otherThreadGroup.equals(threadGroupName)) {
            return false;
        } else {
            recordConflict(account, otherThreadGroup, threadGroupName, eConflictType.DELETED_BEFORE_ACCESSED);
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
        // Important ! use a HashSet and not an ArrayList because we don't want to add several times the same accessor
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

    public synchronized Set<TransactionsPartition> getConflictingPartitions() {
        return new HashSet<>(conflictsPerPartition.keySet());
    }

    public synchronized void discardPartition(TransactionsPartition partition) {
        discardPartition(partition, null);
    }

    private void discardPartition(TransactionsPartition partitionToDiscard, TransactionsPartition replacingPartition) {
        String oldThreadGroupName = partitionToDiscard.getThreadGroup().getName();
        String newThreadGroupName = null;
        if (replacingPartition != null) {
            newThreadGroupName = replacingPartition.getThreadGroup().getName();
            logger.info("Discard partition [{}] into group [{}]", oldThreadGroupName, newThreadGroupName);
        } else {
            logger.info("Discard partition [{}] ", oldThreadGroupName);
        }

        for (Collection<String> readers: threadGroupReadersPerKey.values()) {
            if (readers.remove(oldThreadGroupName)) {
                // remove() returns true if the element was present and has been removed
                if (replacingPartition != null) {
                    readers.add(newThreadGroupName);
                }
            }
        }
        if (replacingPartition != null) {
            String finalNewThreadGroupName = newThreadGroupName;
            threadGroupWriterPerKey.replaceAll((k, v) -> (oldThreadGroupName.equals(v)) ? finalNewThreadGroupName : v);
        } else {
            Set<Map.Entry<ByteArrayWrapper, String>> entries = new HashSet<>(threadGroupWriterPerKey.entrySet());
            entries.forEach(entry -> {
                if (oldThreadGroupName.equals(entry.getValue())) {
                    threadGroupWriterPerKey.remove(entry.getKey());
                }
            });
        }
        for (Collection<String> accessors: accessedAccounts.values()) {
            if (accessors.remove(oldThreadGroupName)) {
                // remove() returns true if the element was present and has been removed
                if (replacingPartition != null) {
                    accessors.add(newThreadGroupName);
                }
            }
        }
        if (replacingPartition != null) {
            String finalNewThreadGroupName1 = newThreadGroupName;
            deletedAccounts.replaceAll((k, v) -> (oldThreadGroupName.equals(v)) ? finalNewThreadGroupName1 : v);
        } else {
            Set<Map.Entry<ByteArrayWrapper, String>> entries = new HashSet<>(deletedAccounts.entrySet());
            entries.forEach(entry -> {
                if (oldThreadGroupName.equals(entry.getValue())) {
                    deletedAccounts.remove(entry.getKey());
                }
            });
        }
        Set<Conflict> conflicts = conflictsPerPartition.remove(partitionToDiscard);
        if (conflicts != null) {
            Set<Conflict> conflictsToAdd = new HashSet<>();
            Set<TransactionsPartition> entriesToRemove = new HashSet<>();
            for (Conflict conflict : conflicts) {
                Iterator<TransactionsPartition> it = conflict.conflictingPartitions.iterator();
                TransactionsPartition conflictingPartition = it.next();
                if (conflictingPartition == partitionToDiscard) {
                    conflictingPartition = it.next();
                }
                if ((replacingPartition == null) || (replacingPartition == conflictingPartition)) {
                    // remove the conflict from the list
                    Set<Conflict> otherConflicts = conflictsPerPartition.get(conflictingPartition);
                    otherConflicts.remove(conflict);
                    if (otherConflicts.isEmpty()) {
                        entriesToRemove.add(conflictingPartition);
                    }
                } else {
                    // replace conflicting partition in Conflict object
                    conflict.conflictingPartitions.remove(partitionToDiscard);
                    conflict.conflictingPartitions.add(replacingPartition);
                    conflictsToAdd.add(conflict);
                }
            }
            for (TransactionsPartition partition : entriesToRemove) {
                conflictsPerPartition.remove(partition);
            }
            if ((replacingPartition != null) && !conflictsToAdd.isEmpty()) {
                // remap the conflicts onto the replacingPartition
                Collection<Conflict> otherConflicts = conflictsPerPartition.computeIfAbsent(replacingPartition, k -> new HashSet<>());
                otherConflicts.addAll(conflictsToAdd);
            }
        }
    }

    public synchronized void resolveConflicts(Set<TransactionsPartition> mergedPartitions, TransactionsPartition resultingPartition) {
        for (TransactionsPartition partition: mergedPartitions) {
            // each access tracked for this partition shall be assigned to mergedPartition instead
            discardPartition(partition, resultingPartition);
        }
    }

    @VisibleForTesting
    public synchronized Collection<Conflict> getConflicts() {
        Collection<Conflict> allConflicts = new HashSet<>();
        for(Collection<Conflict> conflicts: conflictsPerPartition.values()) {
            allConflicts.addAll(conflicts);
        }
        return allConflicts;
    }

    public enum eConflictType {
        ACCESSED_BEFORE_WRITTEN,
        WRITTEN_BEFORE_ACCESSED,
        ACCESSED_BEFORE_DELETE,
        DELETED_BEFORE_ACCESSED
    }
    
    public static class TransactionConflictException extends Exception {

        Collection<Conflict> conflicts;

        public TransactionConflictException(Collection<Conflict> conflicts) {
            this.conflicts = conflicts;
        }

        public Collection<Conflict> getConflicts() {
            return conflicts;
        }
    }
    
    public synchronized void check() throws TransactionConflictException {
        Collection<Conflict> alllConflicts = new HashSet<>();
        if (hasConflict()) {
            for(Collection<Conflict> conflicts: conflictsPerPartition.values()) {
                alllConflicts.addAll(conflicts);
            }
            throw new TransactionConflictException(alllConflicts);
        }
    }
}
