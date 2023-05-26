package co.rsk.net.messages;

public class StateChunkResponseMessage extends MessageWithId {
    private long id;
    private byte[] chunkOfTrieKeyValue;

    public StateChunkResponseMessage(long id, byte[] chunkOfTrieKeyValue) {
        this.id = id;
        this.chunkOfTrieKeyValue = chunkOfTrieKeyValue;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.STATE_CHUNK_RESPONSE_MESSAGE;
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
        return chunkOfTrieKeyValue;
    }

    public byte[] getChunkOfTrieKeyValue() {
        return chunkOfTrieKeyValue;
    }
}
