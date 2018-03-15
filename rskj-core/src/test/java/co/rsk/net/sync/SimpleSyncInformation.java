package co.rsk.net.sync;


import co.rsk.net.BlockProcessResult;
import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;

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
    public void reportEvent(String message, EventType eventType, NodeID peerId, Object... arguments) {

    }

    @Override
    public int getScore(NodeID key) {
        return 1;
    }

    @Override
    public Instant getFailInstant(NodeID peerId) {
        return Instant.EPOCH;
    }

    @Override
    public boolean hasLowerDifficulty(NodeID nodeID) {
        return hasLowerDifficulty;
    }

    @Override
    public BlockProcessResult processBlock(Block block, MessageChannel channel) {
        return new BlockProcessResult(false, null, block.getShortHash(), Duration.ZERO);
    }

    @Override
    public boolean blockHeaderIsValid(@Nonnull BlockHeader header) {
        return false;
    }

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