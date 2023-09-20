package co.rsk.net.messages;


import org.ethereum.util.RLP;
import java.math.BigInteger;

public class SnapStatusResponseMessage extends Message {
    private final long trieSize;
    private final byte[] rootHash;


    public long getTrieSize() {
        return this.trieSize;
    }

    public byte[] getRootHash() {
        return this.rootHash;
    }
    public SnapStatusResponseMessage(long trieSize, byte[] rootHash) {
        this.trieSize = trieSize;
        this.rootHash = rootHash;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] trieSize = RLP.encodeBigInteger(BigInteger.valueOf(this.trieSize));
        byte[] rootHash = RLP.encodeElement(this.rootHash);

        return RLP.encodeList(trieSize, rootHash);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
