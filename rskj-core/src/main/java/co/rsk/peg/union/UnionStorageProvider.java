package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import java.util.Optional;

public interface UnionStorageProvider {
    void save();

    void setUnionAddress(RskAddress unionAddress);
    Optional<RskAddress> getUnionAddress();
}
