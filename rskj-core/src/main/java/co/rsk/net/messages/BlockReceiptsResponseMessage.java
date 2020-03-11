package co.rsk.net.messages;

import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;

import java.util.ArrayList;
import java.util.List;

public class BlockReceiptsResponseMessage extends MessageWithId {

    /**
     * Id to identify request/response correlation
     */
    private long id;

    /**
     * List of receipts from the block
     */
    private List<TransactionReceipt> blockReceipts;



    public BlockReceiptsResponseMessage(long requestId, List<TransactionReceipt> receipts) {
        this.id = requestId;
        this.blockReceipts = new ArrayList<>(receipts);
    }

    @Override
    public long getId() {
        return id;
    }

    public List<TransactionReceipt> getBlockReceipts() { return new ArrayList<>(blockReceipts); }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[][] rlpReceipts = new byte[this.blockReceipts.size()][];

        for (int k = 0; k < this.blockReceipts.size(); k++) {
            rlpReceipts[k] = this.blockReceipts.get(k).getEncoded();
        }

        return RLP.encodeList(rlpReceipts);
    }

    @Override
    public MessageType getMessageType() { return MessageType.BLOCK_RECEIPTS_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) {
//        v.apply(this);
    }
}
