package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import java.util.Optional;

public interface UnionBridgeStorageProvider {
    void setAddress(RskAddress address);
    Optional<RskAddress> getAddress();
    void setLockingCap(Coin lockingCap);
    Optional<Coin> getLockingCap();
    Optional<co.rsk.core.Coin> getWeisTransferredToUnionBridge();
    void setWeisTransferredToUnionBridge(co.rsk.core.Coin weisTransferred);
    void save();
}
