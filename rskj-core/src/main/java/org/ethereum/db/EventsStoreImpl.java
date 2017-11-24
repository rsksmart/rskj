package org.ethereum.db;

import co.rsk.core.bc.EventInfoItem;
import co.rsk.core.bc.Events;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public class EventsStoreImpl implements EventsStore {

    // This store is very low performant. It requires retrieving all logs from a block
    // just to find the log of a single account.
    private KeyValueDataSource DS;

    public EventsStoreImpl(KeyValueDataSource aDS){
        this.DS = aDS;
    }

    public List<EventInfoItem> get(byte[] blockHash) {
        byte[] encoded =DS.get(blockHash);
        if (encoded==null)
            return null;
        List<EventInfoItem> events = new ArrayList<>();
        Events.decodeEventList(events,encoded);
        return events;
    }

    public void save(byte[] blockHash,List<EventInfoItem> events) {
            DS.put(blockHash, Events.encodeEventList(events));
    }
}
