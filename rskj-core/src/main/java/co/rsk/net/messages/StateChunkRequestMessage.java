package co.rsk.net.messages;

import org.ethereum.util.RLP;

class StateChunkRequestMessage extends MessageWithId {

    private final long id;
    private byte[] hash;

    public StateChunkRequestMessage(long id, byte[] hash) {
        this.id = id;
        this.hash = hash.clone();
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
        byte[] rlpHash = RLP.encodeElement(this.hash);
        return RLP.encodeList(rlpHash);
    }

    public byte[] getHash() {
        return this.hash.clone();
    }
}
