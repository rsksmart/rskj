package co.rsk.net.messages;

import co.rsk.crypto.Keccak256;
import org.ethereum.util.RLP;

/**
 * Wrapper around an RSK NewBlockHash message.
 */
public class NewBlockHashMessage extends Message {
    private Keccak256 hash;

    public NewBlockHashMessage(Keccak256 hash) {
        this.hash = hash;
    }

    public Keccak256 getBlockHash() {
        return this.hash;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.NEW_BLOCK_HASH_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] elementHash = RLP.encodeElement(this.hash.getBytes());
        return RLP.encodeList(elementHash);
    }
}
