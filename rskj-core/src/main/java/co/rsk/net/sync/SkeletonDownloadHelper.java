package co.rsk.net.sync;

import co.rsk.net.NodeID;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;

public class SkeletonDownloadHelper {

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> skeleton = null;
    private int lastRequestedLinkIndex;
    private NodeID selectedPeerId;

    public SkeletonDownloadHelper(@Nonnull NodeID selectedPeerId) {
        this.selectedPeerId = selectedPeerId;
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

    public void setSkeleton(@Nonnull List<BlockIdentifier> skeleton) {
        this.skeleton = skeleton;
        this.lastRequestedLinkIndex = 0;
    }

    public int getLastRequestedLinkIndex() { return this.lastRequestedLinkIndex; }

    public void setLastRequestedLinkIndex(int index) { this.lastRequestedLinkIndex = index; }

    @Nonnull
    public NodeID getSelectedPeerId() {
        return selectedPeerId;
    }
}
