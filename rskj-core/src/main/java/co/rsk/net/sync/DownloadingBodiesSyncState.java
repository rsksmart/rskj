package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockRootValidationRule;
import co.rsk.validators.BlockUnclesHashValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class DownloadingBodiesSyncState  extends BaseSyncState {
    private BlockValidationRule blockUnclesHashValidationRule;
    private BlockValidationRule blockTransactionsValidationRule;
    private Queue<BlockHeader> pendingHeaders;
    private Map<Long, PendingBodyResponse> pendingBodyResponses = new HashMap<>();

    public DownloadingBodiesSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Queue<BlockHeader> pendingHeaders) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.pendingHeaders = pendingHeaders;
        this.blockUnclesHashValidationRule = new BlockUnclesHashValidationRule();
        this.blockTransactionsValidationRule = new BlockRootValidationRule();
    }

    @Override
    public void newBody(BodyResponseMessage message) {
        if (!isExpectedBody(message.getId())) {
            // Invalid body response
            syncEventsHandler.onErrorSyncing(
                    "Unexpected body received from node {}",
                    EventType.UNEXPECTED_MESSAGE,
                    syncInformation.getSelectedPeerId());
            return;
        }

        // we know it exists because it was called from a SyncEvent
        BlockHeader header = pendingBodyResponses.get(message.getId()).header;
        Block block = Block.fromValidData(header, message.getTransactions(), message.getUncles());
        if (!blockUnclesHashValidationRule.isValid(block) || !blockTransactionsValidationRule.isValid(block)) {
            syncEventsHandler.onErrorSyncing(
                    "Invalid body received from node {}",
                    EventType.INVALID_MESSAGE,
                    syncInformation.getSelectedPeerId());
            return;
        }

        syncInformation.processBlock(block);

        if (!pendingHeaders.isEmpty()) {
            resetTimeElapsed();
            requestBody();
            return;
        }

        // Finished syncing
        syncEventsHandler.onCompletedSyncing();
    }

    @Override
    public void onEnter() {
        resetTimeElapsed();
        requestBody();
    }

    private void requestBody(){
        BlockHeader header = pendingHeaders.remove();
        long messageId = syncEventsHandler.sendBodyRequest(header);
        NodeID peerId = syncInformation.getSelectedPeerId();
        pendingBodyResponses.put(messageId, new PendingBodyResponse(peerId, header));
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
