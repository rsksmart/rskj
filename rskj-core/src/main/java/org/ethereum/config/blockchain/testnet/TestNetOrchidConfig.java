package org.ethereum.config.blockchain.testnet;

public class TestNetOrchidConfig extends TestNetAfterBridgeSyncConfig {
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
