package co.rsk.net.syncrefactor;


import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DecidingSyncState implements SyncState {

    private final SyncStateFactory factory;
    private final SyncConfiguration syncConfiguration;
    private PeersInformation peersInformation;
    private final SyncMessager syncMessager;
    private Duration timeElapsed;

    public DecidingSyncState(SyncStateFactory factory,
                             SyncConfiguration syncConfiguration,
                             PeersInformation peersInformation,
                             SyncMessager syncMessager) {
        this.factory = factory;
        this.syncConfiguration = syncConfiguration;
        this.peersInformation = peersInformation;
        this.syncMessager = syncMessager;
        this.timeElapsed = Duration.ZERO;
    }

    public CompletableFuture<SyncState> tick(Duration duration) {
        peersInformation.cleanExpired();
        timeElapsed = timeElapsed.plus(duration);
        if (peersInformation.count() > 0 &&
                timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingPeers()) >= 0) {

            Optional<NodeID> bpOpt = peersInformation.getBestPeer();
            if (bpOpt.isPresent()) {
                NodeID selectedPeerId = bpOpt.get();
                byte[] bestBlockHash = peersInformation.getPeer(selectedPeerId).getStatus().getBestBlockHash();

                return CompletableFuture.completedFuture(
                        factory.newCheckingBestHeaderSyncState(selectedPeerId,
                                new Keccak256(bestBlockHash)));
            }
        }
        return syncMessager.registerForTick().thenCompose(this::tick);
    }

    @Override
    public SyncState execute() {
        CompletableFuture<SyncState> tickResolution = syncMessager.registerForTick().thenCompose(this::tick);
        return tickResolution.join();
    }
}
