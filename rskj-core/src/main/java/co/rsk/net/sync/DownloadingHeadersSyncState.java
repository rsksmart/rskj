package co.rsk.net.sync;

import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.util.ByteUtil;

import java.util.*;

public class DownloadingHeadersSyncState extends BaseSyncState {

    private final Map<NodeID, List<BlockIdentifier>> skeletons;
    private final List<Deque<BlockHeader>> pendingHeaders;
    private final ChunksDownloadHelper chunksDownloadHelper;

    public DownloadingHeadersSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.pendingHeaders = new ArrayList<>();
        this.skeletons = skeletons;
        this.chunksDownloadHelper = new ChunksDownloadHelper(syncConfiguration, skeletons.get(syncInformation.getSelectedPeerId()), connectionPoint);
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
        Optional<ChunkDescriptor> currentChunk = chunksDownloadHelper.getCurrentChunk();
        if (!currentChunk.isPresent()
                || chunk.size() != currentChunk.get().getCount()
                || !ByteUtil.fastEquals(chunk.get(0).getHash().getBytes(), currentChunk.get().getHash())) {
            syncEventsHandler.onErrorSyncing(
                    "Invalid chunk received from node {} {}", EventType.INVALID_MESSAGE,
                    syncInformation.getSelectedPeerId(), currentChunk.get().getHash());
            return;
        }

        Deque<BlockHeader> headers = new ArrayDeque<>();
        // the headers come ordered by block number desc
        // we start adding the first parent header
        headers.add(chunk.get(chunk.size() - 1));

        for (int k = 1; k < chunk.size(); ++k) {
            BlockHeader parentHeader = chunk.get(chunk.size() - k);
            BlockHeader header = chunk.get(chunk.size() - k - 1);

            if (!syncInformation.blockHeaderIsValid(header, parentHeader)) {
                syncEventsHandler.onErrorSyncing(
                        "Invalid header received from node {} {} {}", EventType.INVALID_HEADER,
                        syncInformation.getSelectedPeerId(), header.getNumber(), header.getShortHash());
                return;
            }

            headers.add(header);
        }

        pendingHeaders.add(headers);

        if (!chunksDownloadHelper.hasNextChunk()) {
            // Finished verifying headers
            syncEventsHandler.startDownloadingBodies(pendingHeaders, skeletons);
            return;
        }

        resetTimeElapsed();
        trySendRequest();
    }

    @Override
    public void onEnter() {
        trySendRequest();
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return chunksDownloadHelper.getSkeleton();
    }

    private void trySendRequest() {
        boolean sent = syncEventsHandler.sendBlockHeadersRequest(chunksDownloadHelper.getNextChunk());
        if (!sent) {
            syncEventsHandler.onSyncIssue("Channel failed to sent on {} to {}",
                    this.getClass(), syncInformation.getSelectedPeerId());
        }
    }
}
