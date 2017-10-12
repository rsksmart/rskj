package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class DownloadingBodiesSyncState  extends BaseSyncState {
    private Queue<BlockHeader> pendingHeaders;
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();
    ;

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Queue<BlockHeader> pendingHeaders) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.pendingHeaders = pendingHeaders;
    }

    @Override
    public void newBody(BodyResponseMessage message) {
        if (!isExpectedBody(message.getId())) {
            // Invalid body response
            // TODO(mc) do peer scoring, banning
            syncEventsHandler.onErrorSyncing(
                    "Unexpected body received from node {}",
                    syncInformation.getSelectedPeerId());
            return;
        }

        // TODO(mc) validate transactions and uncles are part of this block (with header)
        // we know it exists because it was called from a SyncEvent
        BlockHeader header = pendingBodyResponses.get(message.getId()).header;
        Block block = Block.fromValidData(header, message.getTransactions(), message.getUncles());
        syncInformation.saveBlock(block);

        if (!pendingHeaders.isEmpty()) {
            requestBody();
            return;
        }

        // Finished syncing
        syncEventsHandler.onCompletedSyncing();
    }

    @Override
    public void onEnter() {
        requestBody();
    }

    private void requestBody(){
        BlockHeader header = pendingHeaders.remove();
        long messageId = syncEventsHandler.sendBodyRequest(header);
        NodeID peerId = syncInformation.getSelectedPeerId();
        pendingBodyResponses.put(messageId, new PendingBodyResponse(peerId, header));
        resetTimeElapsed();
    }

    public boolean isExpectedBody(long requestId) {
        PendingBodyResponse expected = pendingBodyResponses.get(requestId);
        return expected != null && expected.nodeID.equals(syncInformation.getSelectedPeerId());
    }


    @Override
    public boolean isSyncing() {
        return true;
    }

    @VisibleForTesting
    public void expectBodyResponseFor(long requestId, NodeID nodeID, BlockHeader header) {
        pendingBodyResponses.put(requestId, new PendingBodyResponse(nodeID, header));
    }

    private static class PendingBodyResponse {
        private NodeID nodeID;
        private BlockHeader header;

        PendingBodyResponse(NodeID nodeID, BlockHeader header) {
            this.nodeID = nodeID;
            this.header = header;
        }
    }
}
