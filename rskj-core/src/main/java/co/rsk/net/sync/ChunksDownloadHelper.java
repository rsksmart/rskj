package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public class ChunksDownloadHelper {
    private SyncConfiguration syncConfiguration;

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> skeleton;
    private long connectionPoint;
    private int lastRequestedLinkIndex;

    public ChunksDownloadHelper(@Nonnull SyncConfiguration syncConfiguration, List<BlockIdentifier> skeleton, long connectionPoint) {
        this.syncConfiguration = syncConfiguration;
        this.connectionPoint = connectionPoint;
        this.lastRequestedLinkIndex = 0;
        this.skeleton = skeleton;
    }

    public boolean hasNextChunk() {
        int linkIndex = this.lastRequestedLinkIndex + 1;
        return linkIndex < skeleton.size() && linkIndex <= syncConfiguration.getMaxSkeletonChunks();
    }

    public Optional<ChunkDescriptor> getCurrentChunk() {
        return Optional.of(getChunk(this.lastRequestedLinkIndex));
    }

    public ChunkDescriptor getNextChunk() {
        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        return getChunk(this.lastRequestedLinkIndex + 1);
    }

    private ChunkDescriptor getChunk(int linkIndex) {
        byte[] hash = skeleton.get(linkIndex).getHash();
        long height = skeleton.get(linkIndex).getNumber();

        long lastHeight = skeleton.get(linkIndex - 1).getNumber();
        long previousKnownHeight = Math.max(lastHeight, connectionPoint);
        int count = (int)(height - previousKnownHeight);
        this.lastRequestedLinkIndex = linkIndex;

        return new ChunkDescriptor(hash, count);
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return this.skeleton;
    }
}
