package co.rsk.net.messages;

import com.google.common.collect.Lists;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;

import java.util.List;

/**
 * Created by ajlopez on 25/08/2017.
 */
public class BodyResponseMessage extends MessageWithId {
    private final long id;
    private final List<Transaction> transactions;
    private final List<BlockHeader> uncles;
    private final BlockHeaderExtension blockHeaderExtension;

    public BodyResponseMessage(long id, List<Transaction> transactions, List<BlockHeader> uncles, BlockHeaderExtension blockHeaderExtension) {
        this.id = id;
        this.transactions = transactions;
        this.uncles = uncles;
        this.blockHeaderExtension = blockHeaderExtension;
    }

    @Override
    public long getId() { return this.id; }

    public List<Transaction> getTransactions() { return this.transactions; }

    public List<BlockHeader> getUncles() { return this.uncles; }
    public BlockHeaderExtension getBlockHeaderExtension() { return this.blockHeaderExtension; }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[][] rlpTransactions = new byte[this.transactions.size()][];
        byte[][] rlpUncles = new byte[this.uncles.size()][];

        for (int k = 0; k < this.transactions.size(); k++) {
            rlpTransactions[k] = this.transactions.get(k).getEncoded();
        }

        for (int k = 0; k < this.uncles.size(); k++) {
            rlpUncles[k] = this.uncles.get(k).getFullEncoded();
        }

        List<byte[]> elements = Lists.newArrayList(RLP.encodeList(rlpTransactions), RLP.encodeList(rlpUncles));

        if (this.blockHeaderExtension != null) {
            elements.add(BlockHeaderExtension.toEncoded(blockHeaderExtension));
        }

        return RLP.encodeList(elements.toArray(new byte[][]{}));
    }

    @Override
    public MessageType getMessageType() { return MessageType.BODY_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
