package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

import java.util.List;

/**
 * Created by ajlopez on 25/08/2017.
 */
public class BodyMessage extends Message {
    private long id;
    private List<Transaction> transactions;
    private List<BlockHeader> uncles;

    public BodyMessage(long id, List<Transaction> transactions, List<BlockHeader> uncles) {
        this.id = id;
        this.transactions = transactions;
        this.uncles = uncles;
    }

    public long getId() { return this.id; }

    public List<Transaction> getTransactions() { return this.transactions; }

    public List<BlockHeader> getUncles() { return this.uncles; }

    @Override
    public byte[] getEncodedMessage() { return null; }

    @Override
    public MessageType getMessageType() { return MessageType.BODY_MESSAGE; }
}
