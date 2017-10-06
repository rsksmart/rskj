package co.rsk.net.sync;


import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;

public class SimpleSyncInformation implements SyncInformation {
    private boolean hasLowerDifficulty = true;

    @Override
    public boolean isKnownBlock(byte[] hash) {
        return false;
    }

    @Override
    public boolean hasLowerDifficulty(MessageChannel peer) {
        return hasLowerDifficulty;
    }

    @Override
    public NodeID getSelectedPeerId() {
        return null;
    }

    @Override
    public Status getSelectedPeerStatus() {
        return null;
    }

    @Override
    public boolean isExpectingMoreBodies() {
        return false;
    }

    @Override
    public boolean isExpectedBody(long expected) {
        return false;
    }

    @Override
    public void saveBlock(BodyResponseMessage message) { }

    public SimpleSyncInformation withWorsePeers() {
        this.hasLowerDifficulty = false;
        return this;
    }
}