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
public class PerContractLog {
    Map<byte[], ContractLog> map;

    public PerContractLog() {
           map = new HashMap<>();
    }

    public void put(byte[] address,ContractLog clog) {
        map.put(address,clog);
    }

    public ContractLog get(byte[] address) {
        return map.get(address);
    }

    public Map<byte[], ContractLog> getMap() {
        return map;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public byte[] getEncoded() {
        List<byte[]> payloads = new ArrayList<>();

        for (byte[] addr : map.keySet()) {
            byte[] payload = map.get(addr).getEncoded();
            payloads.add(payload);
        }
        return RLP.encodeList(payloads);
    }
}
