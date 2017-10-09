package co.rsk.net.sync;

import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public class SkeletonDownloadHelper {
    private SyncConfiguration syncConfiguration;

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> skeleton = null;
    private long connectionPoint;
    private int lastRequestedLinkIndex;

    public SkeletonDownloadHelper(@Nonnull SyncConfiguration syncConfiguration) {
        this.syncConfiguration = syncConfiguration;
    }

    public boolean hasSkeleton() {
        return this.skeleton != null;
    }

    @Nonnull
    public List<BlockIdentifier> getSkeleton() {
        if (!hasSkeleton()) {
            throw new IllegalStateException("The skeleton hasn't been found yet.");
        }

        return this.skeleton;
    }

    public void setSkeleton(@Nonnull List<BlockIdentifier> skeleton, long connectionPoint) {
        this.skeleton = skeleton;
        this.connectionPoint = connectionPoint;
        this.lastRequestedLinkIndex = 0;
    }

    public int getLastRequestedLinkIndex() { return this.lastRequestedLinkIndex; }

    public void setLastRequestedLinkIndex(int index) { this.lastRequestedLinkIndex = index; }

    public boolean hasNextChunk() {
        int linkIndex = getLastRequestedLinkIndex() + 1;
        return hasSkeleton() && linkIndex < skeleton.size() && linkIndex <= syncConfiguration.getMaxSkeletonChunks();
    }

    public Optional<ChunkDescriptor> getCurrentChunk() {
        if (hasSkeleton()) {
            return Optional.of(getChunk(getLastRequestedLinkIndex()));
        }

        return Optional.empty();
    }

    public ChunkDescriptor getNextChunk() {
        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        return getChunk(getLastRequestedLinkIndex() + 1);
    }

    private ChunkDescriptor getChunk(int linkIndex) {
        List<BlockIdentifier> skeleton = getSkeleton();
        byte[] hash = skeleton.get(linkIndex).getHash();
        long height = skeleton.get(linkIndex).getNumber();

        long lastHeight = skeleton.get(linkIndex - 1).getNumber();
        long previousKnownHeight = Math.max(lastHeight, connectionPoint);
        int count = (int)(height - previousKnownHeight);
        setLastRequestedLinkIndex(linkIndex);

        return new ChunkDescriptor(hash, count);
    }
}
