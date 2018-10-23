package co.rsk.db;

import co.rsk.core.RskAddress;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

import java.util.*;

/**
 * Created by SerAdmin on 9/25/2018.
 * This class is ONLY for unit testing. It should NOT be active in production because
 * it grows in size without bounds.
 */
public class GlobalKeyMap {

    static public boolean enabled = false; // enable only for tests

    static protected Map<ByteArrayWrapper,RskAddress> globalAddressMap;

    static protected Map<ByteArrayWrapper,DataWord> globalStorageKeyMap;

    static public synchronized void clear() {
        globalAddressMap =null;
        globalStorageKeyMap =null;
    }

    static public void addAddress(ByteArrayWrapper key,RskAddress addr) {
        if (!enabled) return;

        getGlobalAddressMap().put(key,addr);
    }

    static public void addStorageKey(ByteArrayWrapper key,DataWord mkey) {
        if (!enabled) return;
        getGlobalStorageKeyMap().put(key,mkey);
    }

    static public void addAddress(byte[] key,RskAddress addr) {
        if (!enabled) return;
        getGlobalAddressMap().put(new ByteArrayWrapper(key),addr);
    }

    static public void addStorageKey(byte[]  key,DataWord mkey) {
        if (!enabled) return;
        getGlobalStorageKeyMap().put(new ByteArrayWrapper(key),mkey);
    }

    static public synchronized Map<ByteArrayWrapper,DataWord> getGlobalStorageKeyMap() {
        if (!enabled) return null;

        if (globalStorageKeyMap==null) {
            globalStorageKeyMap =
                    Collections.synchronizedMap(new HashMap<ByteArrayWrapper, DataWord>());
        }
        return globalStorageKeyMap;
    }

    static public synchronized Map<ByteArrayWrapper,RskAddress> getGlobalAddressMap() {
        if (!enabled) return null;
        if  (globalAddressMap==null) {
            globalAddressMap = Collections.synchronizedMap(new HashMap<ByteArrayWrapper,RskAddress>());
        }
        return globalAddressMap;
    }


}

