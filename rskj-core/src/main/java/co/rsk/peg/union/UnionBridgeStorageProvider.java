package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import java.util.Optional;

public interface UnionBridgeStorageProvider {
    void save();

    void setAddress(RskAddress address);
    Optional<RskAddress> getAddress();
}
