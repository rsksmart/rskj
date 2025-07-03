package co.rsk.peg.union;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import java.util.Optional;

public interface UnionBridgeStorageProvider {
    void setAddress(RskAddress address);
    Optional<RskAddress> getAddress();
    void setLockingCap(Coin lockingCap);
    Optional<Coin> getLockingCap();
    Optional<Coin> getWeisTransferredToUnionBridge();
    void increaseWeisTransferredToUnionBridge(Coin amountRequested);
    void decreaseWeisTransferredToUnionBridge(Coin amountReleased);
    void setUnionBridgeRequestEnabled(boolean enabled);
    Optional<Boolean> isUnionBridgeRequestEnabled();
    void save();
}
