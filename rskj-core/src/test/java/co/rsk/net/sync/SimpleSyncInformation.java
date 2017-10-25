package co.rsk.net.sync;


import co.rsk.net.BlockProcessResult;
import co.rsk.net.NodeID;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;

public class SimpleSyncInformation implements SyncInformation {
    private boolean hasLowerDifficulty = true;
    private boolean hasGoodReputation = true;

    @Override
    public boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader) {
        return false;
    }

    @Override
    public NodeID getSelectedPeerId() {
        return null;
    }

    @Override
    public boolean hasGoodReputation(NodeID nodeID) {
        return hasGoodReputation;
    }

    @Override
    public boolean hasLowerDifficulty(NodeID nodeID) {
        return hasLowerDifficulty;
    }

    @Override
    public BlockProcessResult processBlock(Block block) {
        return new BlockProcessResult(false, null);
    }

    @Override
    public boolean isKnownBlock(byte[] hash) {
        return false;
    }

    public SimpleSyncInformation withWorsePeers() {
        this.hasLowerDifficulty = false;
        return this;
    }

    public SimpleSyncInformation withBadReputation() {
        this.hasGoodReputation = false;
        return this;
    }
}