package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public interface UnionBridgeStorageProvider {
    void setAddress(RskAddress address);
    Optional<RskAddress> getAddress();
    void setLockingCap(Coin lockingCap);
    Optional<Coin> getLockingCap();
    Optional<co.rsk.core.Coin> getWeisTransferredToUnionBridge(ActivationConfig.ForBlock activations);
    void setWeisTransferredToUnionBridge(co.rsk.core.Coin weisTransferred);
    void save(ActivationConfig.ForBlock activations);
}
