package org.ethereum.config.blockchain.mainnet;

public class MainNetOrchidConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean isRfs50() {
        return true;
    }

    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs94() {
        return true;
    }

    @Override
    public boolean isRcs230() {
        return true;
    }

    @Override
    public boolean isRfs170() { return true; }
}
