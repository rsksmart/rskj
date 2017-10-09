package co.rsk.net.sync;

import co.rsk.net.Status;
import co.rsk.net.messages.BlockHeadersResponseMessage;
import co.rsk.net.messages.BodyResponseMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.util.ByteUtil;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

public class SyncingWithPeerSyncState implements SyncState {
    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private SyncInformation syncInformation;

    private ConnectionPointFinder connectionPointFinder;
    private Queue<BlockHeader> pendingHeaders;
    private Duration timeElapsed;

    public SyncingWithPeerSyncState(SyncConfiguration syncConfiguration, SyncEventsHandler syncEventsHandler, SyncInformation syncInformation) {
        this.syncConfiguration = syncConfiguration;
        this.syncEventsHandler = syncEventsHandler;
        this.syncInformation = syncInformation;

        this.connectionPointFinder = new ConnectionPointFinder();
        this.pendingHeaders = new ArrayDeque<>();
        this.resetTimeElapsed();
    }

    private void resetTimeElapsed() {
        timeElapsed = Duration.ZERO;
    }

    @Nonnull
    @Override
    public SyncStatesIds getId() {
        return SyncStatesIds.SYNC_WITH_PEER;
    }

    @Override
    public void tick(Duration duration) {
        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingRequest()) >= 0) {
            syncEventsHandler.stopSyncing();
        }
    }

    @Override
    public void newBlockHeaders(BlockHeadersResponseMessage message) {
        List<BlockHeader> chunk = message.getBlockHeaders();

        SkeletonDownloadHelper skeletonDownloadHelper = syncInformation.getSkeletonDownloadHelper();
        Optional<ChunkDescriptor> currentChunk = skeletonDownloadHelper.getCurrentChunk();
        if (!currentChunk.isPresent()
                || chunk.size() != currentChunk.get().getCount()
                || !ByteUtil.fastEquals(chunk.get(0).getHash(), currentChunk.get().getHash())) {
            // TODO(mc) do peer scoring and banning
//            logger.trace("Invalid block headers response with ID {} from peer {}", message.getId(), peer.getPeerNodeID());
            syncEventsHandler.stopSyncing();
            return;
        }

        for (int k = 1; k < chunk.size(); ++k) {
            BlockHeader parentHeader = chunk.get(chunk.size() - k);
            BlockHeader header = chunk.get(chunk.size() - k - 1);

            if (!syncInformation.blockHeaderIsValid(header, parentHeader)) {
                // TODO(mc) do peer scoring and banning
//                logger.trace("Couldn't validate block header {} hash {} from peer {}", header.getNumber(), HashUtil.shortHash(header.getHash()), peer.getPeerNodeID());
                syncEventsHandler.stopSyncing();
                return;
            }
        }

        for (int k = 0; k < chunk.size(); ++k) {
            BlockHeader header = chunk.get(chunk.size() - 1 - k);
            pendingHeaders.add(header);
        }

        if (skeletonDownloadHelper.hasNextChunk()) {
            syncEventsHandler.sendNextBlockHeadersRequest();
            return;
        }

//        logger.trace("Finished verifying headers from peer {}", peer.getPeerNodeID());
        syncEventsHandler.sendNextBodyRequest(pendingHeaders.remove());
    }

    @Override
    public void newBody(BodyResponseMessage message) {
        if (!syncInformation.isExpectedBody(message.getId())) {
            // Invalid body response
            // TODO(mc) do peer scoring, banning and logging
            syncEventsHandler.stopSyncing();
            return;
        }

        // TODO(mc) validate transactions and uncles are part of this block (with header)
        syncInformation.saveBlock(message);

        if (!pendingHeaders.isEmpty()) {
            this.resetTimeElapsed();
            syncEventsHandler.sendNextBodyRequest(this.pendingHeaders.remove());
            return;
        }

        // Finished syncing
        syncEventsHandler.stopSyncing();
    }

    @Override
    public void newConnectionPointData(byte[] hash) {
        this.resetTimeElapsed();

        if (this.syncInformation.isKnownBlock(hash)) {
            connectionPointFinder.updateFound();
        } else {
            connectionPointFinder.updateNotFound();
        }

        Optional<Long> cp = connectionPointFinder.getConnectionPoint();
        if (!cp.isPresent()) {
            syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
            return;
        }

        // connection point found, request skeleton
        syncEventsHandler.sendSkeletonRequest(cp.get());
    }

    @Override
    public void newSkeleton(List<BlockIdentifier> skeleton) {
        // defensive programming: this should never happen
        if (!connectionPointFinder.getConnectionPoint().isPresent()
                || skeleton.size() < 2) {
            syncEventsHandler.stopSyncing();
            return;
        }

        syncEventsHandler.startRequestingHeaders(skeleton, connectionPointFinder.getConnectionPoint().get());
    }

    @Override
    public void onEnter() {
        Status status = syncInformation.getSelectedPeerStatus();
        connectionPointFinder.startFindConnectionPoint(status.getBestBlockNumber());
        syncEventsHandler.sendBlockHashRequest(connectionPointFinder.getFindingHeight());
    }

    @Override
    public void messageSent() {
        this.resetTimeElapsed();
    }

    @Override
    public boolean isSyncing() {
        return true;
    }

    @VisibleForTesting
    public void setConnectionPoint(long height) {
        connectionPointFinder.setConnectionPoint(height);
    }
}
