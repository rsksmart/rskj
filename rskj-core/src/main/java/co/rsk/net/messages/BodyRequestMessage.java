package co.rsk.net.messages;

import co.rsk.core.commons.Keccak256;
import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BodyRequestMessage extends MessageWithId {
    private long id;
    private Keccak256 hash;

    public BodyRequestMessage(long id, Keccak256 hash) {
        this.id = id;
        this.hash = hash;
    }

    public long getId() {
        return this.id;
    }

    public Keccak256 getBlockHash() {
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
