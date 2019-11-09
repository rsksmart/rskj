package co.rsk.core.bc;

import co.rsk.db.ICacheTracking;
import org.ethereum.db.ByteArrayWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * This class tracks accesses in repository (read/write or delete keys), per threadGroup
 * and detect if there are confilcts between threadGroups, that means 2 different threadGroups
 * have accessed to the same key, whatever the access type (read/write or delete)
 */
class TransactionConflictDetector implements ICacheTracking.Listener {

    private static int instancesCounter = 0;
    private int instanceId;
    boolean hasConflict = false;
    String conflict = "";
    public TransactionConflictDetector( ) {
        instanceId = instancesCounter++;
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
            conflict = "ThreadGroup " + threadGroupName + " attempts to access at key " +
                    key.toString() + " but it has already been accessed by another ThreadGroup (" +
                    otherThreadGroup + ")";
            hasConflict = true;
        }
    }

    public synchronized boolean hasConflict() {
        return hasConflict;
    }

    public synchronized String getConflictMessage() {
        return conflict;
    }
}
