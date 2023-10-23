package co.rsk.net.messages;

import org.ethereum.util.RLP;

import java.math.BigInteger;

public class SnapStateChunkRequestMessage extends MessageWithId {

    private final long id;
    private final long from;
    private final long chunkSize;
    private final long blockNumber;

    public SnapStateChunkRequestMessage(long id, long blockNumber, long from, long chunkSize) {
        this.id = id;
        this.from = from;
        this.chunkSize = chunkSize;
        this.blockNumber = blockNumber;
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
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpFrom = RLP.encodeBigInteger(BigInteger.valueOf(this.from));
        byte[] rlpChunkSize = RLP.encodeBigInteger(BigInteger.valueOf(this.chunkSize));
        return RLP.encodeList(rlpBlockNumber, rlpFrom, rlpChunkSize);
    }

    public long getFrom() {
        return from;
    }

    public long getChunkSize() {
        return chunkSize;
    }
    public long getBlockNumber() {
        return blockNumber;
    }
}
