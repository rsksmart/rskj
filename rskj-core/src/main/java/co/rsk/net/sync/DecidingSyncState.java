package co.rsk.net.sync;


import co.rsk.net.NodeID;
import org.ethereum.core.Blockchain;

import java.time.Duration;
import java.util.Optional;

public class DecidingSyncState extends BaseSyncState {

    private PeersInformation peersInformation;
    private final Blockchain forwardBlockchain;

    public DecidingSyncState(SyncConfiguration syncConfiguration,
                             SyncEventsHandler syncEventsHandler,
                             PeersInformation peersInformation,
                             Blockchain forwardBlockchain) {
        super(syncEventsHandler, syncConfiguration);

        this.peersInformation = peersInformation;
        this.forwardBlockchain = forwardBlockchain;
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
        Optional<NodeID> bestPeer = peersInformation.getBestPeer();
        if (!bestPeer.isPresent()) {
            return;
        }

        long bpBestBlock = peersInformation.getPeer(bestPeer.get()).getStatus().getBestBlockNumber();
        boolean shouldForwardSync = bpBestBlock - forwardBlockchain.getBestBlock().getNumber() > 1000000;

        syncEventsHandler.startSyncing(bestPeer.get(), shouldForwardSync);
    }
}
