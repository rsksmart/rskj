package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public interface UnionBridgeStorageProvider {
    void setAddress(RskAddress address);
    Optional<RskAddress> getAddress(ActivationConfig.ForBlock activations);
    void save(ActivationConfig.ForBlock activations);
}
