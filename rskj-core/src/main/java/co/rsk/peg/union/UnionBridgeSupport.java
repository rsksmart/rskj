package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {
    int setUnionBridgeContractAddressForTestnet(Transaction tx, RskAddress unionBridgeContractAddress);
    void save(ActivationConfig.ForBlock activations);
}
