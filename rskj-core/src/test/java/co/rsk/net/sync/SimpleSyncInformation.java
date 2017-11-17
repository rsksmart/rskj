package co.rsk.net.sync;


import co.rsk.net.MessageChannel;
import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;

public class SimpleSyncInformation implements SyncInformation {
    private boolean hasLowerDifficulty = true;

    @Override
    public boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader) {
        return false;
    }

    @Override
    public NodeID getSelectedPeerId() {
        return null;
    }

    @Override
    public boolean hasLowerDifficulty(MessageChannel peer) {
        return hasLowerDifficulty;
    }

    @Override
    public void processBlock(Block block) {

    }

    @Override
    public boolean isKnownBlock(byte[] hash) {
        return false;
    }

    public SimpleSyncInformation withWorsePeers() {
        this.hasLowerDifficulty = false;
        return this;
    }
}