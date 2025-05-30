package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;

import java.util.Optional;
import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {

    RskAddress getUnionBridgeContractAddress();

    UnionResponseCode setUnionBridgeContractAddressForTestnet(Transaction tx,
        RskAddress unionBridgeContractAddress);

    Optional<Coin> getLockingCap();

    UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap);

    void save();
}
