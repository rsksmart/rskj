package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.scoring.EventType;

public abstract class BaseSelectedPeerSyncState extends BaseSyncState {

    protected final Peer selectedPeer;

    protected BaseSelectedPeerSyncState(SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration, Peer peer) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeer = peer;
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                "Timeout waiting requests on {}", this.getClass());
    }

}
