package co.rsk.net.messages;


import org.ethereum.core.Block;
import org.ethereum.util.RLP;
import java.math.BigInteger;

public class SnapStatusResponseMessage extends Message {
    private final Block block;
    private final long trieSize;

    public Block getBlock() {
        return this.block;
    }

    public long getTrieSize() {
        return this.trieSize;
    }

    public SnapStatusResponseMessage(Block block, long trieSize) {
        this.block = block;
        this.trieSize = trieSize;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SNAP_STATUS_RESPONSE_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        byte[] rlpBlock = RLP.encode(this.block.getEncoded());
        byte[] rlpTrieSize = RLP.encodeBigInteger(BigInteger.valueOf(this.trieSize));

        return RLP.encodeList(rlpBlock, rlpTrieSize);
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
