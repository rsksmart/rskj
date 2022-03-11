package org.ethereum.db;

import co.rsk.core.bc.IReadWrittenKeysTracker;

import java.util.HashSet;
import java.util.Set;

public class DummyReadWrittenKeysTracker implements IReadWrittenKeysTracker {

    private final HashSet<ByteArrayWrapper> temporalReadKeys;
    private final HashSet<ByteArrayWrapper> temporalWrittenKeys;

    public DummyReadWrittenKeysTracker() {
        this.temporalReadKeys = new HashSet<>();
        this.temporalWrittenKeys = new HashSet<>();
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalReadKeys() {
        return temporalReadKeys;
    }

    @Override
    public Set<ByteArrayWrapper> getTemporalWrittenKeys() {
        return temporalWrittenKeys;
    }

    @Override
    public void addNewReadKey(ByteArrayWrapper key) {

    }

    @Override
    public void addNewWrittenKey(ByteArrayWrapper key) {

    }

    @Override
    public void clear() {

    }
}
