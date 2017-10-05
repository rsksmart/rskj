package co.rsk.net.sync;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    // eventually inline ConnectionPointFinder in the class that uses it
    ConnectionPointFinder getConnectionPointFinder();
}
