package co.rsk.core.bc;

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
    Map<byte[], EventInfo> map;

    public EventsPerAccount() {
        map = new HashMap<>();
    }

    // The key is the transaction index and log number
    public Map<byte[], EventInfo> getMap() {
        return map;
    }

    // Important: The key of the trie is the txIndex plus logNum
    // to be able to paralellize transaction processing and make
    // the EventTree independant on the order of processing.
    public byte[] encodeKey(int txIndex, int logNum) {
        byte[] _txIndex = RLP.encodeInt(txIndex);
        byte[] _logNum = RLP.encodeInt(logNum);
        return RLP.encodeList(_txIndex,_logNum);
    }

    public void put(int txIndex,int logNum,EventInfo li) {
        byte[] key = encodeKey(txIndex,logNum);
        map.put(key,li);
    }

    public EventInfo get(int txIndex,int logNum) {
        byte[] key = encodeKey(txIndex,logNum);
        return map.get(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public byte[] getEncoded() {
        List<byte[]> payloads = new ArrayList<>();

        for (byte[] key : map.keySet()){
            byte[] payload = map.get(key).getEncoded();
            payloads.add(payload);
        }
        return RLP.encodeList(payloads);
    }

    }
