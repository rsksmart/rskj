package co.rsk.net.messages;

import co.rsk.crypto.Sha3Hash;
import org.ethereum.util.RLP;

/**
 * Wrapper around an RSK NewBlockHash message.
 */
public class NewBlockHashMessage extends Message {
    private Sha3Hash hash;

    public NewBlockHashMessage(Sha3Hash hash) {
        this.hash = hash;
    }

    public Sha3Hash getBlockHash() {
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
