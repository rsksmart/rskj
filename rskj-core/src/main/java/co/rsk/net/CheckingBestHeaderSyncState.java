package co.rsk.net;

import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.ByteUtil;

import java.util.List;

public class CheckingBestHeaderSyncState extends BaseSyncState implements SyncState {
    private final ChunkDescriptor miniChunk;

    public CheckingBestHeaderSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, byte[] bestBlockHash) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.miniChunk = new ChunkDescriptor(bestBlockHash, 1);
    }

    @Override
    public void onEnter(){
        syncEventsHandler.sendBlockHeadersRequest(miniChunk);
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk){
        BlockHeader header = chunk.get(0);
        if (!ByteUtil.fastEquals(header.getHash(), miniChunk.getHash()) ||
                !syncInformation.blockHeaderIsValid(header)) {
            syncEventsHandler.onErrorSyncing(
                    "Invalid chunk received from node {}", EventType.INVALID_HEADER, this.getClass(),
                    syncInformation.getSelectedPeerId());
            return;
        }

        syncEventsHandler.startFindingConnectionPoint();
    }
}
