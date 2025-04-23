package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {
    int setUnionBridgeContractAddressForTestnet(Transaction tx, RskAddress unionBridgeContractAddress);
    void save();
}
