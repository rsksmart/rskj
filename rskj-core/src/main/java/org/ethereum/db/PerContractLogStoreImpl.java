package org.ethereum.db;

import co.rsk.core.bc.ContractLog;
import co.rsk.core.bc.PerContractLog;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.util.RLP;

import java.util.Map;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public class PerContractLogStoreImpl implements PerContractLogStore {

    // This store is very low performant. It requires retrieving all logs from a block
    // just to find the log of a single account.
    private KeyValueDataSource DS;

    public PerContractLogStoreImpl(KeyValueDataSource aDS){
        this.DS = aDS;
    }

    public void save(byte[] blockHash,PerContractLog pcl) {
            DS.put(blockHash, pcl.getEncoded());
    }
}
