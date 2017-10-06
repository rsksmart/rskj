package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.Status;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    boolean hasLowerDifficulty(MessageChannel peer);

    Status getSelectedPeerStatus();
}
