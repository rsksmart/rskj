package co.rsk.net.sync;

import co.rsk.net.BlockProcessResult;
import co.rsk.net.NodeID;
import co.rsk.scoring.EventType;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    boolean hasLowerDifficulty(NodeID nodeID);

    BlockProcessResult processBlock(Block block);

    boolean blockHeaderIsValid(@Nonnull BlockHeader header);

    boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader);

    NodeID getSelectedPeerId();

    boolean hasGoodReputation(NodeID nodeID);

    void reportEvent(String message, EventType eventType, NodeID peerId, Object... arguments);

    int getScore(NodeID key);
}
