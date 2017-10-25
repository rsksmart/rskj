package co.rsk.net.sync;

import co.rsk.scoring.EventType;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.util.ByteUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

public class DownloadingHeadersSyncState extends BaseSyncState {

    private Queue<BlockHeader> pendingHeaders;
    private final SkeletonDownloadHelper skeletonDownloadHelper;

    public DownloadingHeadersSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation, List<BlockIdentifier> skeleton, long connectionPoint) {
        super(syncInformation, syncEventsHandler, syncConfiguration);

        this.pendingHeaders = new ArrayDeque<>();
        this.skeletonDownloadHelper = new SkeletonDownloadHelper(syncConfiguration, skeleton, connectionPoint);
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @Override
    public void newBlockHeaders(List<BlockHeader> chunk) {
        Optional<ChunkDescriptor> currentChunk = skeletonDownloadHelper.getCurrentChunk();
        if (!currentChunk.isPresent()
                || chunk.size() != currentChunk.get().getCount()
                || !ByteUtil.fastEquals(chunk.get(0).getHash(), currentChunk.get().getHash())) {
            syncEventsHandler.onErrorSyncing(
                    "Invalid chunk received from node {}",
                    EventType.INVALID_MESSAGE,
                    syncInformation.getSelectedPeerId());
            return;
        }

        pendingHeaders.add(chunk.get(chunk.size() - 1));

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

            pendingHeaders.add(header);
        }

        if (skeletonDownloadHelper.hasNextChunk()) {
            resetTimeElapsed();
            syncEventsHandler.sendBlockHeadersRequest(skeletonDownloadHelper.getNextChunk());
            return;
        }

        // Finished verifying headers
        syncEventsHandler.startDownloadingBodies(pendingHeaders);
    }

    @Override
    public void onEnter() {
        syncEventsHandler.sendBlockHeadersRequest(skeletonDownloadHelper.getNextChunk());
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return skeletonDownloadHelper.getSkeleton();
    }
}
