package co.rsk.peg.union;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;

import org.ethereum.core.Transaction;

public interface UnionBridgeSupport {

    RskAddress getUnionBridgeContractAddress();

    UnionResponseCode setUnionBridgeContractAddressForTestnet(RskAddress unionBridgeContractAddress);

    Coin getLockingCap();

    UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap);

    UnionResponseCode requestUnionRbtc(Transaction tx, Coin amount);

    UnionResponseCode releaseUnionRbtc(Transaction tx);

    UnionResponseCode setTransferPermissions(Transaction tx, boolean requestEnabled, boolean releaseEnabled);

    byte[] getSuperEvent();
    void setSuperEvent(Transaction tx, byte[] data);
    void clearSuperEvent(Transaction tx);
    byte[] getBaseEvent();
    void setBaseEvent(Transaction tx, byte[] data);
    void clearBaseEvent(Transaction tx);

    void save();
}
