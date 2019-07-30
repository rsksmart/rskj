package co.rsk.net.sync;

import java.time.Duration;

public class DecidingSyncState extends BaseSyncState {
    private PeersInformation knownPeers;

    public DecidingSyncState(SyncConfiguration syncConfiguration,
                             SyncEventsHandler syncEventsHandler,
                             PeersInformation knownPeers) {
        super(syncEventsHandler, syncConfiguration);

        this.knownPeers = knownPeers;
    }

    @Override
    public void newPeerStatus() {
        if (knownPeers.count() >= syncConfiguration.getExpectedPeers()) {
            tryStartSyncing();
        }
    }

    @Override
    public void tick(Duration duration) {
        knownPeers.cleanExpired();
        timeElapsed = timeElapsed.plus(duration);
        if (knownPeers.count() > 0 &&
                timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingPeers()) >= 0) {

            tryStartSyncing();
        }
    }

    private void tryStartSyncing() {
        knownPeers.getBestPeer().ifPresent(syncEventsHandler::startSyncing);
    }
}
