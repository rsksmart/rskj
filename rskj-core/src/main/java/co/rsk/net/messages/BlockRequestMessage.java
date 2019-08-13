package co.rsk.net.messages;

import org.ethereum.util.RLP;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BlockRequestMessage extends MessageWithId {
    private long id;
    private byte[] hash;

    public BlockRequestMessage(long id, byte[] hash) {
        this.id = id;
        this.hash = hash;
    }

    public long getId() { return this.id; }

    public byte[] getBlockHash() {
        return this.hash;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_REQUEST_MESSAGE;
    }

    @Override
    public MessageType getResponseMessageType() {
        return MessageType.BLOCK_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessageWithoutId() {
        byte[] rlpHash = RLP.encodeElement(this.hash);
        return RLP.encodeList(rlpHash);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
