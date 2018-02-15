package org.ethereum.config.blockchain.testnet;

// TODO: find a proper name for the "FirstFork"
public class TestNetFirstForkConfig extends TestNetAfterBridgeSyncConfig {
    @Override
    public boolean isRfs50() {
        return true;
    }

    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs90() {
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
}
