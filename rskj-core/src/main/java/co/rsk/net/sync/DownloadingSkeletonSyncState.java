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

    private final PeersInformation peersInformation;
    private final Map<NodeID, List<BlockIdentifier>> skeletons;
    private final Map<NodeID, Boolean> availables;
    private final NodeID selectedPeerId;
    private long connectionPoint;
    private long expectedSkeletons;
    private boolean selectedPeerAnswered;


    public DownloadingSkeletonSyncState(SyncConfiguration syncConfiguration,
                                        SyncEventsHandler syncEventsHandler,
                                        PeersInformation peersInformation,
                                        NodeID selectedPeerId,
                                        long connectionPoint) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeerId = selectedPeerId;
        this.connectionPoint = connectionPoint;
        this.skeletons = new HashMap<>();
        this.availables = new HashMap<>();
        this.selectedPeerAnswered = false;
        this.peersInformation = peersInformation;
        this.expectedSkeletons = 0;
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton, MessageChannel peer) {
        NodeID peerId = peer.getPeerNodeID();
        boolean isSelectedPeer = peerId.equals(selectedPeerId);

        // defensive programming: this should never happen
        if (skeleton.size() < 2) {
            peersInformation.reportEvent("Invalid skeleton received from node {}",
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
            if (skeletons.isEmpty()){
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
            peersInformation.getPeerCandidates().stream()
                    .filter(availables::get)
                    .filter(c -> !skeletons.containsKey(c))
                    .forEach(p ->
                            peersInformation.reportEvent("Timeout waiting skeleton from node {}",
                                    EventType.TIMEOUT_MESSAGE, p, p));

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
        peersInformation.getPeerCandidates().forEach(this::trySendRequest);
        expectedSkeletons = availables.size();
    }

    private void trySendRequest(NodeID p) {
        boolean sent = syncEventsHandler.sendSkeletonRequest(p, connectionPoint);
        availables.put(p, sent);
        if (!sent){
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}", this.getClass(), p);
        }
    }
}
