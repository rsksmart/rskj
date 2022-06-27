package org.ethereum.db;

import co.rsk.core.bc.IReadWrittenKeysTracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DummyReadWrittenKeysTracker implements IReadWrittenKeysTracker {

    private final Map<ByteArrayWrapper, Set<Long>> temporalReadKeys;
    private final Map<ByteArrayWrapper, Long> temporalWrittenKeys;

    public DummyReadWrittenKeysTracker() {
        this.temporalReadKeys = new HashMap<>();
        this.temporalWrittenKeys = new HashMap<>();
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalReadKeys() {
        return new HashSet<>(this.temporalReadKeys.keySet());
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalWrittenKeys() {
        return new HashSet<>(this.temporalWrittenKeys.keySet());
    }

    @Override
    public boolean hasCollided() {
        return false;
    }

    @Override
    public void addNewReadKey(ByteArrayWrapper key) {
        //Dummy tracker does not store added keys
    }

    @Override
    public void addNewWrittenKey(ByteArrayWrapper key) {
        //Dummy tracker does not store added keys
    }

    @Override
    public void clear() {
        //Dummy tracker does not store added keys
    }
}
