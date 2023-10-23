package co.rsk.net.messages;

import org.ethereum.util.RLP;

import java.math.BigInteger;

public class SnapStateChunkResponseMessage extends MessageWithId {
    private final long to;
    private long id;
    private byte[] chunkOfTrieKeyValue;

    private long from;

    private boolean complete;
    private long blockNumber;

    public SnapStateChunkResponseMessage(long id, byte[] chunkOfTrieKeyValue, long blockNumber, long from, long to, boolean complete) {
        this.id = id;
        this.chunkOfTrieKeyValue = chunkOfTrieKeyValue;
        this.blockNumber = blockNumber;
        this.from = from;
        this.to = to;
        this.complete = complete;
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
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpFrom = RLP.encodeBigInteger(BigInteger.valueOf(this.from));
        byte[] rlpTo = RLP.encodeBigInteger(BigInteger.valueOf(this.to));
        byte[] rlpComplete = new byte[]{this.complete?(byte)1:(byte)0};
        return RLP.encodeList(chunkOfTrieKeyValue, rlpBlockNumber, rlpFrom, rlpTo, rlpComplete);
    }
    public byte[] getChunkOfTrieKeyValue() {
        return chunkOfTrieKeyValue;
    }

    public long getFrom() {
        return from;
    }

    public boolean isComplete() {
        return complete;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getTo() {
        return to;
    }
}
