package co.rsk.net.messages;

import org.ethereum.util.RLP;

public class BlockReceiptsRequestMessage extends MessageWithId {

    /**
     * Id to identify request/response correlation
     */
    private long id;

    /**
     * Hash of the block
     */
    private byte[] hash;

    public BlockReceiptsRequestMessage(long id, byte[] hash) {
        this.id = id;
        this.hash = hash.clone();
    }

    @Override
    public long getId() {
        return id;
    }

    public byte[] getBlockHash() {
        return this.hash.clone();
    }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[] rlpHash = RLP.encodeElement(this.hash);
        return RLP.encodeList(rlpHash);
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_RECEIPTS_REQUEST_MESSAGE;
    }

    @Override
    public void accept(MessageVisitor v) { v.apply(this); }

}
