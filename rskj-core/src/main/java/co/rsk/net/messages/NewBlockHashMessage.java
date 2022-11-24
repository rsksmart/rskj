package co.rsk.net.messages;

import org.ethereum.util.RLP;

/**
 * Wrapper around an RSK NewBlockHash message.
 */
public class NewBlockHashMessage extends Message {
    private byte[] hash;

    public NewBlockHashMessage(byte[] hash) {
        this.hash = hash;
    }

    public byte[] getBlockHash() {
        return this.hash;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.NEW_BLOCK_HASH_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] elementHash = RLP.encodeElement(this.hash);
        return RLP.encodeList(elementHash);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
