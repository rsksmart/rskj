package co.rsk.net.messages;

import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BodyRequestMessage extends MessageWithId {
    private long id;
    private byte[][] hashes;

    public BodyRequestMessage(long id, byte[]... hashes) {
        this.id = id;
        this.hashes = hashes;
    }

    public long getId() {
        return this.id;
    }

    public byte[][] getBlockHashes() {
        return this.hashes;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BODY_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.BODY_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[][] encoded = new byte[hashes.length][];
        for (int i = 0 ; i < this.hashes.length ; i++) {
            byte[] hash = hashes[i];
            encoded[i] = RLP.encodeElement(hash);
        }
        return RLP.encodeList(encoded);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
