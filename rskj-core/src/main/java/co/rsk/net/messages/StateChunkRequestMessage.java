package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class StateChunkRequestMessage extends MessageWithId {

    private final long id;

    public StateChunkRequestMessage(long id) {
        this.id = id;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.STATE_CHUNK_REQUEST_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        return RLP.encodeList(RLP.encodeElement(null));
    }
}
