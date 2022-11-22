package co.rsk.net.messages;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.util.RLP;

/**
 * Wrapper around an RSK NewBlockHash message.
 */
public class NewBlockHashMessage extends MessageVersionAware {
    private int version;
    private byte[] hash;

    public NewBlockHashMessage(int version, byte[] hash) {
        this.version = version;
        this.hash = hash;
    }

    @VisibleForTesting
    public NewBlockHashMessage(byte[] hash) {
        this(0, hash);
    }

    public byte[] getBlockHash() {
        return this.hash;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.NEW_BLOCK_HASH_MESSAGE;
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public byte[] encodeWithoutVersion() {
        byte[] elementHash = RLP.encodeElement(this.hash);
        return RLP.encodeList(elementHash);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
