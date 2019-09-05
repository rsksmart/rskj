package co.rsk.net.sync;


import java.time.Duration;

public class DecidingSyncState extends BaseSyncState {

    private PeersInformation peersInformation;

    public DecidingSyncState(SyncConfiguration syncConfiguration,
                             SyncEventsHandler syncEventsHandler,
                             PeersInformation peersInformation) {
        super(syncEventsHandler, syncConfiguration);

        this.peersInformation = peersInformation;
    }

    @Override
    public void newPeerStatus() {
        if (peersInformation.count() >= syncConfiguration.getExpectedPeers()) {
            tryStartSyncing();
        }
    }

    @Override
    public void tick(Duration duration) {
        peersInformation.cleanExpired();
        timeElapsed = timeElapsed.plus(duration);
        if (peersInformation.count() > 0 &&
                timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingPeers()) >= 0) {

            tryStartSyncing();
        }
    }

    private void tryStartSyncing() {
        peersInformation.getBestPeer().ifPresent(syncEventsHandler::startSyncing);
    }
}
