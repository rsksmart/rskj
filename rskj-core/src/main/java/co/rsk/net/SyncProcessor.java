package co.rsk.net;

import co.rsk.net.messages.*;
import co.rsk.net.sync.SyncState;
import com.google.common.annotations.VisibleForTesting;

import java.time.Duration;
import java.util.Set;

public interface SyncProcessor {
    void processStatus(MessageChannel sender, Status status);

    void processSkeletonResponse(MessageChannel peer, SkeletonResponseMessage message);

    void processBlockHashResponse(MessageChannel peer, BlockHashResponseMessage message);

    void processBlockHeadersResponse(MessageChannel peer, BlockHeadersResponseMessage message);

    void processBodyResponse(MessageChannel peer, BodyResponseMessage message);

    void processNewBlockHash(MessageChannel peer, NewBlockHashMessage message);

    void processBlockResponse(MessageChannel peer, BlockResponseMessage message);

    Set<NodeID> getKnownPeersNodeIDs();

    void onTimePassed(Duration timePassed);

    void stopSyncing();

    @VisibleForTesting
    SyncState getSyncState();
}
