package co.rsk.rpc;

import co.rsk.core.bc.EventInfo;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;

import java.util.Arrays;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

/**
 * Created by SerAdmin on 11/23/2017.
 */
public class EventFilterElement {
    public String blockNumber;
    public String blockHash;
    public String transactionHash;
    public String transactionIndex;
    public String address;
    public String data;
    public String[] topics;

    public EventFilterElement(EventInfo logInfo, Block b, int txIndex, Transaction tx) {
        blockNumber = b == null ? null : toJsonHex(b.getNumber());
        blockHash = b == null ? null : toJsonHex(b.getHash());
        transactionIndex = b == null ? null : toJsonHex(txIndex);
        transactionHash = toJsonHex(tx.getHash());
        //address = toJsonHex(logInfo.getAddress());
        data = toJsonHex(logInfo.getData());
        topics = new String[logInfo.getTopics().size()];
        for (int i = 0; i < topics.length; i++) {
            topics[i] = toJsonHex(logInfo.getTopics().get(i).getData());
        }
    }

    @Override
    public String toString() {
        return "EventFilterElement{" +
                " blockNumber='" + blockNumber + '\'' +
                ", blockHash='" + blockHash + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", transactionIndex='" + transactionIndex + '\'' +
                ", address='" + address + '\'' +
                ", data='" + data + '\'' +
                ", topics=" + Arrays.toString(topics) +
                '}';
    }
}
