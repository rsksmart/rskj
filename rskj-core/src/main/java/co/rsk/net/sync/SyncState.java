package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public interface SyncState {
    void newBlockHeaders(List<BlockHeader> chunk);

    // TODO(mc) don't receive a full message
    void newBody(BodyResponseMessage message, Peer peer);

    void newConnectionPointData(byte[] hash);

    /**
     * should only be called when a new peer arrives
     */
    void newPeerStatus();

    void newSkeleton(List<BlockIdentifier> skeletonChunk, Peer peer);

    void onEnter();

    void tick(Duration duration);
}
