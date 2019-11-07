package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by ajlopez on 25/08/2017.
 */
public class BodyResponseMessage extends MessageWithId {
    private long id;
    private List<Transaction> transactions;
    private List<BlockHeader> uncles;

    public BodyResponseMessage(long id, List<Transaction> transactions, List<BlockHeader> uncles) {
        this.id = id;
        this.transactions = transactions;
        this.uncles = uncles;
    }

    @Override
    public long getId() { return this.id; }

    public List<Transaction> getTransactions() { return this.transactions; }

    public List<BlockHeader> getUncles() { return this.uncles; }

    @Override
    protected byte[] getEncodedMessageWithoutId() {
        byte[][] rlpTransactions = new byte[this.transactions.size()][];
        byte[][] rlpUncles = new byte[this.uncles.size()][];
        byte[][] rlpSigs = null;
		
        for (int k = 0; k < this.transactions.size(); k++) {
            rlpTransactions[k] = this.transactions.get(k).getEncodedForBlock();
        }

        for (int k = 0; k < this.uncles.size(); k++) {
            rlpUncles[k] = this.uncles.get(k).getFullEncoded();
		}
		
		List<byte[]> encodeSigs = new ArrayList<byte[]>();
        for (int j = 0; j < transactions.size(); j++) {
            Transaction tx = transactions.get(j);
            if (tx.getVersion() == 1){
                byte[] txIdx = RLP.encodeInt(j);
                byte[] rsv = tx.getEncodedRSV();
                encodeSigs.add(RLP.encodeList(txIdx, rsv));
            }
        }
        if (encodeSigs.size()>0){
            rlpSigs = encodeSigs.toArray(new byte[encodeSigs.size()][]);
		}

		if (rlpSigs == null){
		    return RLP.encodeList(RLP.encodeList(rlpTransactions), RLP.encodeList(rlpUncles));
		}else{
			return RLP.encodeList(RLP.encodeList(rlpTransactions), RLP.encodeList(rlpUncles), RLP.encodeList(rlpSigs));
		}
    }

    @Override
    public MessageType getMessageType() { return MessageType.BODY_RESPONSE_MESSAGE; }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }
}
