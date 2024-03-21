package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class SnapStatusRequestMessage extends Message {

    public SnapStatusRequestMessage() {
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_REQUEST_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        return RLP.encodedEmptyList();
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}