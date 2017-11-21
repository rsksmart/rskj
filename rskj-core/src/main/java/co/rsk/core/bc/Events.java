package co.rsk.core.bc;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public class Events {
    List<EventInfoItem> list;
    byte[] rlpEncoded;

    public Events() {
           list = new ArrayList<>();
    }
/*
    public void put(byte[] address,EventsPerAccount clog) {
        map.put(new ByteArrayWrapper(address),clog);
    }
    public EventsPerAccount get(byte[] address) {
        return map.get(new ByteArrayWrapper(address));
    }
public Map<ByteArrayWrapper, EventsPerAccount> getMap() {
        return map;
    }

*/
    public List<EventInfoItem> getList() {
        return list;
    }

    public boolean addAll(List<EventInfoItem> listb) {
        return list.addAll(listb);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public byte[] getEncoded() {
        List<byte[]> payloads = new ArrayList<>();

        for (EventInfoItem item : list) {
            byte[] payload = item.getEncoded();
            payloads.add(payload);
        }
        rlpEncoded =RLP.encodeList(payloads);
        return rlpEncoded;
    }

    public void decode(byte[] rlp) {
        RLPList rlplist =(RLPList) RLP.decode2(rlp);

        for (RLPElement payload : rlplist) {
            byte[] item = payload.getRLPData();
            list.add(new EventInfoItem(item));
        }

        rlpEncoded = rlp;
    }
}
