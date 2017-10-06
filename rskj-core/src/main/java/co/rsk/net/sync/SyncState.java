package co.rsk.net.sync;

import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;

public interface SyncState {
    @Nonnull
    SyncStatesIds getId();

    default void messageSent() { }

    /**
     * should only be called when a new peer arrives
     */
    default void newPeerStatus() { }

    // TODO(mc) don't receive a full message
    default void newBody(BodyResponseMessage message) { }

    default void newConnectionPointData(byte[] hash) { }

    default void newSkeleton(List<BlockIdentifier> skeletonChunk) { }

    default void onEnter() { }

    default void tick(Duration duration) { }

    default boolean isSyncing(){
        return false;
    }
}
