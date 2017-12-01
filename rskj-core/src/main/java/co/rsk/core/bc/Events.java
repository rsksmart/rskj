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
        rlpEncoded =encodeEventList(list);
        return rlpEncoded;
    }

    static public byte[] encodeEventList(List<EventInfoItem> list) {
        List<byte[]> payloads = new ArrayList<>();

        for (EventInfoItem item : list) {
            byte[] payload = item.getEncoded();
            payloads.add(payload);
        }
        byte[] rlpEncoded =RLP.encodeList(payloads);
        return rlpEncoded;
    }

    public void decode(byte[] rlp) {
        rlpEncoded =rlp;
        decodeEventList(list,rlp);
    }

    static public void decodeEventList(List<EventInfoItem> list,byte[] rlp) {
        //RLPList rlplist =(RLPList) RLP.decode2(rlp);
        //ArrayList<RLPElement> rlplist = RLP.decode2(rlp).get(0);
        RLPList rlplist =(RLPList)  RLP.decode2(rlp).get(0);

        for (RLPElement payload : rlplist) {
            byte[] item = payload.getRLPData();
            list.add(new EventInfoItem(item));
        }

    }
}
