package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;

import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {

    RskAddress getUnionBridgeContractAddress();

    UnionResponseCode setUnionBridgeContractAddressForTestnet(Transaction tx,
        RskAddress unionBridgeContractAddress);

    Coin getLockingCap();

    UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap);

    UnionResponseCode requestUnionRbtc(Transaction tx, co.rsk.core.Coin amount);

    void save();
}
