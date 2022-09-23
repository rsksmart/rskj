package org.ethereum.db;

import co.rsk.core.bc.IReadWrittenKeysTracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DummyReadWrittenKeysTracker implements IReadWrittenKeysTracker {

    private final Map<Long, Set<ByteArrayWrapper>> readKeysByThread;
    private final Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread;

    public DummyReadWrittenKeysTracker() {
        this.readKeysByThread = new HashMap<>();
        this.writtenKeysByThread = new HashMap<>();
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadReadKeys() {
        return new HashSet<>();
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadWrittenKeys() {
        return new HashSet<>();
    }

    @Override
    public Map<Long, Set<ByteArrayWrapper>> getReadKeysByThread() {
        return readKeysByThread;
    }

    @Override
    public Map<Long, Set<ByteArrayWrapper>> getWrittenKeysByThread() {
        return writtenKeysByThread;
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
    public boolean detectCollision(){
        return false;
    }

    @Override
    public void clear() {
        //Dummy tracker does not store added keys
    }
}
