package co.rsk.core.bc;

import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public class ContractLog {
    Map<byte[], LogInfo> map;

    public ContractLog() {
        map = new HashMap<>();
    }

    public Map<byte[], LogInfo> getMap() {
        return map;
    }

    public byte[] encodeKey(int txIndex, int logNum) {
        byte[] _txIndex = RLP.encodeInt(txIndex);
        byte[] _logNum = RLP.encodeInt(logNum);
        return RLP.encodeList(_txIndex,_logNum);
    }

    public void put(int txIndex,int logNum,LogInfo li) {
        byte[] key = encodeKey(txIndex,logNum);
        map.put(key,li);
    }

    public LogInfo get(int txIndex,int logNum) {
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
