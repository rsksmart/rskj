package co.rsk.net.sync;

import java.time.Duration;

public class DecidingSyncState extends BaseSyncState {
    private PeersInformation knownPeers;

    public DecidingSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, PeersInformation knownPeers) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.knownPeers = knownPeers;
    }

    @Override
    public boolean isSyncing() {
        return false;
    }

    @Override
    public void newPeerStatus() {
        if (knownPeers.count() >= syncConfiguration.getExpectedPeers()) {
            canStartSyncing();
        }
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (knownPeers.countIf(s -> !s.isExpired(syncConfiguration.getExpirationTimePeerStatus())) > 0 &&
                timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingPeers()) >= 0) {

            canStartSyncing();
        } else {
            knownPeers.cleanExpired(syncConfiguration.getExpirationTimePeerStatus());
        }
    }

    private void canStartSyncing() {
        knownPeers.getBestPeer()
                .filter(syncInformation::hasLowerDifficulty)
                .ifPresent(syncEventsHandler::startSyncing);
    }
}
