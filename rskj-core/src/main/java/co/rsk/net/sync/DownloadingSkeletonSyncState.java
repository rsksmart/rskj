package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.*;

public class DownloadingSkeletonSyncState extends BaseSyncState {

    private final PeersInformation peersInformation;
    private final Map<Peer, List<BlockIdentifier>> skeletons;
    private final Peer selectedPeer;
    private final List<Peer> candidates;
    private long connectionPoint;
    private long expectedSkeletons;
    private boolean selectedPeerAnswered;


    public DownloadingSkeletonSyncState(SyncConfiguration syncConfiguration,
                                        SyncEventsHandler syncEventsHandler,
                                        PeersInformation peersInformation,
                                        Peer peer,
                                        long connectionPoint) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeer = peer;
        this.connectionPoint = connectionPoint;
        this.skeletons = new HashMap<>();
        this.selectedPeerAnswered = false;
        this.peersInformation = peersInformation;
        this.candidates = peersInformation.getPeerCandidates();
        this.expectedSkeletons = 0;
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, Peer peer) {
        NodeID peerId = peer.getPeerNodeID();
        boolean isSelectedPeer = peer.equals(selectedPeer);

        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            peersInformation.reportEventWithLog("Invalid skeleton received from node {}",
                    peerId, EventType.INVALID_MESSAGE, peerId);

            // when the selected peer fails automatically all process restarts
            if (isSelectedPeer){
                syncEventsHandler.stopSyncing();
                return;
            }
        } else {
            skeletons.put(peer, skeleton);
        }

        expectedSkeletons--;
        selectedPeerAnswered = selectedPeerAnswered || isSelectedPeer;

        if (expectedSkeletons <= 0){
            if (skeletons.isEmpty()){
                syncEventsHandler.stopSyncing();
                return;
            }
            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint, peer);
        }
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            candidates.stream()
                    .filter(c -> !skeletons.containsKey(c))
                    .forEach(p ->
                            peersInformation.reportEventWithLog(
                                    "Timeout waiting skeleton from node {}",
                                    p.getPeerNodeID(),
                                    EventType.TIMEOUT_MESSAGE, p));

            // when the selected peer fails automatically all process restarts
            if (!selectedPeerAnswered){
                syncEventsHandler.stopSyncing();
                return;
            }

            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint, selectedPeer);
        }
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(
                selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }

    @Override
    public void onEnter() {
        peersInformation.getPeerCandidates().forEach(p -> syncEventsHandler.sendSkeletonRequest(p, connectionPoint));
    }
}
