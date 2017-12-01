package co.rsk.core.bc;

import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by SerAdmin on 11/18/2017.
 */
public class EventInfo {
    int txIndex;
    List<DataWord> topics = new ArrayList<>();
    byte[] data = new byte[]{};

    /* Eventinfo in encoded form */
    private byte[] rlpEncoded;

    public EventInfo(byte[] rlp) {
        decode(rlp);
    }

    public EventInfo() {
    }

    public void decode(byte[] rlp) {
        RLPList params = (RLPList) RLP.decode2(rlp).get(0);
        decode(params);
        rlpEncoded = rlp;
    }

    public void decode(RLPList params){
        RLPList topics = (RLPList) params.get(0);
        RLPItem data = (RLPItem) params.get(1);
        RLPItem txIndex = (RLPItem) params.get(2);
        this.data = data.getRLPData() != null ? data.getRLPData() : new byte[]{};
        this.txIndex = txIndex.getRLPData() != null ? RLP.decodeInt(txIndex.getRLPData(),0):0;

        for (RLPElement topic1 : topics) {
            byte[] topic = topic1.getRLPData();
            this.topics.add(new DataWord(topic));
        }
    }


    public EventInfo(List<DataWord> topics, byte[] data,int txindex) {
        this.topics = (topics != null) ? topics : new ArrayList<DataWord>();
        this.data = (data != null) ? data : new byte[]{};
        this.txIndex = txindex;
    }

    public List<DataWord> getTopics() {
        return topics;
    }

    public byte[] getData() {
        return data;
    }

    public int getTxIndex() { return txIndex; }

    /*  [address, [topic, topic ...] data] */
    public byte[] getEncoded() {

        byte[] txindexEncoded = RLP.encodeInt(this.txIndex);

        byte[][] topicsEncoded = null;
        if (topics != null) {
            topicsEncoded = new byte[topics.size()][];
            int i = 0;
            for (DataWord topic : topics) {
                byte[] topicData = topic.getData();
                topicsEncoded[i] = RLP.encodeElement(topicData);
                ++i;
            }
        }

        byte[] dataEncoded = RLP.encodeElement(data);
        return RLP.encodeList(RLP.encodeList(topicsEncoded), dataEncoded,txindexEncoded );
    }

    public Bloom getBloom() {
        Bloom ret = new Bloom();
        for (DataWord topic : topics) {
            byte[] topicData = topic.getData();
            ret.or(Bloom.create(HashUtil.sha3(topicData)));
        }
        return ret;
    }

    @Override
    public String toString() {

        StringBuilder topicsStr = new StringBuilder();
        topicsStr.append("[");

        for (DataWord topic : topics) {
            String topicStr = Hex.toHexString(topic.getData());
            topicsStr.append(topicStr).append(" ");
        }
        topicsStr.append("]");


        return "EventInfo{" +
                "topics=" + topicsStr +
                ", data=" + Hex.toHexString(data) +
                ", txindex=" + Integer.toString(txIndex) +
                '}';
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!EventInfo.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final EventInfo other = (EventInfo) obj;

        if (!Arrays.equals(data,other.data)) {
            return false;
        }

        if (txIndex!=other.txIndex) {
            return false;
        }
        if (!topics.equals(other.topics))
            return false;

        return true;
    }
}
