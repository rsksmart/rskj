package co.rsk.net.sync;

import co.rsk.net.BlockProcessResult;
import co.rsk.net.NodeID;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    boolean hasLowerDifficulty(NodeID nodeID);

    BlockProcessResult processBlock(Block block);

    boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader);

    NodeID getSelectedPeerId();

    boolean hasGoodReputation(NodeID nodeID);
}
