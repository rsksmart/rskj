package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DownloadingSkeletonSyncState extends BaseSyncState {

    private final List<MessageChannel> candidates;
    private final Map<NodeID, List<BlockIdentifier>> skeletons;
    private long connectionPoint;
    private long expectedSkeletons;
    private boolean selectedPeerAnswered;


    public DownloadingSkeletonSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, PeersInformation knownPeers, long connectionPoint) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.connectionPoint = connectionPoint;
        this.skeletons = new HashMap<>();
        this.selectedPeerAnswered = false;
        this.candidates = knownPeers.getPeerCandidates();
        this.expectedSkeletons = candidates.size();
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, MessageChannel peer) {
        NodeID peerId = peer.getPeerNodeID();
        boolean isSelectedPeer = peerId == syncInformation.getSelectedPeerId();

        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            syncInformation.reportEvent("Invalid skeleton received from node {}",
                    EventType.INVALID_MESSAGE, peerId, peerId);

            // when the selected peer fails automatically all process restarts
            if (isSelectedPeer){
                syncEventsHandler.stopSyncing();
                return;
            }
        } else {
            skeletons.put(peerId, skeleton);
        }

        expectedSkeletons--;
        selectedPeerAnswered = selectedPeerAnswered || isSelectedPeer;

        if (expectedSkeletons <= 0){
            if (skeletons.size() == 0){
                syncEventsHandler.stopSyncing();
                return;
            }
            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint);
        }
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            candidates.stream()
                    .filter(c -> !skeletons.containsKey(c.getPeerNodeID()))
                    .forEach(p ->
                            syncInformation.reportEvent("Timeout waiting requests from node {}",
                                    EventType.TIMEOUT_MESSAGE, p.getPeerNodeID(), p.getPeerNodeID()));

            // when the selected peer fails automatically all process restarts
            if (!selectedPeerAnswered){
                syncEventsHandler.stopSyncing();
                return;
            }

            syncEventsHandler.startDownloadingHeaders(skeletons, connectionPoint);
        }
    }

    @Override
    public void onEnter() {
        candidates.forEach(p -> syncEventsHandler.sendSkeletonRequest(p, connectionPoint));
    }
}
