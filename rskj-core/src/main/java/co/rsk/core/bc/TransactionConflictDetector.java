package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.ICacheTracking;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * This class tracks accesses in repository (read/write or delete keys), per partitionId
 * and detect if there are confilcts between partitions, that means 2 different partitionId
 * have accessed to the same key, whatever the access type (read/write or delete)
 */
class TransactionConflictDetector implements ICacheTracking.Listener {

    private static final Logger logger = LoggerFactory.getLogger("txconflictdetector");

    public static class Conflict {
        private ByteArrayWrapper key;
        private eConflictType conflictType;
        private TransactionsPartition [] conflictingPartitions = new TransactionsPartition [2];

        public Conflict(ByteArrayWrapper key, TransactionsPartition conflictFrom, TransactionsPartition conflictWith, eConflictType conflictType) {
            this.key = key;
            this.conflictType = conflictType;
            this.conflictingPartitions[0]  = conflictFrom;
            this.conflictingPartitions[1] = conflictWith;
        }

        public ByteArrayWrapper getKey() {
            return key;
        }

        public eConflictType getConflictType() {
            return conflictType;
        }

        public TransactionsPartition getConflictFrom() {
            return conflictingPartitions[0];
        }

        public TransactionsPartition getConflictWith() {
            return conflictingPartitions[1];
        }
    }

    public static class SerializableConflict implements Serializable {
        private ByteArrayWrapper key;
        private eConflictType conflictType;
        private int[] conflictingPartIds = new int [2];

        public SerializableConflict(Conflict conflict) {
            this.key = conflict.getKey();
            this.conflictType = conflict.getConflictType();
            this.conflictingPartIds[0] = conflict.getConflictFrom().getId();
            this.conflictingPartIds[1] = conflict.getConflictWith().getId();
        }
    }

    private Map<TransactionsPartition, Set<Conflict>> conflictsPerPartition = new HashMap<>();
    private TransactionsPartitioner partitioner;

    public TransactionConflictDetector(TransactionsPartitioner partitioner) {
        this.partitioner = partitioner;
    }

    // We record all the partitions reading to a given key.
    // If another group attempts to write the same key, there will be a conflict recorded with every readers,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    private Map<ByteArrayWrapper, Collection<Integer>> partitionReadersPerKey = new HashMap<>();
    // We record only one partition writing to a given key.
    // If another group attempts to write the same key, there will be a conflict recorded with the first writer,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    private Map<ByteArrayWrapper, Integer> partitionWriterPerKey = new HashMap<>();
    // We record all the partitions accessing to the keys of a given account.
    // If another group attempts to delete this account, there will be a conflict recorded with every accessors,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    private Map<ByteArrayWrapper, Collection<Integer>> accessedAccounts = new HashMap<>();
    // We record only one partition deleting a given account.
    // If another group attempts to delete the same account, there will be a conflict recorded with the first deletor,
    // resulting in: either failure of block validation, or merging of the conflicting partitions
    private Map<ByteArrayWrapper, Integer> deletedAccounts = new HashMap<>();

    @Override
    public synchronized void onReadKey(ICacheTracking cacheTracking, ByteArrayWrapper key, int partitionId) {
        // There is a conflict if :
        // - the key has already been written by another partition
        // or
        // - the account from that key has been deleted by another partition
        logger.info("key [{}] is read by group [{}]",
                key.toString(), partitionId);

        if (!checkKeyAlreadyWritten(key, partitionId)) {
            // No conflict detected -> track the read access to the key
            trackReadAccess(key, partitionId);
        }
        checkAccountDeleted(cacheTracking.getAccountFromKey(key), partitionId);
        // Whatever if there is a conflict or not, track access to the account of this key
        trackAccessToAccount(cacheTracking.getAccountFromKey(key), partitionId);
    }

    @Override
    public synchronized void onWriteKey(ICacheTracking cacheTracking, ByteArrayWrapper key, int partitionId) {
        // There is a conflict if :
        // - the key has already been written by another partition
        // or
        // - the key has already been read by one or more other partitions
        // or
        // - the account from that key has been deleted by another partition
        logger.info("key [{}] is written by group [{}]",
                key.toString(), partitionId);

        if (!checkKeyAlreadyWritten(key, partitionId)) {
            // No conflict detected -> track the write access to the key
            trackWriteAccess(key, partitionId);
            // Only check if already read by one or more other partitions is there is no conflict with another writer,
            // because in such a case, the readers are already in conflict with it.
            checkKeyAlreadyRead(key, partitionId);
        }
        checkAccountDeleted(cacheTracking.getAccountFromKey(key), partitionId);
        // Whatever if there is a conflict or not, track access to the account of this key
        trackAccessToAccount(cacheTracking.getAccountFromKey(key), partitionId);
    }

    @Override
    public synchronized void onDeleteAccount(ICacheTracking cacheTracking, ByteArrayWrapper account, int partitionId) {
        // There is a conflict if :
        // - some keys of this account have been accessed (read or written) by another partition
        // or
        // - the account have already been deleted by another partition

        checkAccountAccessed(account, partitionId);
        if (!checkAccountDeleted(account, partitionId)) {
            trackAccountDeleted(account, partitionId);
        }
    }

    private Conflict createConflict(ByteArrayWrapper key, int conflictPartId, int originPartId, eConflictType conflictType) {
        logger.info("Record conflict between groups '[{}]' and '[{}}]'", conflictPartId, originPartId);
        TransactionsPartition conflictPartition = partitioner.fromId(conflictPartId);
        TransactionsPartition originPartition = partitioner.fromId(originPartId);
        if (conflictPartition == null) {
            logger.error("Unable to get the partition for id " + conflictPartId);
        }
        if (originPartition == null) {
            logger.error("Unable to get the partition for id " + conflictPartId);
        }
        return new Conflict(key, conflictPartition, originPartition, conflictType);
    }

    protected void recordConflict(Conflict conflict) {
        Collection<Conflict> conflicts;
        conflicts = conflictsPerPartition.computeIfAbsent(conflict.getConflictFrom(), k -> new HashSet<>());
        conflicts.add(conflict);
        conflicts = conflictsPerPartition.computeIfAbsent(conflict.getConflictWith(), k -> new HashSet<>());
        conflicts.add(conflict);
    }

    private boolean checkKeyAlreadyWritten(ByteArrayWrapper key, int partId) {
        Integer otherPartId = partitionWriterPerKey.get(key);
        if ((otherPartId == null) || otherPartId.equals(partId)) {
            return false;
        } else {
            logger.info("key [{}] has already been written by group [{}] --> conflict from group [{}]",
                    key.toString(), otherPartId, partId);
            recordConflict(createConflict(key, otherPartId, partId, eConflictType.WRITTEN_BEFORE_ACCESSED));
        }
        return true;
    }

    private boolean checkKeyAlreadyRead(ByteArrayWrapper key, int partitionId) {
        Collection<Integer> otherPartIds = partitionReadersPerKey.get(key);
        if (otherPartIds == null) {
            return false;
        }
        // there can not be a conflict with itself
        otherPartIds.remove(partitionId);
        if (otherPartIds.isEmpty()) {
            return false;
        }
        // Record a conflict for each reading group
        otherPartIds.forEach(conflictPartId -> {
            recordConflict(createConflict(key, conflictPartId, partitionId, eConflictType.ACCESSED_BEFORE_WRITTEN));
            logger.info("key [{}] has already been read by group [{}] --> conflict from group [{}]",
                    key.toString(), conflictPartId, partitionId);
        });
        return true;
    }

    private boolean checkAccountAccessed(ByteArrayWrapper account, int partitionId) {
        Collection<Integer> otherPartIds = accessedAccounts.get(account);
        if (otherPartIds == null) {
            return false;
        }
        // there can not be a conflict with itself
        otherPartIds.remove(partitionId);
        if (otherPartIds.isEmpty()) {
            return false;
        }
        // Record a conflict for each reading group
        otherPartIds.forEach(conflictPartId -> recordConflict(createConflict(account, conflictPartId, partitionId, eConflictType.ACCESSED_BEFORE_DELETE)));
        return true;
    }

    private boolean checkAccountDeleted(ByteArrayWrapper account, int partitionId) {
        Integer otherPartId = deletedAccounts.get(account);
        if ((otherPartId == null) || otherPartId.equals(partitionId)) {
            return false;
        } else {
            recordConflict(createConflict(account, otherPartId, partitionId, eConflictType.DELETED_BEFORE_ACCESSED));
        }
        return true;
    }

    protected void trackReadAccess(ByteArrayWrapper key, int partitionId) {
        // Imprtant ! use a HashSet and not an ArrayList because we don't want to add several times the same reader
        Collection<Integer> readers = partitionReadersPerKey.computeIfAbsent(key, k -> new HashSet<>());
        // Add the reader if not already in the collection
        readers.add(partitionId);
    }

    protected void trackWriteAccess(ByteArrayWrapper key, int partitionId) {
        partitionWriterPerKey.put(key, partitionId);
    }

    protected void trackAccessToAccount(ByteArrayWrapper account, int partitionId) {
        // Important ! use a HashSet and not an ArrayList because we don't want to add several times the same accessor
        Collection<Integer> accessors = accessedAccounts.computeIfAbsent(account, k -> new HashSet<>());
        // Add the accessor if not already in the collection
        accessors.add(partitionId);
    }

    protected void trackAccountDeleted(ByteArrayWrapper account, int partitionId) {
        deletedAccounts.put(account, partitionId);
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

    private void discardPartition(TransactionsPartition partitionToDiscard, TransactionsPartition replacingPartition) {
        Integer oldPartId = partitionToDiscard.getId();
        Integer newPartId = replacingPartition.getId();
        logger.info("Discard partition [{}] into group [{}]", oldPartId, newPartId);

        for (Collection<Integer> readers: partitionReadersPerKey.values()) {
            if (readers.remove(oldPartId)) {
                // remove() returns true if the element was present and has been removed
                readers.add(newPartId);
            }
        }
        int finalNewPartId = newPartId;
        partitionWriterPerKey.replaceAll((k, v) -> (oldPartId.equals(v)) ? finalNewPartId : v);
        for (Collection<Integer> accessors: accessedAccounts.values()) {
            if (accessors.remove(oldPartId)) {
                // remove() returns true if the element was present and has been removed
                accessors.add(newPartId);
            }
        }
        int finalNewPartId2 = newPartId;
        deletedAccounts.replaceAll((k, v) -> (oldPartId.equals(v)) ? finalNewPartId2 : v);

        Set<Conflict> conflicts = conflictsPerPartition.remove(partitionToDiscard);
        if (conflicts != null) {
            Set<Conflict> conflictsToAdd = new HashSet<>();
            Set<TransactionsPartition> entriesToRemove = new HashSet<>();
            for (Conflict conflict : conflicts) {
                int conflictingPartitionIndex = 0;
                TransactionsPartition conflictingPartition = conflict.conflictingPartitions[conflictingPartitionIndex];
                if (conflictingPartition == partitionToDiscard) {
                    conflictingPartitionIndex = 1;
                    conflictingPartition = conflict.conflictingPartitions[conflictingPartitionIndex];
                }
                if (replacingPartition == conflictingPartition) {
                    // remove the conflict from the list
                    Set<Conflict> otherConflicts = conflictsPerPartition.get(conflictingPartition);
                    otherConflicts.remove(conflict);
                    if (otherConflicts.isEmpty()) {
                        entriesToRemove.add(conflictingPartition);
                    }
                } else {
                    // replace conflicting partition in Conflict object
                    conflict.conflictingPartitions[(conflictingPartitionIndex + 1) % 2] = replacingPartition;
                    conflictsToAdd.add(conflict);
                }
            }
            for (TransactionsPartition partition : entriesToRemove) {
                conflictsPerPartition.remove(partition);
            }
            if (!conflictsToAdd.isEmpty()) {
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

        private Collection<SerializableConflict> conflicts = new ArrayList<>();

        public TransactionConflictException(Collection<Conflict> conflicts) {
            for(Conflict conflict: conflicts) {
                this.conflicts.add(new SerializableConflict(conflict));
            }
        }

        public Collection<SerializableConflict> getConflicts() {
            return new ArrayList<>(conflicts);
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
