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
    private List<Stack<BlockHeader>> pendingHeaders;
    private final ChunksDownloadHelper chunksDownloadHelper;

    public DownloadingHeadersSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, Map<NodeID, List<BlockIdentifier>> skeletons, long connectionPoint) {
        super(syncInformation, syncEventsHandler, syncConfiguration);
        this.pendingHeaders = new ArrayList<>();
        this.skeletons = skeletons;
        this.chunksDownloadHelper = new ChunksDownloadHelper(syncConfiguration, skeletons.get(syncInformation.getSelectedPeerId()), connectionPoint);
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
        Optional<ChunkDescriptor> currentChunk = chunksDownloadHelper.getCurrentChunk();
        if (!currentChunk.isPresent()
                || chunk.size() != currentChunk.get().getCount()
                || !ByteUtil.fastEquals(chunk.get(0).getHash(), currentChunk.get().getHash())) {
            syncEventsHandler.onErrorSyncing(
                    "Invalid chunk received from node {}",
                    EventType.INVALID_MESSAGE,
                    syncInformation.getSelectedPeerId());
            return;
        }

        List<BlockHeader> headers = new ArrayList<>();
        headers.add(chunk.get(chunk.size() - 1));

        for (int k = 1; k < chunk.size(); ++k) {
            BlockHeader parentHeader = chunk.get(chunk.size() - k);
            BlockHeader header = chunk.get(chunk.size() - k - 1);

            if (!syncInformation.blockHeaderIsValid(header, parentHeader)) {
                syncEventsHandler.onErrorSyncing(
                        "Invalid header received from node {}",
                        EventType.INVALID_MESSAGE,
                        syncInformation.getSelectedPeerId());
                return;
            }

            headers.add(header);
        }
        Stack<BlockHeader> headerStack = new Stack<>();
        Collections.reverse(headers);
        headerStack.addAll(headers);
        pendingHeaders.add(headerStack);

        if (!chunksDownloadHelper.hasNextChunk()) {
            // Finished verifying headers
            syncEventsHandler.startDownloadingBodies(pendingHeaders, skeletons);
            return;
        }

        resetTimeElapsed();
        syncEventsHandler.sendBlockHeadersRequest(chunksDownloadHelper.getNextChunk());
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendBlockHeadersRequest(chunksDownloadHelper.getNextChunk());
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return chunksDownloadHelper.getSkeleton();
    }
}
