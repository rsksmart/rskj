package co.rsk.net.messages;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

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

    public static Message create(BlockFactory blockFactory, RLPList list) {
        try {
            byte[] rlpId = list.get(0).getRLPData();
            RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpBlockNumber = message.get(0).getRLPData();
            byte[] rlpFrom = message.get(1).getRLPData();
            byte[] rlpChunkSize = message.get(2).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();
            long from = rlpFrom == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpFrom).longValue();
            long chunkSize = rlpChunkSize == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpChunkSize).longValue();
            return new SnapStateChunkRequestMessage(id, blockNumber, from, chunkSize);
        } catch (Exception e) {
            throw e;
        }
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
