package co.rsk.net;

import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.ByteUtil;

import java.util.List;

public class CheckingBestHeaderSyncState extends BaseSyncState implements SyncState {
    private final BlockHeaderValidationRule blockHeaderValidationRule;
    private final Peer selectedPeer;
    private final ChunkDescriptor miniChunk;

    public CheckingBestHeaderSyncState(
            SyncConfiguration syncConfiguration,
            SyncEventsHandler syncEventsHandler,
            BlockHeaderValidationRule blockHeaderValidationRule,
            Peer peer,
            byte[] bestBlockHash) {
        super(syncEventsHandler, syncConfiguration);
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.selectedPeer = peer;
        this.miniChunk = new ChunkDescriptor(bestBlockHash, 1);
    }

    @Override
    public void onEnter(){
        trySendRequest();
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk){
        BlockHeader header = chunk.get(0);
        if (!ByteUtil.fastEquals(header.getHash().getBytes(), miniChunk.getHash()) ||
                !blockHeaderValidationRule.isValid(header)) {
            syncEventsHandler.onErrorSyncing(
                    selectedPeer.getPeerNodeID(),
                    "Invalid chunk received from node {}", EventType.INVALID_HEADER,
                    this.getClass());
            return;
        }

        syncEventsHandler.startFindingConnectionPoint(selectedPeer);
    }

    private void trySendRequest() {
        boolean sent = syncEventsHandler.sendBlockHeadersRequest(selectedPeer, miniChunk);
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), selectedPeer.getPeerNodeID());
        }
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeer.getPeerNodeID(),
                "Timeout waiting requests {}",
                EventType.TIMEOUT_MESSAGE,
                this.getClass(),
                selectedPeer.getPeerNodeID());
    }
}
