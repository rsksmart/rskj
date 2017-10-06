package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    boolean hasLowerDifficulty(MessageChannel peer);

    NodeID getSelectedPeerId();

    Status getSelectedPeerStatus();

    boolean isExpectingMoreBodies();

    boolean isExpectedBody(long expected);

    // TODO(mc) don't receive a full message
    void saveBlock(BodyResponseMessage message);
}
