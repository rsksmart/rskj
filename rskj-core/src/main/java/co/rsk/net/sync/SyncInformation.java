package co.rsk.net.sync;

import co.rsk.net.MessageChannel;
import co.rsk.net.Status;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nonnull;

public interface SyncInformation {
    boolean isKnownBlock(byte[] hash);

    boolean hasLowerDifficulty(MessageChannel peer);

    Status getSelectedPeerStatus();

    boolean isExpectedBody(long expected);

    // TODO(mc) don't receive a full message
    void saveBlock(BodyResponseMessage message);

    boolean blockHeaderIsValid(@Nonnull BlockHeader header, @Nonnull BlockHeader parentHeader);
}
