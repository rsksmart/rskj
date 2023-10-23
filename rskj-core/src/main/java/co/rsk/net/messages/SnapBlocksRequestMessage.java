package co.rsk.net.messages;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public class SnapBlocksRequestMessage extends Message {
    private long blockNumber;

    public SnapBlocksRequestMessage(long blockNumber) {
        this.blockNumber = blockNumber;
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_BLOCKS_REQUEST_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] encodedBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(blockNumber));
        return RLP.encodeList(encodedBlockNumber);
    }

    public static Message decodeMessage(BlockFactory blockFactory, RLPList list) {
        byte[] rlpBlockNumber = list.get(0).getRLPData();

        long blockNumber = rlpBlockNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBlockNumber).longValue();

        return new SnapBlocksRequestMessage(blockNumber);
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}