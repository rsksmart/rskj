package co.rsk.net.messages;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

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
        try {
            byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
            byte[] rlpFrom = RLP.encodeBigInteger(BigInteger.valueOf(this.from));
            byte[] rlpTo = RLP.encodeBigInteger(BigInteger.valueOf(this.to));
            byte[] rlpComplete = new byte[]{this.complete ? (byte) 1 : (byte) 0};
            return RLP.encodeList(chunkOfTrieKeyValue, rlpBlockNumber, rlpFrom, rlpTo, rlpComplete);
        } catch (Exception e) {
            throw e;
        }
    }

    public static Message create(BlockFactory blockFactory, RLPList list) {
        try {
            byte[] rlpId = list.get(0).getRLPData();
            RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] chunkOfTrieKeys = message.get(0).getRLPData();
            byte[] rlpBlockNumber = message.get(1).getRLPData();
            byte[] rlpFrom = message.get(2).getRLPData();
            byte[] rlpTo = message.get(3).getRLPData();
            byte[] rlpComplete = message.get(4).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();
            long from = rlpFrom == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpFrom).longValue();
            long to = rlpTo == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpTo).longValue();
            boolean complete = rlpComplete == null ? Boolean.FALSE : rlpComplete[0] != 0;
            return new SnapStateChunkResponseMessage(id, chunkOfTrieKeys, blockNumber, from, to, complete);
        } catch (Exception e) {
            throw e;
        }
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
