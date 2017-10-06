package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.Status;
import org.ethereum.core.BlockIdentifier;

import java.util.List;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    // eventually inline ConnectionPointFinder in the class that uses it
    ConnectionPointFinder getConnectionPointFinder();

    boolean hasLowerDifficulty(MessageChannel peer);

    Status getSelectedPeerStatus();
}
