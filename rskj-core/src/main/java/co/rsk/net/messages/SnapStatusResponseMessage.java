package co.rsk.net.messages;


import org.ethereum.util.RLP;
import java.math.BigInteger;

public class SnapStatusResponseMessage extends Message {
    private SnapStatus snapStatus;

    public SnapStatusResponseMessage(SnapStatus status) {
        this.snapStatus = status;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] trieSize = RLP.encodeBigInteger(BigInteger.valueOf(snapStatus.getTrieSize()));
        byte[] rootHash = RLP.encodeElement(snapStatus.getRootHash());

        return RLP.encodeList(trieSize, rootHash);
    }

    public SnapStatus getSnapStatus() {
        return this.snapStatus;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
