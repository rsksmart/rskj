package org.ethereum.db;

import co.rsk.core.bc.EventInfoItem;
import co.rsk.core.bc.Events;
import org.ethereum.vm.LogInfo;

import java.util.List;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public interface EventsStore {
    void save(byte[] blockHash, List<EventInfoItem> events);
    List<EventInfoItem> get(byte[] blockHash);
}
