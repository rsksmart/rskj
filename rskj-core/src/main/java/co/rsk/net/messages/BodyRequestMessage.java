package co.rsk.net.messages;

import co.rsk.crypto.Sha3Hash;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BodyRequestMessage extends MessageWithId {
    private long id;
    private Sha3Hash hash;

    public BodyRequestMessage(long id, Sha3Hash hash) {
        this.id = id;
        this.hash = hash;
    }

    public long getId() {
        return this.id;
    }

    public Sha3Hash getBlockHash() {
        return this.hash;
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
        byte[] rlpHash = RLP.encodeElement(this.hash.getBytes());
        return RLP.encodeList(rlpHash);
    }
}
