package co.rsk.net.messages;

import org.ethereum.core.Block;
import org.ethereum.core.BlockBody;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;

import java.util.List;

/**
 * Created by ajlopez on 25/08/2017.
 */
public class BodyResponseMessage extends MessageWithId {
    private final List<BlockBody> blocks;
    private long id;

    public BodyResponseMessage(long id, List<BlockBody> blocks) {
        this.id = id;
        this.blocks = blocks;
    }

    @Override
    public long getId() { return this.id; }

    public List<BlockBody> getBlocks() { return this.blocks; }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[][] rlpBlocks = new byte[blocks.size()][];
        for (int i = 0 ; i < blocks.size() ; i++) {
            BlockBody block = blocks.get(i);
            if (block == null) {
                rlpBlocks[i] = null;
            } else {
                byte[][] rlpTransactions = new byte[block.getTransactionsList().size()][];
                byte[][] rlpUncles = new byte[block.getUncleList().size()][];

                for (int k = 0; k < block.getTransactionsList().size(); k++) {
                    rlpTransactions[k] = block.getTransactionsList().get(k).getEncoded();
                }

                for (int k = 0; k < block.getUncleList().size(); k++) {
                    rlpUncles[k] = block.getUncleList().get(k).getFullEncoded();
                }
                rlpBlocks[i] = RLP.encodeList(RLP.encodeList(rlpTransactions), RLP.encodeList(rlpUncles));
            }
        }
        return RLP.encodeList(rlpBlocks);
    }

    @Override
    public MessageType getMessageType() { return MessageType.BODY_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
