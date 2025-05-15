package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import java.util.Optional;
import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {

    Optional<RskAddress> getUnionBridgeContractAddress();

    int setUnionBridgeContractAddressForTestnet(Transaction tx,
        RskAddress unionBridgeContractAddress);

    Optional<Coin> getLockingCap();

    int increaseLockingCap(Transaction tx, Coin newCap);

    int requestUnionRbtc(Transaction tx, co.rsk.core.Coin amount);

    void save();
}
