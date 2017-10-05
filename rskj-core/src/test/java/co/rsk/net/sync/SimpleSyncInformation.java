package co.rsk.net.sync;


import co.rsk.net.MessageChannel;

public class SimpleSyncInformation implements SyncInformation {
    private boolean hasLowerDifficulty = true;

    @Override
    public boolean isKnownBlock(byte[] hash) {
        return false;
    }

    @Override
    public ConnectionPointFinder getConnectionPointFinder() {
        return null;
    }

    @Override
    public boolean hasLowerDifficulty(MessageChannel peer) {
        return hasLowerDifficulty;
    }

    public SimpleSyncInformation withWorsePeers() {
        this.hasLowerDifficulty = false;
        return this;
    }
}