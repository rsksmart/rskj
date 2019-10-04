package co.rsk.net.sync;


import co.rsk.net.Peer;
import org.ethereum.db.BlockStore;

import java.time.Duration;
import java.util.Optional;

public class DecidingSyncState extends BaseSyncState {
    private PeersInformation peersInformation;
    private final BlockStore blockStore;

    public DecidingSyncState(SyncConfiguration syncConfiguration,
                             SyncEventsHandler syncEventsHandler,
                             PeersInformation peersInformation,
                             BlockStore blockStore) {
        super(syncEventsHandler, syncConfiguration);

        this.peersInformation = peersInformation;
        this.blockStore = blockStore;
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

        Optional<Peer> bestPeer = peersInformation.getBestPeer();
        if (!bestPeer.isPresent()) {
            return;
        }

        long bpBestBlockNumber = peersInformation.getPeer(bestPeer.get().getPeerNodeID()).getStatus().getBestBlockNumber();
        long distanceToTip = bpBestBlockNumber - blockStore.getBestBlock().getNumber();
        if (distanceToTip > syncConfiguration.getLongSyncLimit() || blockStore.getMinNumber() == 0) {
            syncEventsHandler.startSyncing(bestPeer.get());
            return;
        }
        syncEventsHandler.backwardSyncing(bestPeer.get());
    }
}
