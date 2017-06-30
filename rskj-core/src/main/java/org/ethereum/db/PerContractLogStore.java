package org.ethereum.db;

import co.rsk.core.bc.PerContractLog;
import org.ethereum.vm.LogInfo;

/**
 * Created by SerAdmin on 6/30/2017.
 */
public interface PerContractLogStore {
    //void add(byte[] blockHash, int transactionIndex, byte[] contractAddress,LogInfo logi);
    void save(byte[] blockHash,PerContractLog pcl);
}
