package co.rsk.net.messages;

import org.ethereum.util.RLP;

import java.math.BigInteger;

public class SnapStatusRequestMessage extends Message {
    private long blockNumber;

    public SnapStatusRequestMessage(long blockNumber) {
        this.blockNumber = blockNumber;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_REQUEST_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] encodedBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(blockNumber));
        return RLP.encodeList(encodedBlockNumber);
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}