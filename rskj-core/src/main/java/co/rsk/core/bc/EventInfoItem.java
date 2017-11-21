package co.rsk.core.bc;

import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

/**
 * Created by SerAdmin on 11/20/2017.
 */
public class EventInfoItem {
    public EventInfo eventInfo;
    public byte[] address;

    public EventInfoItem() {
        eventInfo = new EventInfo();
        address = null;
    }

    public Bloom getBloomFilter() {
        // on-the-fly computation
        Bloom bloomFilter = new Bloom();
        bloomFilter.or(eventInfo.getBloom());
        bloomFilter.or(Bloom.create(HashUtil.sha3(address)));
        return bloomFilter;
    }

    public EventInfoItem(byte[] encoded) {
        eventInfo = new EventInfo();
        address = null;
        decode(encoded);
    }

    public EventInfoItem(EventInfo eventInfo, byte[] address) {
        this.eventInfo = eventInfo;
        this.address = address;
    }

    // Encoding for storage in the store
    public byte[] getEncoded() {

        byte[] address = RLP.encodeElement(this.address);
        byte[] ei = eventInfo.getEncoded();

        return RLP.encodeList(address, ei);
    }


    public void decode(byte[] encoded) {
        decode((RLPList) RLP.decode2(encoded).get(0));
    }

    public void decode(RLPList rlp) {
        this.address = rlp.get(0).getRLPData();
        this.eventInfo.decode(rlp.get(1).getRLPData());
    }

}