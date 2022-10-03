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
    private long id;
    private List<Transaction> transactions;
    private List<BlockHeader> uncles;
    private BlockHeaderExtension headerExtension;

    public BodyResponseMessage(long id, List<Transaction> transactions, List<BlockHeader> uncles, BlockHeaderExtension headerExtension) {
        this.id = id;
        this.transactions = transactions;
        this.uncles = uncles;
        this.headerExtension = headerExtension;
    }

    @Override
    public long getId() { return this.id; }

    public List<Transaction> getTransactions() { return this.transactions; }

    public List<BlockHeader> getUncles() { return this.uncles; }

    public BlockHeaderExtension getHeaderExtension() { return this.headerExtension; }

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

        List<byte[]> elementsToEncode = Lists.newArrayList(RLP.encodeList(rlpTransactions), RLP.encodeList(rlpUncles));
        if (this.headerExtension != null) elementsToEncode.add(this.headerExtension.getEncoded());

        return RLP.encodeList(elementsToEncode.toArray(new byte[][]{}));
    }

    @Override
    public MessageType getMessageType() { return MessageType.BODY_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
