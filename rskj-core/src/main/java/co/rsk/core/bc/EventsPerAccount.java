package co.rsk.core.bc;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by SerAdmin on 6/30/2017.
 * a List of events that belong to a single contract address in a block.
 */
public class EventsPerAccount {
    // The key is the lognum
    List<EventInfo> list;

    public EventsPerAccount() {
        list = new ArrayList<>();
    }

    public List<EventInfo> getList() {
        return list;
    }

    public int size() {
        return list.size();
    }

    public void add(EventInfo li) {
        list.add(li);
    }

    public EventInfo get(int logNum) {
        return list.get(logNum);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public byte[] getEncoded() {
        List<byte[]> payloads = new ArrayList<>();

        for (int i=0;i<list.size();i++){
            byte[] payload = list.get(i).getEncoded();
            payloads.add(payload);
        }
        return RLP.encodeListFromList(payloads);
    }

    }
