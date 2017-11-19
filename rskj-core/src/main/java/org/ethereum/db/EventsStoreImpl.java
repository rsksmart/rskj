package org.ethereum.db;

import co.rsk.core.bc.Events;
import org.ethereum.datasource.KeyValueDataSource;

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

    public void save(byte[] blockHash,Events pcl) {
            DS.put(blockHash, pcl.getEncoded());
    }
}
