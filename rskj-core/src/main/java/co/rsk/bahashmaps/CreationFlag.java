package co.rsk.bahashmaps;

import java.util.EnumSet;

public enum CreationFlag {
    storeKeys,
    supportNullValues,  // Allow values to be null, and stored as such in the map
    allowRemovals,      // allow remove() to really remove the values from the heap
    supportBigValues, // support values with lengths higher than 127 bytes to be efficiently handled
    supportAdditionalKV, // Support KVs with keys that are not hashes of data.
    flushAfterPut, // Provides full ACID properties. Every put() or updateBatch() is made durable.
    atomicBatches,
    variableLengthKeys,
    useLogForBatchConsistency,
    useMaxOffsetForBatchConsistency,
    useMWChecksumForSlotConsistency,
    useDBForDescriptions,
    autoUpgrade,
    AlignSlotInPages;

    public static final EnumSet<CreationFlag> Default = EnumSet.of(
            storeKeys,variableLengthKeys,
            supportNullValues ,  allowRemovals ,supportBigValues,
            useMaxOffsetForBatchConsistency,
            useMWChecksumForSlotConsistency
    );
    public static final EnumSet<CreationFlag> All = EnumSet.allOf(CreationFlag.class);

    public static EnumSet<CreationFlag> fromBinary(int mask) {
        EnumSet<CreationFlag> set = EnumSet.noneOf(CreationFlag.class);
        for (CreationFlag value : CreationFlag.values()) {
            if ((mask & (1 << value.ordinal())) != 0) {
                set.add(value);
            }
        }
        return set;
    }

    public static int toBinary(EnumSet<CreationFlag> set) {
        int mask = 0;

        for (CreationFlag value : CreationFlag.values()) {
            if (set.contains(value)) {
                mask |= (1 << value.ordinal());
            }
        }
        return mask;
    }
}